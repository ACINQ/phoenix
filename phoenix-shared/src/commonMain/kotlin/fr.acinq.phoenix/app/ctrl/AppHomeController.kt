package fr.acinq.phoenix.app.ctrl

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.Aborted
import fr.acinq.lightning.channel.Closed
import fr.acinq.lightning.channel.Closing
import fr.acinq.lightning.channel.ErrorInformationLeak
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.app.PaymentsManager
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val paymentsManager: PaymentsManager
) : AppController<Home.Model, Home.Intent>(
    loggerFactory,
    firstModel = Home.emptyModel
) {

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->
                val newBalance = channels.values.map {
                    when (it) {
                        is Closing -> MilliSatoshi(0)
                        is Closed -> MilliSatoshi(0)
                        is Aborted -> MilliSatoshi(0)
                        is ErrorInformationLeak -> MilliSatoshi(0)
                        else -> it.localCommitmentSpec?.toLocal ?: MilliSatoshi(0)
                    }
                }.sum()

                model { copy(balance = newBalance) }
            }
        }

        launch {
            paymentsManager.incomingSwaps.collect {
                model { copy(incomingBalance = it.values.sum().takeIf { it.msat > 0 }) }
            }
        }

        launch {
            paymentsManager.payments.collect {
                model {
                    copy(payments = it)
                }
            }
        }
    }

    override fun process(intent: Home.Intent) {}
}
