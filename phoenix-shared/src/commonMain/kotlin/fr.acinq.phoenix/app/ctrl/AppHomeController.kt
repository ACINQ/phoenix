package fr.acinq.phoenix.app.ctrl

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.channel.ChannelStateWithCommitments
import fr.acinq.eclair.io.Peer
import fr.acinq.phoenix.app.AppHistoryManager
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.data.Transaction
import fr.acinq.phoenix.utils.NetworkMonitor
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.consumeAsFlow
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
                        balanceSat = channels.values.sumOf { it.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0 }
                    )
                }
            }
        }

        launch {
            historyManager.openTransactionsSubscriptions()
                .consumeAsFlow()
                .collectIndexed { nth, list ->
                    model {
                        val lastTransaction = list.firstOrNull()
                        if (nth != 0 && lastTransaction != null && lastTransaction.status != Transaction.Status.Pending) {
                            copy(history = list, lastTransaction = lastTransaction)
                        } else {
                            copy(history = list)
                        }
                    }
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
