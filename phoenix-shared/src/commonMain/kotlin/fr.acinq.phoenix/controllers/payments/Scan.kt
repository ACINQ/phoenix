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
        object UnsupportedLnUrl: BadRequestReason()
        object IsBitcoinAddress: BadRequestReason()
        object UnknownFormat: BadRequestReason()
        object AlreadyPaidInvoice: BadRequestReason()
    }

    sealed class DangerousRequestReason {
        object IsAmountlessInvoice: DangerousRequestReason()
        object IsOwnInvoice: DangerousRequestReason()
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
        data class DangerousRequest(
            val reason: DangerousRequestReason,
            val request: String,
            val paymentRequest: PaymentRequest
        ): Model()
        data class ValidateRequest(
            val request: String,
            val paymentRequest: PaymentRequest,
            val amountMsat: Long?,
            val expiryTimestamp: Long?, // since unix epoch
            val requestDescription: String?,
            val balanceMsat: Long
        ): Model()
        object Sending: Model()
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

    sealed class Intent : MVI.Intent() {
        data class Parse(
            val request: String
        ) : Intent()
        data class ConfirmDangerousRequest(
            val request: String,
            val paymentRequest: PaymentRequest
        ) : Intent()
        data class Send(
            val paymentRequest: PaymentRequest,
            val amount: MilliSatoshi
        ) : Intent()
        data class Login(
            val auth: LNUrl.Auth,
            val minSuccessDelaySeconds: Double = 0.0
        ) : Intent()
    }
}
