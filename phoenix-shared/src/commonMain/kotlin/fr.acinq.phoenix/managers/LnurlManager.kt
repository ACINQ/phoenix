/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.lnurl.*
import fr.acinq.phoenix.utils.loggerExtensions.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject


class LnurlManager(
    loggerFactory: Logger,
    private val walletManager: WalletManager
) : CoroutineScope by MainScope() {

    // use special client for lnurl since we dont want ktor to break when receiving non-2xx response
    private val httpClient: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(json = Json { ignoreUnknownKeys = true })
                expectSuccess = false
            }
        }
    }

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.newLoggerFactory,
        walletManager = business.walletManager
    )

    private val log = loggerFactory.appendingTag("LnurlManager")

    /** Executes an HTTP GET request on the provided url and parses the JSON response into an [Lnurl] object. */
    fun executeLnurl(url: Url): Deferred<Lnurl> = async {
        val response: HttpResponse = try {
            httpClient.get(url)
        } catch (err: Throwable) {
            throw LnurlError.RemoteFailure.CouldNotConnect(origin = url.host)
        }
        try {
            val json = Lnurl.processLnurlResponse(response)
            return@async Lnurl.parseLnurlJson(url, json)
        } catch (e: Exception) {
            when (e) {
                is LnurlError -> throw e
                else -> throw LnurlError.RemoteFailure.Unreadable(url.host)
            }
        }
    }

    /**
     * Execute an HTTP GET request to obtain a [LnurlPay.Invoice] from a [LnurlPay.Intent]. May throw a
     * [LnurlError.RemoteFailure] or a [LnurlError.Pay.Invoice] error.
     *
     * @param intent the description of the payment as provided by the service.
     * @param amount the amount that the user is willing to pay to settle the [LnurlPay.Intent].
     * @param comment an optional string commenting the payment and sent to the service.
     */
    fun requestPayInvoice(
        intent: LnurlPay.Intent,
        amount: MilliSatoshi,
        comment: String?
    ): Deferred<LnurlPay.Invoice> = async {

        val builder = URLBuilder(intent.callback)
        builder.appendParameter(name = "amount", value = amount.msat.toString())
        if (!comment.isNullOrEmpty()) {
            builder.appendParameter(name = "comment", value = comment)
        }
        val callback = builder.build()
        val origin = callback.host

        val response: HttpResponse = try {
            httpClient.get(callback)
        } catch (err: Throwable) {
            throw LnurlError.RemoteFailure.CouldNotConnect(origin)
        }

        val json = Lnurl.processLnurlResponse(response)
        val invoice = LnurlPay.parseLnurlPayInvoice(intent, origin, json)

        // SPECS: LN WALLET verifies that the amount in the provided invoice equals the amount previously specified by user.
        if (amount != invoice.paymentRequest.amount) {
            log.error { "rejecting invoice from $origin with amount_invoice=${invoice.paymentRequest.amount} requested_amount=$amount" }
            throw LnurlError.Pay.Invoice.InvalidAmount(origin)
        }

        return@async invoice
    }

    /**
     * Send an invoice to a lnurl service following a [LnurlWithdraw] request.
     * Throw [LnurlError.RemoteFailure].
     */
    fun sendWithdrawInvoice(
        lnurlWithdraw: LnurlWithdraw,
        paymentRequest: PaymentRequest
    ): Deferred<JsonObject> = async {

        val builder = URLBuilder(lnurlWithdraw.callback)
        builder.appendParameter(name = "k1", value = lnurlWithdraw.k1)
        builder.appendParameter(name = "pr", value = paymentRequest.write())
        val callback = builder.build()
        val origin = callback.host

        val response: HttpResponse = try {
            httpClient.get(callback)
        } catch (err: Throwable) {
            throw LnurlError.RemoteFailure.CouldNotConnect(origin)
        }

        // SPECS: even if the response is an error, the invoice may still be paid by the service
        // we still parse the response to see what's up.
        Lnurl.processLnurlResponse(response)
    }

    suspend fun signAndSendAuthRequest(
        auth: LnurlAuth,
        scheme: LnurlAuth.Scheme,
    ) {
        val key = LnurlAuth.getAuthLinkingKey(
            localKeyManager = walletManager.keyManager.filterNotNull().first(),
            serviceUrl = auth.initialUrl,
            scheme = scheme
        )
        val (pubkey, signedK1) = LnurlAuth.signChallenge(auth.k1, key)

        val builder = URLBuilder(auth.initialUrl)
        builder.appendParameter(name = "sig", value = signedK1.toHex())
        builder.appendParameter(name = "key", value = pubkey.toString())
        val url = builder.build()

        val response: HttpResponse = try {
            httpClient.get(url)
        } catch (t: Throwable) {
            throw LnurlError.RemoteFailure.CouldNotConnect(origin = url.host)
        }

        Lnurl.processLnurlResponse(response) // throws on any/all non-success
    }
}

private fun URLBuilder.appendParameter(name: String, value: String) {
    this.parameters.append(name = name, value = value)
}