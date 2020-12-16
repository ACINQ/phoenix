package fr.acinq.phoenix.app.ctrl

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.io.Peer
import fr.acinq.phoenix.app.AppHistoryManager
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.data.Transaction
import fr.acinq.phoenix.utils.NetworkMonitor
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(loggerFactory: LoggerFactory, private val peer: Peer, private val electrumClient: ElectrumClient, private val networkMonitor: NetworkMonitor, private val historyManager: AppHistoryManager) : AppController<Home.Model, Home.Intent>(loggerFactory, Home.emptyModel) {

    init {
        launch {
            peer.connectionState.collect {
                model { copy(connections = connections.copy(peer = it)) }
            }
        }
        launch {
            electrumClient.connectionState.collect {
                model { copy(connections = connections.copy(electrum = it)) }
            }
        }
        launch {
            networkMonitor.networkState.collect {
                model { copy(connections = connections.copy(internet = it)) }
            }
        }

        launch {
            peer.channelsFlow.collect { channels ->
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

    override fun process(intent: Home.Intent) {}

}
