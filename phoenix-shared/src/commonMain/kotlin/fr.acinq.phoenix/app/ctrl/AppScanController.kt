package fr.acinq.phoenix.app.ctrl

import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.SendPayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.UUID
import fr.acinq.phoenix.ctrl.Scan
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

class AppScanController(di: DI) : AppController<Scan.Model, Scan.Intent>(di, Scan.Model.Ready) {

    private val peer: Peer by instance()

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Parse -> {
                launch {
                    try {
                        val paymentRequest = PaymentRequest.read(intent.request)
                        model(Scan.Model.Validate(intent.request, paymentRequest.amount?.toLong(), paymentRequest.description))
                    } catch (t: Throwable) {
                        model(Scan.Model.BadRequest)
                    }
                }
            }
            is Scan.Intent.Send -> {
                launch {
                    val paymentRequest = PaymentRequest.read(intent.request)
                    val uuid = UUID.randomUUID()

                    peer.send(SendPayment(uuid, paymentRequest))

                    model(Scan.Model.Sending(paymentRequest.amount?.toLong() ?: intent.amountMsat, paymentRequest.description))
                }
            }
        }
    }
}
