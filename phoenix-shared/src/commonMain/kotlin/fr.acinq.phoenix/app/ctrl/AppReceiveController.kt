package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.ReceivePayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.secure
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.data.toMilliSatoshi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppReceiveController(loggerFactory: LoggerFactory, private val peer: Peer) : AppController<Receive.Model, Receive.Intent>(loggerFactory, Receive.Model.Awaiting) {

    private val Receive.Intent.Ask.paymentAmountMsat: MilliSatoshi? get() = amount?.toMilliSatoshi(unit)

    private val Receive.Intent.Ask.paymentDescription: String get() = desc ?: "Phoenix payment"

    override fun process(intent: Receive.Intent) {
        when (intent) {
            is Receive.Intent.Ask -> {
                launch {
                    model(Receive.Model.Generating)
                    try {
                        val deferred = CompletableDeferred<PaymentRequest>()
                        val preimage = ByteVector32(Random.secure().nextBytes(32)) // must be different everytime
                        peer.send(
                            ReceivePayment(
                                preimage,
                                intent.paymentAmountMsat,
                                intent.paymentDescription,
                                deferred
                            )
                        )
                        val request = deferred.await()
                        check(request.amount == intent.paymentAmountMsat) { "Payment request amount not corresponding to expected" }
                        check(request.description == intent.paymentDescription) { "Payment request description not corresponding to expected" }
                        model(Receive.Model.Generated(request.write(), intent.amount, intent.unit, intent.desc))
                    } catch (e: Throwable) {
                        logger.error(e) { "failed to process intent=$intent" }
                    }
                }
            }
        }
    }

}
