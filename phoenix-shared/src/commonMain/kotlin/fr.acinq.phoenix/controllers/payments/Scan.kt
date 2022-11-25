/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.controllers.payments

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.controllers.MVI
import fr.acinq.phoenix.data.BitcoinAddressInfo
import fr.acinq.phoenix.data.lnurl.*
import io.ktor.http.*
import kotlin.time.ExperimentalTime

data class MaxFees(
    val feeBase: Satoshi,
    val feeProportionalMillionths: Long
)

@OptIn(ExperimentalTime::class)
object Scan {

    sealed class BadRequestReason {
        object UnknownFormat : BadRequestReason()
        object AlreadyPaidInvoice : BadRequestReason()
        data class ChainMismatch(val myChain: Chain, val requestChain: Chain?) : BadRequestReason()
        data class ServiceError(val url: Url, val error: LnurlError.RemoteFailure) : BadRequestReason()
        data class InvalidLnurl(val url: Url) : BadRequestReason()
        data class UnsupportedLnurl(val url: Url) : BadRequestReason()
    }

    sealed class DangerousRequestReason {
        object IsAmountlessInvoice : DangerousRequestReason()
        object IsOwnInvoice : DangerousRequestReason()
    }

    sealed class LnurlPayError {
        data class RemoteError(val err: LnurlError.RemoteFailure) : LnurlPayError()
        data class BadResponseError(val err: LnurlError.Pay.Invoice) : LnurlPayError()
        data class ChainMismatch(val myChain: Chain, val requestChain: Chain?) : LnurlPayError()
        object AlreadyPaidInvoice : LnurlPayError()
    }

    sealed class LnurlWithdrawError {
        data class RemoteError(val err: LnurlError.RemoteFailure) : LnurlWithdrawError()
    }

    sealed class LoginError {
        data class ServerError(val details: LnurlError.RemoteFailure) : LoginError()
        data class NetworkError(val details: Throwable) : LoginError()
        data class OtherError(val details: Throwable) : LoginError()
    }

    sealed class Model : MVI.Model() {
        object Ready : Model()

        data class BadRequest(
            val reason: BadRequestReason
        ) : Model()

        sealed class InvoiceFlow : Model() {
            data class DangerousRequest(
                val request: String,
                val paymentRequest: PaymentRequest,
                val reason: DangerousRequestReason
            ): InvoiceFlow()
            data class InvoiceRequest(
                val request: String,
                val paymentRequest: PaymentRequest
            ): InvoiceFlow()
            object Sending: InvoiceFlow()
        }

        sealed class SwapOutFlow: Model() {
            abstract val address: BitcoinAddressInfo
            data class Init(override val address: BitcoinAddressInfo): SwapOutFlow()
            data class RequestingSwapout(override val address: BitcoinAddressInfo): SwapOutFlow()
            /** The swap-out is ready to be settled with a Lightning payment. The user must confirm the swap (the fee should be shown prominently). */
            data class SwapOutReady(override val address: BitcoinAddressInfo, val initialUserAmount: Satoshi, val fee: Satoshi, val paymentRequest: PaymentRequest): SwapOutFlow()
            data class SendingSwapOut(override val address: BitcoinAddressInfo, val paymentRequest: PaymentRequest): SwapOutFlow()
        }

        object LnurlServiceFetch : Model()

        sealed class LnurlPayFlow : Model() {
            abstract val paymentIntent: LnurlPay.Intent
            data class LnurlPayRequest(
                override val paymentIntent: LnurlPay.Intent,
                val error: LnurlPayError?
            ) : LnurlPayFlow()

            data class LnurlPayFetch(
                override val paymentIntent: LnurlPay.Intent
            ) : LnurlPayFlow()

            data class Sending(
                override val paymentIntent: LnurlPay.Intent
            ) : LnurlPayFlow()
        }

        sealed class LnurlWithdrawFlow : Model() {
            abstract val lnurlWithdraw: LnurlWithdraw
            data class LnurlWithdrawRequest(
                override val lnurlWithdraw: LnurlWithdraw,
                val error: LnurlWithdrawError?
            ) : LnurlWithdrawFlow()

            data class LnurlWithdrawFetch(
                override val lnurlWithdraw: LnurlWithdraw,
            ) : LnurlWithdrawFlow()

            data class Receiving(
                override val lnurlWithdraw: LnurlWithdraw,
                val amount: MilliSatoshi,
                val description: String?,
                val paymentHash: String
            ) : LnurlWithdrawFlow()
        }

        sealed class LnurlAuthFlow : Model() {
            abstract val auth: LnurlAuth
            data class LoginRequest(
                override val auth: LnurlAuth
            ) : LnurlAuthFlow()

            data class LoggingIn(
                override val auth: LnurlAuth
            ) : LnurlAuthFlow()

            data class LoginResult(
                override val auth: LnurlAuth,
                val error: LoginError?
            ) : LnurlAuthFlow()
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
            ) : InvoiceFlow()

            data class SendInvoicePayment(
                val paymentRequest: PaymentRequest,
                val amount: MilliSatoshi,
                val maxFees: MaxFees?
            ) : InvoiceFlow()
        }

        sealed class SwapOutFlow: Intent() {
            /**
             * Use this to go back to the initial state [Model.SwapOutFlow.Init], when the swap-out
             * amount has been edited by the user, which invalidates the current model.
             */
            data class Invalidate(val address: BitcoinAddressInfo): SwapOutFlow()

            data class PrepareSwapOut(
                val address: BitcoinAddressInfo,
                val amount: Satoshi,
            ): SwapOutFlow()

            data class SendSwapOut(
                val amount: Satoshi,
                val swapOutFee: Satoshi,
                val address: BitcoinAddressInfo,
                val paymentRequest: PaymentRequest,
                val maxFees: MaxFees?,
            ): SwapOutFlow()
        }

        object CancelLnurlServiceFetch : Intent()

        sealed class LnurlPayFlow : Intent() {
            data class RequestInvoice(
                val paymentIntent: LnurlPay.Intent,
                val amount: MilliSatoshi,
                val maxFees: MaxFees?,
                val comment: String?
            ) : LnurlPayFlow()

            data class CancelLnurlPayment(
                val lnurlPay: LnurlPay.Intent
            ) : LnurlPayFlow()
        }

        sealed class LnurlWithdrawFlow : Intent() {
            data class SendLnurlWithdraw(
                val lnurlWithdraw: LnurlWithdraw,
                val amount: MilliSatoshi,
                val description: String?
            ) : LnurlWithdrawFlow()

            data class CancelLnurlWithdraw(
                val lnurlWithdraw: LnurlWithdraw
            ) : LnurlWithdrawFlow()
        }

        sealed class LnurlAuthFlow : Intent() {
            data class Login(
                val auth: LnurlAuth,
                val minSuccessDelaySeconds: Double = 0.0,
                val scheme: LnurlAuth.Scheme
            ) : LnurlAuthFlow()
        }
    }

    sealed class ClipboardContent {
        data class InvoiceRequest(
            val paymentRequest: PaymentRequest
        ): ClipboardContent()

        data class BitcoinRequest(
            val address: BitcoinAddressInfo
        ): ClipboardContent()

        data class LnurlRequest(
            val url: Url
        ) : ClipboardContent()

        data class LoginRequest(
            val auth: LnurlAuth
        ) : ClipboardContent()
    }
}
