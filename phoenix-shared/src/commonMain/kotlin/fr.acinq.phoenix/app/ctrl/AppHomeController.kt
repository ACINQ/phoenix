package fr.acinq.phoenix.app.ctrl

import fr.acinq.eclair.channel.Aborted
import fr.acinq.eclair.channel.Closed
import fr.acinq.eclair.channel.Closing
import fr.acinq.eclair.channel.ErrorInformationLeak
import fr.acinq.eclair.io.Peer
import fr.acinq.phoenix.app.PaymentsManager
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(
    loggerFactory: LoggerFactory,
    private val peer: Peer,
    private val paymentsManager: PaymentsManager
) : AppController<Home.Model, Home.Intent>(loggerFactory, Home.emptyModel) {

    init {
        launch {
            peer.channelsFlow.collect { channels ->
                model {
                    copy(balanceSat = channels.values.sumOf {
                        when (it) {
                            is Closing -> 0
                            is Closed -> 0
                            is Aborted -> 0
                            is ErrorInformationLeak -> 0
                            else -> it.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0
                        }
                    })
                }
            }
        }

        launch {
            paymentsManager.subscribeToPayments()
                .collectIndexed { index, pair ->
                    model {
                        copy(payments = pair.first, lastPayment = pair.second)
                    }
                }
        }
    }

    override fun process(intent: Home.Intent) {}

}
