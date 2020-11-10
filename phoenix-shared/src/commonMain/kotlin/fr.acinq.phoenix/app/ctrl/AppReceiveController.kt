package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.CltvExpiry
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.io.PaymentRequestGenerated
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.ReceivePayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.secure
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.toMilliSatoshi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.filter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppReceiveController(di: DI) : AppController<Receive.Model, Receive.Intent>(di, Receive.Model.Awaiting) {

    val preimage = ByteVector32(Random.secure().nextBytes(32))

    val peer: Peer by instance()

    private val Receive.Intent.Ask.paymentAmountMsat: MilliSatoshi? get() = amount?.toMilliSatoshi(unit)

    private val Receive.Intent.Ask.paymentDescription: String get() = desc ?: "Phoenix payment"

    override fun process(intent: Receive.Intent) {
        when (intent) {
            is Receive.Intent.Ask -> {
                launch {
                    model(Receive.Model.Generating)
                    val deferred = CompletableDeferred<PaymentRequest>()
                    peer.send(ReceivePayment(preimage, intent.paymentAmountMsat, intent.paymentDescription, deferred))
                    val request = deferred.await()
                    check(request.amount == intent.paymentAmountMsat) { "Payment request amount not corresponding to expected" }
                    check(request.description == intent.paymentDescription) { "Payment request description not corresponding to expected" }
                    model(Receive.Model.Generated(request.write(), intent.amount, intent.unit, intent.desc))
                }
            }
        }
    }

}
