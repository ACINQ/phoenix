package fr.acinq.phoenix.app.ctrl

import fr.acinq.eklair.blockchain.electrum.ElectrumClient
import fr.acinq.eklair.channel.HasCommitments
import fr.acinq.eklair.io.Peer
import fr.acinq.phoenix.app.AppHistoryManager
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.utils.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(di: DI) : AppController<Home.Model, Home.Intent>(di, Home.emptyModel) {
    private val peer: Peer by instance()
    private val electrumClient: ElectrumClient by instance()
    private val networkMonitor: NetworkMonitor by instance()
    private val historyManager: AppHistoryManager by instance()

    init {
        launch {
            peer.openConnectedSubscription().consumeEach {
                model { copy(connections = connections.copy(peer = it)) }
            }
        }
        launch {
            electrumClient.openConnectedSubscription().consumeEach {
                model { copy(connections = connections.copy(electrum = it)) }
            }
        }
        launch {
            networkMonitor.openNetworkStateSubscription().consumeEach {
                model { copy(connections = connections.copy(internet = it)) }
            }
        }

        launch {
            peer.openChannelsSubscription().consumeEach { channels ->
                model {
                    copy(
                        balanceSat = channels.values
                            .filterIsInstance<HasCommitments>()
                            .sumOf { it.commitments.localCommit.spec.toLocal.truncateToSatoshi().toLong() }
                    )
                }
            }
        }

        launch {
            historyManager.openTransactionsSubscriptions().consumeEach {
                model { copy(history = it) }
            }
        }
    }

    override fun process(intent: Home.Intent) {
        when (intent) {
            is Home.Intent.Connect -> {
                launch {
                    peer.connect("localhost", 48001) // TODO: Only for demo
                }
            }
        }
    }

}
