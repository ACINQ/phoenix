package fr.acinq.phoenix.app

import fr.acinq.eclair.NodeUri
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.utils.NetworkMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AppConnectionsDaemon(
    private val configurationManager: AppConfigurationManager,
    private val walletManager: WalletManager,
    private val monitor: NetworkMonitor,
    private val electrumClient: ElectrumClient,
    private val acinqNodeUri: NodeUri,
    loggerFactory: LoggerFactory,
    private val getPeer: () -> Peer // peer is lazy as it may not exist yet.
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    private val electrumConnectionOrder = Channel<ConnectionOrder>()
    private val peerConnectionOrder = Channel<ConnectionOrder>()

    private var peerConnectionJob :Job? = null
    private var electrumConnectionJob: Job? = null
    private var networkMonitorJob: Job? = null

    private var networkStatus = Connection.CLOSED

    init {
        // Electrum
        launch {
            electrumConnectionOrder.consumeEach {
                when {
                    it == ConnectionOrder.CONNECT && networkStatus != Connection.CLOSED -> {
                        electrumConnectionJob = connectionLoop("Electrum", electrumClient.connectionState) {
                            val electrumServer = configurationManager.getElectrumServer()
                            electrumClient.connect(electrumServer.asServerAddress())
                        }
                    }
                    else -> {
                        electrumConnectionJob?.cancel()
                        electrumClient.disconnect()
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
                        electrumConnectionOrder.send(ConnectionOrder.CLOSE)
                        electrumConnectionOrder.send(ConnectionOrder.CONNECT)
                    }

                    previousElectrumServer = it
                }
            }
        }
        // Peer
        launch {
            peerConnectionOrder.consumeEach {
                when {
                    it == ConnectionOrder.CONNECT  && networkStatus != Connection.CLOSED -> {
                        peerConnectionJob = connectionLoop("Peer", getPeer().connectionState) {
                            getPeer().connect()
                        }
                    }
                    else -> {
                        peerConnectionJob?.cancel()
                    }
                }
            }
        }
        // Internet connection
        launch {
            walletManager.openWalletUpdatesSubscription().consumeEach {
                if (networkMonitorJob == null) networkMonitorJob = networkStateMonitoring()
            }
        }
    }

    private fun networkStateMonitoring() = launch {
        monitor.start()
        monitor.openNetworkStateSubscription().consumeEach {
            if (networkStatus == it) return@consumeEach
            logger.info { "New internet status: $it" }

            when(it) {
                Connection.CLOSED -> {
                    peerConnectionOrder.send(ConnectionOrder.CLOSE)
                    electrumConnectionOrder.send(ConnectionOrder.CLOSE)
                }
                else -> {
                    peerConnectionOrder.send(ConnectionOrder.CONNECT)
                    electrumConnectionOrder.send(ConnectionOrder.CONNECT)
                }
            }

            networkStatus = it
        }
    }

    private fun connectionLoop(name: String, statusStateFlow: StateFlow<Connection>, connect: () -> Unit) = launch {
        var retryDelay = 1.seconds
        statusStateFlow.collect {
            logger.debug { "New $name status $it" }

            if (it == Connection.CLOSED) {
                logger.debug { "Wait for $retryDelay before retrying connection on $name" }
                delay(retryDelay) ; retryDelay = increaseDelay(retryDelay)
                connect()
            } else if (it == Connection.ESTABLISHED) {
                retryDelay = 1.seconds
            }
        }
    }

    private fun increaseDelay(retryDelay: Duration) = when (val delay = retryDelay.inSeconds) {
        8.0 -> delay
        else -> delay * 2.0
    }.seconds

    enum class ConnectionOrder { CONNECT, CLOSE }
}
