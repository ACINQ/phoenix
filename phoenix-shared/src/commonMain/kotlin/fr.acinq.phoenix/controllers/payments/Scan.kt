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

import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.controllers.MVI
import fr.acinq.phoenix.data.BitcoinUri
import fr.acinq.phoenix.data.lnurl.*
import io.ktor.http.*

data class MaxFees(
    val feeBase: Satoshi,
    val feeProportionalMillionths: Long
)

object Scan {

    sealed class BadRequestReason {
        object UnknownFormat : BadRequestReason()
        object AlreadyPaidInvoice : BadRequestReason()
        data class Expired(val timestampSeconds: Long, val expirySeconds: Long) : BadRequestReason()
        data class ChainMismatch(val expected: Chain) : BadRequestReason()
        data class ServiceError(val url: Url, val error: LnurlError.RemoteFailure) : BadRequestReason()
        data class InvalidLnurl(val url: Url) : BadRequestReason()
        data class InvalidBip353(val url: Url) : BadRequestReason()
        data class UnsupportedLnurl(val url: Url) : BadRequestReason()
    }

    sealed class LnurlPayError {
        data class RemoteError(val err: LnurlError.RemoteFailure) : LnurlPayError()
        data class BadResponseError(val err: LnurlError.Pay.Invoice) : LnurlPayError()
        data class ChainMismatch(val expected: Chain) : LnurlPayError()
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
            val request: String,
            val reason: BadRequestReason
        ) : Model()

        sealed class Bolt11InvoiceFlow : Model() {
            data class Bolt11InvoiceRequest(
                val request: String,
                val invoice: Bolt11Invoice,
            ): Bolt11InvoiceFlow()
            object Sending: Bolt11InvoiceFlow()
        }

        data class OfferFlow(val offer: OfferTypes.Offer) : Model()

        data class OnchainFlow(val uri: BitcoinUri): Model()

        object LnurlServiceFetch : Model()
        object ResolvingBip353 : Model()

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
        object Reset: Intent()

        data class Parse(
            val request: String
        ) : Intent()

        sealed class Bolt11InvoiceFlow : Intent() {
            data class SendBolt11Invoice(val invoice: Bolt11Invoice, val amount: MilliSatoshi, val trampolineFees: TrampolineFees) : Bolt11InvoiceFlow()
        }

        object CancelLnurlServiceFetch : Intent()

        sealed class LnurlPayFlow : Intent() {
            data class RequestInvoice(
                val paymentIntent: LnurlPay.Intent,
                val amount: MilliSatoshi,
                val trampolineFees: TrampolineFees,
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
        data class Bolt11InvoiceRequest(
            val invoice: Bolt11Invoice
        ): ClipboardContent()

        data class BitcoinRequest(
            val address: BitcoinUri
        ): ClipboardContent()

        data class LnurlRequest(
            val url: Url
        ) : ClipboardContent()

        data class LoginRequest(
            val auth: LnurlAuth
        ) : ClipboardContent()
    }
}
