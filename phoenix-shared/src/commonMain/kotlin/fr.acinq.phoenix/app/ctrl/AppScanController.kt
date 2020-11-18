package fr.acinq.phoenix.app.ctrl

import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.SendPayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.UUID
import fr.acinq.phoenix.ctrl.Scan
import fr.acinq.phoenix.data.toMilliSatoshi
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppScanController(loggerFactory: LoggerFactory, private val peer: Peer) : AppController<Scan.Model, Scan.Intent>(loggerFactory, Scan.Model.Ready) {

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Parse -> {
                launch {
                    try {
                        val paymentRequest = PaymentRequest.read(intent.request.removePrefix("lightning:"))
                        model(Scan.Model.Validate(
                            request = intent.request,
                            amountSat = paymentRequest.amount?.truncateToSatoshi()?.toLong(),
                            requestDescription = paymentRequest.description
                        ))
                    } catch (t: Throwable) { // TODO Throwable is not a good choice, analyze the possible output of PaymentRequest.read(...)
                        model(Scan.Model.BadRequest)
                    }
                }
            }
            is Scan.Intent.Send -> {
                launch {
                    val paymentRequest = PaymentRequest.read(intent.request)
                    val paymentAmount = intent.amount.toMilliSatoshi(intent.unit)
                    val paymentId = UUID.randomUUID()

                    peer.send(SendPayment(paymentId, paymentRequest, paymentAmount))

                    model(Scan.Model.Sending)
                }
            }
        }
    }
}
