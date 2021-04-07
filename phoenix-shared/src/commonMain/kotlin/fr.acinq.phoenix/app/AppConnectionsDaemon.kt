package fr.acinq.phoenix.app

import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.data.ElectrumConfig
import fr.acinq.phoenix.utils.NetworkMonitor
import fr.acinq.phoenix.utils.NetworkState
import fr.acinq.phoenix.utils.RETRY_DELAY
import fr.acinq.phoenix.utils.increaseDelay
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AppConnectionsDaemon(
    private val configurationManager: AppConfigurationManager,
    private val walletManager: WalletManager,
    private val peerManager: PeerManager,
    private val currencyManager: CurrencyManager,
    private val monitor: NetworkMonitor,
    private val electrumClient: ElectrumClient,
    loggerFactory: LoggerFactory,
) : CoroutineScope by MainScope() {

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
                            electrumConnectionJob = connectionLoop("Electrum", electrumClient.connectionState) {
                                val electrumConfig = configurationManager.electrumConfig().value
                                if (electrumConfig == null) {
                                    logger.info { "ignored electrum connection opportunity because no server is configured yet" }
                                } else {
                                    logger.info { "connecting to electrum using config=$electrumConfig" }
                                    electrumClient.connect(electrumConfig.server)
                                }
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
                            peerConnectionJob = connectionLoop("Peer", peer.connectionState) {
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
                            configurationManager.startWalletParamsLoop()
                            currencyManager.start()
                        }
                    }
                    else -> {
                        if (httpControlFlowEnabled) {
                            httpControlFlowEnabled = false
                            configurationManager.stopWalletParamsLoop()
                            currencyManager.stop()
                        }
                    }
                }
            }
        }
        // Internet connection monitor
        launch {
            monitor.start()
            monitor.networkState.filter { it != networkStatus.value }.collect {
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
            configurationManager.electrumConfig().collect { config ->
                if (config == null) {
                    electrumControlChanges.send { incrementDisconnectCount() }
                } else if (config.server.host != previousElectrumConfig?.server?.host) {
                    logger.info { "electrum server config updated to=$config, reconnecting..." }
                    electrumControlChanges.send { incrementDisconnectCount() }
                    delay(500)
                    // We need to delay the next connection vote because the collector WILL skip fast updates (see documentation)
                    // and ignore the change since the TrafficControl object would not have changed.
                    electrumControlChanges.send { decrementDisconnectCount() }
                }
                previousElectrumConfig = config
            }
        }
    }

    fun incrementDisconnectCount(): Unit {
        launch {
            peerControlChanges.send { incrementDisconnectCount() }
            electrumControlChanges.send { incrementDisconnectCount() }
            httpApiControlChanges.send { incrementDisconnectCount() }
        }
    }

    fun decrementDisconnectCount(): Unit {
        launch {
            peerControlChanges.send { decrementDisconnectCount() }
            electrumControlChanges.send { decrementDisconnectCount() }
            httpApiControlChanges.send { decrementDisconnectCount() }
        }
    }

    private fun connectionLoop(name: String, statusStateFlow: StateFlow<Connection>, connect: () -> Unit) = launch {
        var retryDelay = RETRY_DELAY
        statusStateFlow.collect {
            if (it == Connection.CLOSED) {
                logger.debug { "Wait for $retryDelay before retrying connection on $name" }
                delay(retryDelay) ; retryDelay = increaseDelay(retryDelay)
                connect()
            } else if (it == Connection.ESTABLISHED) {
                retryDelay = RETRY_DELAY
            }
        }
    }
}
