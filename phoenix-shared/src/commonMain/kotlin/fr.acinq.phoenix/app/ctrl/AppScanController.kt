package fr.acinq.phoenix.app.ctrl

import fr.acinq.eklair.io.Peer
import fr.acinq.eklair.io.SendPayment
import fr.acinq.eklair.payment.PaymentRequest
import fr.acinq.eklair.utils.UUID
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
                    val paymentRequest = PaymentRequest.read(intent.request)
                    model { Scan.Model.Validate(intent.request, paymentRequest.amount?.toLong(), paymentRequest.description) }
                }
            }
            is Scan.Intent.Send -> {
                launch {
                    val paymentRequest = PaymentRequest.read(intent.request)
                    val uuid = UUID.randomUUID()

                    peer.send(SendPayment(uuid, paymentRequest))

                    model { Scan.Model.Sending(paymentRequest.amount?.toLong() ?: intent.amountMsat, paymentRequest.description) }
                }
            }
        }
    }
}
