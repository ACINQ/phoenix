package fr.acinq.phoenix.app

import fr.acinq.eklair.blockchain.electrum.ElectrumClient
import fr.acinq.eklair.io.Peer
import fr.acinq.eklair.utils.Connection
import fr.acinq.phoenix.utils.NetworkMonitor
import kotlinx.coroutines.*
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
class AppConnectionsDaemon(override val di: DI) : DIAware {

    // TODO to be replaced by a real DB
    private val walletManager: WalletManager by instance()

    private val monitor: NetworkMonitor by instance()
    private val peer: Peer by instance()
    private val electrumClient: ElectrumClient by instance()

    private val logger = direct.instance<LoggerFactory>().newLogger(AppConnectionsDaemon::class)

    init {
        MainScope().launch {
            walletManager.openWalletUpdatesSubscription().consumeEach {
                networkStateMonitoring()
            }
        }
    }

    private var connectionDaemonJob :Job? = null
    private var connectionElectrumJob: Job? = null

    private suspend fun networkStateMonitoring() {
        monitor.start()
        var networkStatus = Connection.CLOSED
        monitor.openNetworkStateSubscription().consumeEach {
            if (networkStatus == it) return@consumeEach
            logger.info { "New internet status: $it" }

            if (it != Connection.CLOSED) {
                connectionDaemonJob = connectionLoop("Peer", peer.openConnectedSubscription()) {
                        val host: String by instance(tag = "host")
                        peer.connect(host, 48001)
                    }
                connectionElectrumJob = connectionLoop("Electrum", electrumClient.openConnectedSubscription()) {
                        electrumClient.connect()
                    }
            } else {
                connectionDaemonJob?.cancel()
                connectionElectrumJob?.cancel()
                electrumClient.disconnect()
            }

            networkStatus = it
        }
    }

    private fun connectionLoop(name: String, statusChannel: ReceiveChannel<Connection>, connect: () -> Unit) = MainScope().launch {
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

}
