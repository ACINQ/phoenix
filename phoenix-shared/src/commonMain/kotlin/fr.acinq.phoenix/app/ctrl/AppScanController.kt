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
            is Scan.Intent.Parse -> launch {
                readPaymentRequest(intent.request)?.run {
                    if (amount != null)
                        validatePaymentRequest(intent.request, this)
                    else
                        model(Scan.Model.RequestWithoutAmount(intent.request))
                }
            }
            is Scan.Intent.ConfirmEmptyAmount -> launch {
                readPaymentRequest(intent.request)?.run {
                    validatePaymentRequest(intent.request, this)
                }
            }
            is Scan.Intent.Send -> {
                launch {
                    readPaymentRequest(intent.request)?.run {
                        val paymentAmount = intent.amount.toMilliSatoshi(intent.unit)
                        val paymentId = UUID.randomUUID()

                        peer.send(SendPayment(paymentId, this, paymentAmount))

                        model(Scan.Model.Sending)
                    }
                }
            }
        }
    }

    private suspend fun readPaymentRequest(request: String) : PaymentRequest? {
        return try {
            PaymentRequest.read(request.cleanUpInvoice())
        } catch (t: Throwable) { // TODO Throwable is not a good choice, analyze the possible output of PaymentRequest.read(...)
            model(Scan.Model.BadRequest)
            null
        }
    }

    private suspend fun validatePaymentRequest(request: String, paymentRequest: PaymentRequest) {
        model(
            Scan.Model.Validate(
                request = request,
                amountSat = paymentRequest.amount?.truncateToSatoshi()?.toLong(),
                requestDescription = paymentRequest.description
            )
        )
    }

    private fun String.cleanUpInvoice(): String {
        val trimmed = replace("\\u00A0", "").trim()
        return when {
            trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
            trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
            trimmed.startsWith("bitcoin://", true) -> trimmed.drop(10)
            trimmed.startsWith("bitcoin:", true) -> trimmed.drop(8)
            else -> trimmed
        }
    }

}
