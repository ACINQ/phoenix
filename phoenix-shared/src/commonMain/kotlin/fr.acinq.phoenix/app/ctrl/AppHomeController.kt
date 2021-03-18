package fr.acinq.phoenix.app.ctrl

import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.channel.Aborted
import fr.acinq.eclair.channel.Closed
import fr.acinq.eclair.channel.Closing
import fr.acinq.eclair.channel.ErrorInformationLeak
import fr.acinq.eclair.utils.sum
import fr.acinq.phoenix.app.PaymentsManager
import fr.acinq.phoenix.app.PeerManager
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
    private val peerManager: PeerManager,
    private val paymentsManager: PaymentsManager
) : AppController<Home.Model, Home.Intent>(loggerFactory, Home.emptyModel) {

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->
                model {
                    copy(balance = channels.values.map {
                        when (it) {
                            is Closing -> MilliSatoshi(0)
                            is Closed -> MilliSatoshi(0)
                            is Aborted -> MilliSatoshi(0)
                            is ErrorInformationLeak -> MilliSatoshi(0)
                            else -> it.localCommitmentSpec?.toLocal ?: MilliSatoshi(0)
                        }
                    }.sum())
                }
            }
        }

        launch {
            paymentsManager.subscribeToPayments()
                .collectIndexed { _, pair ->
                    model {
                        copy(payments = pair.first, lastPayment = pair.second)
                    }
                }
        }
    }

    override fun process(intent: Home.Intent) {}

}
