package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.io.ReceivePayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.secure
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.ctrl.Receive
import fr.acinq.phoenix.data.toMilliSatoshi
import kotlinx.coroutines.*
import org.kodein.log.LoggerFactory
import kotlin.random.Random


@OptIn(ExperimentalCoroutinesApi::class)
class AppReceiveController(loggerFactory: LoggerFactory, private val peerManager: PeerManager) : AppController<Receive.Model, Receive.Intent>(loggerFactory, Receive.Model.Awaiting) {

    private val Receive.Intent.Ask.description: String get() = desc?.takeIf { it.isNotBlank() } ?: ""

    override fun process(intent: Receive.Intent) {
        when (intent) {
            is Receive.Intent.Ask -> {
                launch {
                    model(Receive.Model.Generating)
                    try {
                        val deferred = CompletableDeferred<PaymentRequest>()
                        val preimage = ByteVector32(Random.secure().nextBytes(32)) // must be different everytime
                        peerManager.getPeer().send(
                            ReceivePayment(
                                preimage,
                                intent.amount,
                                intent.description,
                                deferred
                            )
                        )
                        val request = deferred.await()
                        check(request.amount == intent.amount) { "payment request amount=${request.amount} does not match expected amount=${intent.amount}" }
                        check(request.description == intent.description) { "payment request amount=${request.description} does not match expected amount=${intent.description}" }
                        val paymentHash: String = request.paymentHash.toHex()
                        model(Receive.Model.Generated(request.write(), paymentHash, request.amount, request.description))
                    } catch (e: Throwable) {
                        logger.error(e) { "failed to process intent=$intent" }
                    }
                }
            }
        }
    }

}
