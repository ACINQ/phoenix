package fr.acinq.phoenix.app.ctrl

import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.channel.Aborted
import fr.acinq.eclair.channel.Closed
import fr.acinq.eclair.channel.Closing
import fr.acinq.eclair.channel.ErrorInformationLeak
import fr.acinq.eclair.db.WalletPayment
import fr.acinq.eclair.io.SwapInConfirmedEvent
import fr.acinq.eclair.io.SwapInPendingEvent
import fr.acinq.eclair.utils.getValue
import fr.acinq.eclair.utils.setValue
import fr.acinq.eclair.utils.sum
import fr.acinq.eclair.utils.toMilliSatoshi
import fr.acinq.phoenix.app.PaymentsManager
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.ctrl.Home
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val paymentsManager: PaymentsManager
) : AppController<Home.Model, Home.Intent>(loggerFactory, Home.emptyModel) {

    /** Map of (bitcoinAddress -> amount) swap-ins. */
    private val incomingSwapsFlow = MutableStateFlow<Map<String, MilliSatoshi>>(HashMap())
    private var incomingSwaps by incomingSwapsFlow

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
            peerManager.getPeer().openListenerEventSubscription().consumeEach { event ->
                when (event) {
                    is SwapInPendingEvent -> {
                        incomingSwaps = incomingSwaps + (event.swapInPending.bitcoinAddress to event.swapInPending.amount.toMilliSatoshi())
                    }
                    is SwapInConfirmedEvent -> {
                        incomingSwaps = incomingSwaps - event.swapInConfirmed.bitcoinAddress
                    }
                    else -> Unit
                }
            }
        }

        launch {
            incomingSwapsFlow.collect {
                model { copy(incomingBalance = it.values.sum().takeIf { it.msat > 0 }) }
            }
        }

        launch {
            paymentsManager.payments.collect {
                model {
                    copy(payments = it, lastPayment = it.firstOrNull()?.takeIf { WalletPayment.completedAt(it) > 0 })
                }
            }
        }

    }

    override fun process(intent: Home.Intent) {}

}
