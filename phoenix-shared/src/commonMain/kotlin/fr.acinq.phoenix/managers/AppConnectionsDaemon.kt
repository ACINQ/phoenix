package fr.acinq.phoenix.managers

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.ElectrumConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AppConnectionsDaemon(
    private val configurationManager: AppConfigurationManager,
    private val walletManager: WalletManager,
    private val peerManager: PeerManager,
    private val currencyManager: CurrencyManager,
    private val networkManager: NetworkManager,
    private val electrumClient: ElectrumClient,
    loggerFactory: LoggerFactory,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        configurationManager = business.appConfigurationManager,
        walletManager = business.walletManager,
        peerManager = business.peerManager,
        currencyManager = business.currencyManager,
        networkManager = business.networkMonitor,
        electrumClient = business.electrumClient
    )

    private val logger = newLogger(loggerFactory)

    private var peerConnectionJob: Job? = null
    private var electrumConnectionJob: Job? = null
    private var httpControlFlowEnabled: Boolean = false

    private var networkStatus = MutableStateFlow(NetworkState.NotAvailable)

    private data class TrafficControl(
        val networkIsAvailable: Boolean = false,

        // Under normal circumstances, the connections are automatically managed based on whether
        // or not an Internet connection is available. However, the app may need to influence
        // this in one direction or another.
        // For example, the app may want us to disconnect.
        // This variable allows different parts of the app to "vote" towards various overrides.
        //
        // if > 0, triggers disconnect & prevents future connection attempts.
        // if <= 0, allows connection based on available Internet connection (as usual).
        // Any part of the app that "votes" is expected to properly balance their calls.
        // For example, on iOS, when the app goes into background mode,
        // it votes by incrementing this value. Therefore it must balance that call by
        // decrementing the value when the app goes into the foreground again.
        val disconnectCount: Int = 0
    ) {
        fun incrementDisconnectCount(): TrafficControl {
            val safeInc = disconnectCount.let { if (it == Int.MAX_VALUE) it else it + 1 }
            return copy(disconnectCount = safeInc)
        }
        fun decrementDisconnectCount(): TrafficControl {
            val safeDec = disconnectCount.let { if (it == Int.MIN_VALUE) it else it - 1 }
            return copy(disconnectCount = safeDec)
        }
    }

    private val peerControlFlow = MutableStateFlow(TrafficControl())
    private val peerControlChanges = Channel<TrafficControl.() -> TrafficControl>()

    private val electrumControlFlow = MutableStateFlow(TrafficControl())
    private val electrumControlChanges = Channel<TrafficControl.() -> TrafficControl>()

    private val httpApiControlFlow = MutableStateFlow(TrafficControl())
    private val httpApiControlChanges = Channel<TrafficControl.() -> TrafficControl>()

    private var _lastElectrumServerAddress = MutableStateFlow<ServerAddress?>(null)
    val lastElectrumServerAddress: StateFlow<ServerAddress?> = _lastElectrumServerAddress

    init {
        fun enableControlFlow(
            label: String,
            controlFlow: MutableStateFlow<TrafficControl>,
            controlChanges: ReceiveChannel<TrafficControl.() -> TrafficControl>
        ) = launch {
            controlChanges.consumeEach { change ->
                val newState = controlFlow.value.change()
                logger.debug { "$label = $newState" }
                controlFlow.value = newState
            }
        }

        enableControlFlow("peerControlFlow", peerControlFlow, peerControlChanges)
        enableControlFlow("electrumControlFlow", electrumControlFlow, electrumControlChanges)
        enableControlFlow("httpApiControlFlow", httpApiControlFlow, httpApiControlChanges)

        // Electrum
        launch {
            electrumControlFlow.collect {
                when {
                    it.networkIsAvailable && it.disconnectCount <= 0 -> {
                        if (electrumConnectionJob == null) {
                            electrumConnectionJob = connectionLoop(
                                name = "Electrum",
                                statusStateFlow = electrumClient.connectionState
                            ) {
                                val electrumServerAddress : ServerAddress? = configurationManager.electrumConfig().value?.let { electrumConfig ->
                                    when (electrumConfig) {
                                        is ElectrumConfig.Custom -> electrumConfig.server
                                        is ElectrumConfig.Random -> configurationManager.randomElectrumServer()
                                    }
                                }
                                if (electrumServerAddress == null) {
                                    logger.info { "ignored electrum connection opportunity because no server is configured yet" }
                                } else {
                                    logger.info { "connecting to electrum server=$electrumServerAddress" }
                                    electrumClient.connect(electrumServerAddress)
                                }
                                _lastElectrumServerAddress.value = electrumServerAddress
                            }
                        }
                    }
                    else -> {
                        electrumConnectionJob?.let { job ->
                            logger.debug { "disconnecting from electrum" }
                            job.cancel()
                            electrumClient.disconnect()
                        }
                        electrumConnectionJob = null
                    }
                }
            }
        }
        // Peer
        launch {
            peerControlFlow.collect {
                val peer = peerManager.getPeer()
                when {
                    it.networkIsAvailable && it.disconnectCount <= 0 -> {
                        if (peerConnectionJob == null) {
                            peerConnectionJob = connectionLoop(
                                name = "Peer",
                                statusStateFlow = peer.connectionState
                            ) {
                                peer.connect()
                            }
                        }
                    }
                    else -> {
                        peerConnectionJob?.let { job ->
                            logger.debug { "disconnecting from peer" }
                            job.cancel()
                            peer.disconnect()
                        }
                        peerConnectionJob = null
                    }
                }
            }
        }
        // HTTP APIs
        launch {
            httpApiControlFlow.collect {
                when {
                    it.networkIsAvailable && it.disconnectCount <= 0 -> {
                        if (!httpControlFlowEnabled) {
                            httpControlFlowEnabled = true
                            configurationManager.enableNetworkAccess()
                            currencyManager.start()
                        }
                    }
                    else -> {
                        if (httpControlFlowEnabled) {
                            httpControlFlowEnabled = false
                            configurationManager.disableNetworkAccess()
                            currencyManager.stop()
                        }
                    }
                }
            }
        }
        // Internet connection monitor
        launch {
            networkManager.start()
            networkManager.networkState.filter { it != networkStatus.value }.collect {
                logger.info { "network state=$it" }
                networkStatus.value = it
            }
        }
        // Internet dependent flows - related to the app configuration
        launch {
            networkStatus.collect {
                when(it) {
                    NetworkState.Available -> httpApiControlChanges.send { copy(networkIsAvailable = true) }
                    NetworkState.NotAvailable -> httpApiControlChanges.send { copy(networkIsAvailable = false) }
                }
            }
        }
        // Internet dependent flows - related to the Wallet
        launch {
            // Suspends until the wallet is initialized
            walletManager.wallet.filterNotNull().first()
            networkStatus.collect {
                when (it) {
                    NetworkState.Available -> {
                        peerControlChanges.send { copy(networkIsAvailable = true) }
                        electrumControlChanges.send { copy(networkIsAvailable = true) }
                    }
                    NetworkState.NotAvailable -> {
                        peerControlChanges.send { copy(networkIsAvailable = false) }
                        electrumControlChanges.send { copy(networkIsAvailable = false) }
                    }
                }
            }
        }
        // TODO Tor usage
        launch { configurationManager.subscribeToIsTorEnabled().collect {
            logger.info { "Tor is ${if (it) "enabled" else "disabled"}." }
        } }
        // listen to electrum configuration changes and reconnect when needed.
        launch {
            var previousElectrumConfig: ElectrumConfig? = null
            configurationManager.electrumConfig().collect { newElectrumConfig ->
                logger.info { "electrum config changed from=$previousElectrumConfig to $newElectrumConfig" }
                val changed = when (val oldElectrumConfig = previousElectrumConfig) {
                    is ElectrumConfig.Custom -> {
                        when (newElectrumConfig) {
                            is ElectrumConfig.Custom -> { // custom -> custom
                                newElectrumConfig.server.host != oldElectrumConfig.server.host ||
                                newElectrumConfig.server.port != oldElectrumConfig.server.port
                            }
                            is ElectrumConfig.Random -> true // custom -> random
                            else -> true // custom -> null
                        }
                    }
                    is ElectrumConfig.Random -> {
                        when (newElectrumConfig) {
                            is ElectrumConfig.Custom -> true // random -> custom
                            is ElectrumConfig.Random -> false // random -> random
                            else -> true // random -> null
                        }
                    }
                    else -> {
                        when (newElectrumConfig) {
                            null -> false // null -> null
                            else -> true // null -> (custom || random)
                        }
                    }
                }
                if (changed) {
                    logger.info { "electrum server config changed to=$newElectrumConfig, reconnecting..." }
                    electrumControlChanges.send { incrementDisconnectCount() }
                    if (previousElectrumConfig != null) {
                        // The electrumConfig is only null on app launch.
                        // It gets set by the client app during launch,
                        // and from that point forward it's either Custom or Random.
                        delay(500)
                    }
                    // We need to delay the next connection vote because the collector WILL skip fast updates (see documentation)
                    // and ignore the change since the TrafficControl object would not have changed.
                    electrumControlChanges.send { decrementDisconnectCount() }
                }
                previousElectrumConfig = newElectrumConfig
            }
        }
    }

    enum class ControlTarget(val flags: Int) {
        Peer(0b001),
        Electrum(0b010),
        Http(0b100),
        All(0b111);

        val peer get() = (flags and Peer.flags) != 0
        val electrum get() = (flags and Electrum.flags) != 0
        val http get() = (flags and Http.flags) != 0
    }

    fun incrementDisconnectCount(target: ControlTarget = ControlTarget.All) {
        launch {
            if (target.peer) { peerControlChanges.send { incrementDisconnectCount() } }
            if (target.electrum) { electrumControlChanges.send { incrementDisconnectCount() } }
            if (target.http) { httpApiControlChanges.send { incrementDisconnectCount() } }
        }
    }

    fun decrementDisconnectCount(target: ControlTarget = ControlTarget.All) {
        launch {
            if (target.peer) { peerControlChanges.send { decrementDisconnectCount() } }
            if (target.electrum) { electrumControlChanges.send { decrementDisconnectCount() } }
            if (target.http) { httpApiControlChanges.send { decrementDisconnectCount() } }
        }
    }

    private fun connectionLoop(
        name: String,
        statusStateFlow: StateFlow<Connection>,
        connect: () -> Unit
    ) = launch {
        var pause = Duration.seconds(0)
        statusStateFlow.collect {
            if (it == Connection.CLOSED) {
                logger.debug { "next $name connection attempt in $pause" }
                delay(pause)
                val minPause = Duration.seconds(0.25)
                val maxPause = Duration.seconds(8)
                pause = (pause.coerceAtLeast(minPause) * 2).coerceAtMost(maxPause)
                connect()
            } else if (it == Connection.ESTABLISHED) {
                pause = Duration.seconds(0.5)
            }
        }
    }
}
