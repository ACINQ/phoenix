package fr.acinq.phoenix.controllers.payments

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.controllers.MVI
import fr.acinq.phoenix.data.LNUrl
import io.ktor.http.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object Scan {

    sealed class BadRequestReason {
        data class ChainMismatch(val myChain: Chain, val requestChain: Chain?): BadRequestReason()
        object InvalidLnUrl: BadRequestReason()
        object UnsupportedLnUrl: BadRequestReason()
        object IsBitcoinAddress: BadRequestReason()
        object UnknownFormat: BadRequestReason()
        object AlreadyPaidInvoice: BadRequestReason()
    }

    sealed class DangerousRequestReason {
        object IsAmountlessInvoice: DangerousRequestReason()
        object IsOwnInvoice: DangerousRequestReason()
    }

    sealed class LNUrlPayError {
        data class RemoteError(val err: LNUrl.Error.RemoteFailure): LNUrlPayError()
        data class BadResponseError(val err: LNUrl.Error.PayInvoice): LNUrlPayError()
        data class ChainMismatch(val myChain: Chain, val requestChain: Chain?): LNUrlPayError()
        object AlreadyPaidInvoice: LNUrlPayError()
    }

    sealed class LoginError {
        data class ServerError(val details: LNUrl.Error.RemoteFailure): LoginError()
        data class NetworkError(val details: Throwable): LoginError()
        data class OtherError(val details: Throwable): LoginError()
    }

    sealed class Model : MVI.Model() {
        object Ready: Model()

        data class BadRequest(
            val reason: BadRequestReason
        ): Model()

        sealed class InvoiceFlow : Model() {
            data class DangerousRequest(
                val request: String,
                val paymentRequest: PaymentRequest,
                val reason: DangerousRequestReason
            ): Model()
            data class InvoiceRequest(
                val request: String,
                val paymentRequest: PaymentRequest,
                val balanceMsat: Long
            ): Model()
            object Sending: Model()
        }

        object LnurlServiceFetch: Model()

        sealed class LnurlPayFlow : Model() {
            data class LnurlPayRequest(
                val lnurlPay: LNUrl.Pay,
                val balanceMsat: Long,
                val error: LNUrlPayError?
            ): Model()

            data class LnUrlPayFetch(
                val lnurlPay: LNUrl.Pay,
                val balanceMsat: Long
            ): Model()

            object Sending: Model()
        }

        sealed class LnurlAuthFlow : Model() {
            data class LoginRequest(
                val auth: LNUrl.Auth
            ): Model()

            data class LoggingIn(
                val auth: LNUrl.Auth
            ): Model()

            data class LoginResult(
                val auth: LNUrl.Auth,
                val error: LoginError?
            ): Model()
        }
    }

    sealed class Intent : MVI.Intent() {
        data class Parse(
            val request: String
        ) : Intent()

        sealed class InvoiceFlow : Intent() {
            data class ConfirmDangerousRequest(
                val request: String,
                val paymentRequest: PaymentRequest
            ) : Intent()

            data class SendInvoicePayment(
                val paymentRequest: PaymentRequest,
                val amount: MilliSatoshi
            ) : Intent()
        }

        object CancelLnurlServiceFetch: Intent()

        sealed class LnurlPayFlow : Intent() {
            data class SendLnurlPayment(
                val lnurlPay: LNUrl.Pay,
                val amount: MilliSatoshi,
                val comment: String?
            ): LnurlPayFlow()

            data class CancelLnurlPayment(
                val lnurlPay: LNUrl.Pay
            ): LnurlPayFlow()
        }

        sealed class LnurlAuthFlow : Intent() {
            data class Login(
                val auth: LNUrl.Auth,
                val minSuccessDelaySeconds: Double = 0.0
            ) : LnurlAuthFlow()
        }
    }
}
