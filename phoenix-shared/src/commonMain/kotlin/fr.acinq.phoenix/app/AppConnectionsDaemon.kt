package fr.acinq.phoenix.app

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.data.ElectrumServer
import fr.acinq.phoenix.data.address
import fr.acinq.phoenix.data.asServerAddress
import fr.acinq.phoenix.utils.NetworkMonitor
import fr.acinq.phoenix.utils.NetworkState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AppConnectionsDaemon(
    private val configurationManager: AppConfigurationManager,
    private val walletManager: WalletManager,
    private val currencyManager: CurrencyManager,
    private val monitor: NetworkMonitor,
    private val electrumClient: ElectrumClient,
    loggerFactory: LoggerFactory,
    private val getPeer: () -> Peer // peer is lazy as it may not exist yet.
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    private var peerConnectionJob :Job? = null
    private var electrumConnectionJob: Job? = null
    private var networkMonitorJob: Job? = null

    private var networkStatus = NetworkState.NotAvailable

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

    init {
        launch {
            peerControlChanges.consumeEach { change ->
                val newState = peerControlFlow.value.change()
                logger.debug { "peerControlFlow = $newState" }
                peerControlFlow.value = newState
            }
        }
        launch {
            electrumControlChanges.consumeEach { change ->
                val newState = electrumControlFlow.value.change()
                logger.debug { "electrumControlFlow = $newState" }
                electrumControlFlow.value = newState
            }
        }
        // Electrum
        launch {
            electrumControlFlow.collect {
                when {
                    it.networkIsAvailable && it.disconnectCount == 0 -> {
                        electrumConnectionJob = connectionLoop("Electrum", electrumClient.connectionState) {
                            val electrumServer = configurationManager.getElectrumServer()
                            electrumClient.connect(electrumServer.asServerAddress())
                        }
                    }
                    else -> {
                        electrumConnectionJob?.let {
                            it.cancel()
                            electrumClient.disconnect()
                        }
                    }
                }
            }
        }
        launch {
            configurationManager.run {
                if (!getElectrumServer().customized) {
                    setRandomElectrumServer()
                }

                var previousElectrumServer: ElectrumServer? = null
                openElectrumServerUpdateSubscription().consumeEach {
                    if (previousElectrumServer?.address() != it.address()) {
                        logger.info { "Electrum server has changed. We need to refresh the connection." }
                        electrumControlChanges.send { incrementDisconnectCount() }
                        electrumControlChanges.send { decrementDisconnectCount() }
                    }

                    previousElectrumServer = it
                }
            }
        }
        // Peer
        launch {
            peerControlFlow.collect {
                when {
                    it.networkIsAvailable && it.disconnectCount == 0 -> {
                        peerConnectionJob = connectionLoop("Peer", getPeer().connectionState) {
                            getPeer().connect()
                        }
                    }
                    else -> {
                        peerConnectionJob?.let {
                            it.cancel()
                            getPeer().disconnect()
                        }
                    }
                }
            }
        }
        // Internet connection
        launch {
            walletManager.walletState.filter { it != null }.collect {
                if (networkMonitorJob == null) networkMonitorJob = networkStateMonitoring()
            }
        }
    }

    fun incrementDisconnectCount(): Unit {
        launch {
            peerControlChanges.send { incrementDisconnectCount() }
            electrumControlChanges.send { incrementDisconnectCount() }
        }
    }

    fun decrementDisconnectCount(): Unit {
        launch {
            peerControlChanges.send { decrementDisconnectCount() }
            electrumControlChanges.send { decrementDisconnectCount() }
        }
    }

    private fun networkStateMonitoring() = launch {
        monitor.start()
        monitor.networkState.filter { it != networkStatus }.collect {
            logger.info { "New internet status: $it" }

            networkStatus = it
            when(it) {
                NetworkState.Available -> {
                    peerControlChanges.send { copy(networkIsAvailable = true) }
                    electrumControlChanges.send { copy(networkIsAvailable = true) }
                    currencyManager.start()
                }
                NetworkState.NotAvailable -> {
                    peerControlChanges.send { copy(networkIsAvailable = false) }
                    electrumControlChanges.send { copy(networkIsAvailable = false) }
                    currencyManager.stop()
                }
            }
        }
    }

    private fun connectionLoop(name: String, statusStateFlow: StateFlow<Connection>, connect: () -> Unit) = launch {
        var retryDelay = 0.5.seconds
        statusStateFlow.collect {
            logger.debug { "New $name status $it" }

            if (it == Connection.CLOSED) {
                logger.debug { "Wait for $retryDelay before retrying connection on $name" }
                delay(retryDelay) ; retryDelay = increaseDelay(retryDelay)
                connect()
            } else if (it == Connection.ESTABLISHED) {
                retryDelay = 0.5.seconds
            }
        }
    }

    private fun increaseDelay(retryDelay: Duration) = when (val delay = retryDelay.inSeconds) {
        8.0 -> delay
        else -> delay * 2.0
    }.seconds
}
