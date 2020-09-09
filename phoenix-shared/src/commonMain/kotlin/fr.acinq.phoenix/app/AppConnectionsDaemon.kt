package fr.acinq.phoenix.app

import fr.acinq.eklair.blockchain.electrum.ElectrumClient
import fr.acinq.eklair.io.Peer
import fr.acinq.eklair.utils.Connection
import fr.acinq.phoenix.data.ElectrumServer
import fr.acinq.phoenix.data.address
import fr.acinq.phoenix.data.asServerAddress
import fr.acinq.phoenix.utils.NetworkMonitor
import fr.acinq.phoenix.utils.TAG_ACINQ_ADDRESS
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AppConnectionsDaemon(override val di: DI) : DIAware, CoroutineScope by MainScope() {

    private val appConfigurationManager: AppConfigurationManager by instance()
    private val walletManager: WalletManager by instance()

    private val monitor: NetworkMonitor by instance()
    private val peer: Peer by instance()
    private val electrumClient: ElectrumClient by instance()

    private val logger = direct.instance<LoggerFactory>().newLogger(AppConnectionsDaemon::class)

    private val electrumConnectionOrder = Channel<ConnectionOrder>()
    private val peerConnectionOrder = Channel<ConnectionOrder>()

    private var peerConnectionJob :Job? = null
    private var electrumConnectionJob: Job? = null
    private var networkMonitorJob: Job? = null

    init {
        // Electrum
        launch {
            electrumConnectionOrder.consumeEach {
                when (it) {
                    ConnectionOrder.CONNECT -> {
                        electrumConnectionJob = connectionLoop("Electrum", electrumClient.openConnectedSubscription()) {
                            val electrumServer = appConfigurationManager.getElectrumServer()
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
            var previousElectrumServer: ElectrumServer? = null
            appConfigurationManager.openElectrumServerUpdateSubscription().consumeEach {
                if (previousElectrumServer?.address() != it.address()) {
                    logger.info { "Electrum server has changed. We need to refresh the connection." }
                    electrumConnectionOrder.send(ConnectionOrder.CLOSE)
                    electrumConnectionOrder.send(ConnectionOrder.CONNECT)
                }

                previousElectrumServer = it
            }
        }
        // Peer
        launch {
            peerConnectionOrder.consumeEach {
                when (it) {
                    ConnectionOrder.CONNECT -> {
                        peerConnectionJob = connectionLoop("Peer", peer.openConnectedSubscription()) {
                            peer.connect(direct.instance(tag = TAG_ACINQ_ADDRESS), 48001)
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
        var networkStatus = Connection.CLOSED
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

    private fun connectionLoop(name: String, statusChannel: ReceiveChannel<Connection>, connect: () -> Unit) = launch {
        var retryDelay = 1.seconds
        statusChannel.consumeEach {
            logger.verbose { "New $name status $it" }

            if (it == Connection.CLOSED) {
                logger.verbose { "Wait for $retryDelay before retrying connection on $name" }
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
