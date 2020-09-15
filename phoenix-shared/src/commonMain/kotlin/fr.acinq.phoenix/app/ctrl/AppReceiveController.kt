package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.CltvExpiry
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.io.PaymentRequestGenerated
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.ReceivePayment
import fr.acinq.eclair.utils.secure
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.data.toMilliSatoshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppReceiveController(di: DI) : AppController<Receive.Model, Receive.Intent>(di, Receive.Model.Awaiting) {

    val preimage = ByteVector32(Random.secure().nextBytes(32))

    val peer: Peer by instance()

    var lastAsk: Receive.Intent.Ask? = null

    init {
        launch {
            peer.openListenerEventSubscription().consumeEach {
                when (it) {
                    is PaymentRequestGenerated -> {
                        if (it.receivePayment.paymentPreimage == preimage) {
                            val ask = lastAsk ?: error("Received a payment request when none was expected")
                            check(it.receivePayment.amount == ask.paymentAmountMsat) { "Payment request amount not corresponding to expected" }
                            check(it.receivePayment.description == ask.paymentDescription) { "Payment request description not corresponding to expected" }
                            model { Receive.Model.Generated(it.request, ask.amount, ask.unit, ask.desc) }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private val Receive.Intent.Ask.paymentAmountMsat: MilliSatoshi? get() = amount?.toMilliSatoshi(unit)

    private val Receive.Intent.Ask.paymentDescription: String get() = desc ?: "Phoenix payment"

    override fun process(intent: Receive.Intent) {
        when (intent) {
            is Receive.Intent.Ask -> {
                launch {
                    model { Receive.Model.Generating }
                    lastAsk = intent
                    peer.send(ReceivePayment(preimage, intent.paymentAmountMsat, CltvExpiry(100), intent.paymentDescription)) // TODO: Is CltvExpiry correct?
                }
            }
        }
    }

}
