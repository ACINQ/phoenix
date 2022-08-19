package fr.acinq.phoenix.managers

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.ElectrumConfig
import fr.acinq.phoenix.utils.TorHelper.connectionState
import fr.acinq.tor.Tor
import fr.acinq.tor.TorState
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
    loggerFactory: LoggerFactory,
    private val configurationManager: AppConfigurationManager,
    private val walletManager: WalletManager,
    private val peerManager: PeerManager,
    private val currencyManager: CurrencyManager,
    private val networkManager: NetworkManager,
    private val tcpSocketBuilder: suspend () -> TcpSocket.Builder,
    private val tor: Tor,
    private val electrumClient: ElectrumClient,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        configurationManager = business.appConfigurationManager,
        walletManager = business.walletManager,
        peerManager = business.peerManager,
        currencyManager = business.currencyManager,
        networkManager = business.networkMonitor,
        tcpSocketBuilder = business.tcpSocketBuilderFactory,
        tor = business.tor,
        electrumClient = business.electrumClient
    )

    private val logger = newLogger(loggerFactory)

    private var peerConnectionJob: Job? = null
    private var electrumConnectionJob: Job? = null
    private var torConnectionJob: Job? = null
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

    private val torControlFlow = MutableStateFlow(TrafficControl())
    private val torControlChanges = Channel<TrafficControl.() -> TrafficControl>()

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
                logger.info { "$label = $newState" }
                controlFlow.value = newState
            }
        }

        enableControlFlow("peerControlFlow", peerControlFlow, peerControlChanges)
        enableControlFlow("electrumControlFlow", electrumControlFlow, electrumControlChanges)
        enableControlFlow("httpApiControlFlow", httpApiControlFlow, httpApiControlChanges)
        enableControlFlow("torControlFlow", torControlFlow, torControlChanges)

        // Electrum
        launch {
            electrumControlFlow.collect {
                when {
                    it.networkIsAvailable && it.disconnectCount <= 0 -> {
                        if (electrumConnectionJob == null) {
                            logger.info { "electrum socket builder=${electrumClient.socketBuilder}" }
                            electrumConnectionJob = connectionLoop(
                                name = "Electrum",
                                statusStateFlow = electrumClient.connectionState
                            ) {
                                val electrumServerAddress : ServerAddress? = configurationManager.electrumConfig.value?.let { electrumConfig ->
                                    when (electrumConfig) {
                                        is ElectrumConfig.Custom -> electrumConfig.server
                                        is ElectrumConfig.Random -> configurationManager.randomElectrumServer()
                                    }
                                }
                                if (electrumServerAddress == null) {
                                    logger.info { "ignored electrum connection opportunity because no server is configured yet" }
                                } else {
                                    logger.info { "connecting to electrum server=$electrumServerAddress" }
                                    electrumClient.socketBuilder = tcpSocketBuilder()
                                    electrumClient.connect(electrumServerAddress)
                                }
                                _lastElectrumServerAddress.value = electrumServerAddress
                            }
                        }
                    }
                    else -> {
                        electrumConnectionJob?.let { job ->
                            logger.info { "disconnecting from electrum" }
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
                                peer.socketBuilder = tcpSocketBuilder()
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
        // Tor
        launch {
            combine(configurationManager.isTorEnabled.filterNotNull(), torControlFlow) { torEnabled, controlFlow ->
                logger.info { "Tor isEnabled=$torEnabled" }
                if (torEnabled) controlFlow else controlFlow.copy(networkIsAvailable = false)
            }.filterNotNull().collect { controlFlow ->
                when {
                    controlFlow.networkIsAvailable && controlFlow.disconnectCount <= 0 -> {
                        if (torConnectionJob == null) {
                            torConnectionJob = connectionLoop("Tor", tor.state.connectionState(this)) {
                                try {
                                    tor.start(this)
                                } catch (t: Throwable) {
                                    logger.error(t) { "tor cannot be started: ${t.message}" }
                                }
                            }
                        }
                    }
                    else -> {
                        torConnectionJob?.let {
                            it.cancel()
                            tor.stop()
                        }
                        torConnectionJob = null
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
                            currencyManager.enableNetworkAccess()
                        }
                    }
                    else -> {
                        if (httpControlFlowEnabled) {
                            httpControlFlowEnabled = false
                            configurationManager.disableNetworkAccess()
                            currencyManager.disableNetworkAccess()
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
                    NetworkState.Available -> {
                        torControlChanges.send { copy(networkIsAvailable = true) }
                        httpApiControlChanges.send { copy(networkIsAvailable = true) }
                    }
                    NetworkState.NotAvailable -> {
                        torControlChanges.send { copy(networkIsAvailable = false) }
                        httpApiControlChanges.send { copy(networkIsAvailable = false) }
                    }
                }
            }
        }
        // Internet dependent flows - related to the Wallet
        launch {
            // Suspends until the wallet is initialized
            walletManager.wallet.filterNotNull().first()
            // internet dependent flows depend on the state of tor if it is enabled.
            combine(networkStatus, configurationManager.isTorEnabled.filterNotNull(), tor.state) { networkState, torEnabled, torState ->
                networkState to (if (torEnabled) torState else null)
            }.collect { (networkStatus, torState) ->
                when (networkStatus) {
                    NetworkState.NotAvailable -> {
                        peerControlChanges.send { copy(networkIsAvailable = false) }
                        electrumControlChanges.send { copy(networkIsAvailable = false) }
                    }
                    NetworkState.Available -> when (torState) {
                        TorState.STOPPED, TorState.STARTING -> {
                            peerControlChanges.send { copy(networkIsAvailable = false) }
                            electrumControlChanges.send { copy(networkIsAvailable = false) }
                        }
                        null, TorState.RUNNING -> {
                            peerControlChanges.send { copy(networkIsAvailable = true) }
                            electrumControlChanges.send { copy(networkIsAvailable = true) }
                        }
                    }
                }
            }
        }
        // listen to electrum configuration changes and reconnect when needed.
        launch {
            var previousElectrumConfig: ElectrumConfig? = null
            configurationManager.electrumConfig.collect { newElectrumConfig ->
                logger.info { "electrum config changed from=$previousElectrumConfig to $newElectrumConfig" }
                val changed = when (val oldElectrumConfig = previousElectrumConfig) {
                    is ElectrumConfig.Custom -> {
                        when (newElectrumConfig) {
                            is ElectrumConfig.Custom -> { // custom -> custom
                                newElectrumConfig != oldElectrumConfig
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
                    logger.info { "electrum config changed: reconnecting..." }
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
                } else {
                    logger.info { "electrum config: no changes" }
                }
                previousElectrumConfig = newElectrumConfig
            }
        }
    }

    data class ControlTarget(val flags: Int) { // <- bitmask

        companion object {
            val Peer = ControlTarget(0b0001)
            val Electrum = ControlTarget(0b0010)
            val Http = ControlTarget(0b0100)
            val Tor = ControlTarget(0b1000)
            val All = ControlTarget(0b1111)
        }

        /* The `+` operator is implemented, so it can be used like so:
         * `val options = ControlTarget.Peer + ControlTarget.Electrum`
         */
        operator fun plus(other: ControlTarget): ControlTarget {
            return ControlTarget(this.flags or other.flags)
        }

        fun contains(options: ControlTarget): Boolean {
            return (this.flags and options.flags) != 0
        }

        val containsPeer get() = contains(Peer)
        val containsElectrum get() = contains(Electrum)
        val containsHttp get() = contains(Http)
        val containsTor get() = contains(Tor)
    }

    fun incrementDisconnectCount(target: ControlTarget = ControlTarget.All) {
        launch {
            if (target.containsPeer) {
                peerControlChanges.send { incrementDisconnectCount() }
            }
            if (target.containsElectrum) {
                electrumControlChanges.send { incrementDisconnectCount() }
            }
            if (target.containsHttp) {
                httpApiControlChanges.send { incrementDisconnectCount() }
            }
            if (target.containsTor) {
                torControlChanges.send { incrementDisconnectCount() }
            }
        }
    }

    fun decrementDisconnectCount(target: ControlTarget = ControlTarget.All) {
        launch {
            if (target.containsPeer) {
                peerControlChanges.send { decrementDisconnectCount() }
            }
            if (target.containsElectrum) {
                electrumControlChanges.send { decrementDisconnectCount() }
            }
            if (target.containsHttp) {
                httpApiControlChanges.send { decrementDisconnectCount() }
            }
            if (target.containsTor) {
                torControlChanges.send { decrementDisconnectCount() }
            }
        }
    }

    private fun connectionLoop(
        name: String,
        statusStateFlow: StateFlow<Connection>,
        connect: suspend () -> Unit
    ) = launch {
        var pause = Duration.seconds(0)
        statusStateFlow.collect {
            if (it is Connection.CLOSED) {
                logger.info { "next $name connection attempt in $pause" }
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
