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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.LNUrl
import fr.acinq.phoenix.utils.PublicSuffixList
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class LNUrlManager(
    loggerFactory: LoggerFactory,
    private val walletManager: WalletManager
) : CoroutineScope by MainScope() {

    // use special client for lnurl since we dont want ktor to break when receiving non-2xx response
    private val httpClient: HttpClient by lazy {
        HttpClient {
            expectSuccess = false // required for non-json responses
            install(ContentNegotiation) {
                json(json = Json { ignoreUnknownKeys = true })
                expectSuccess = false
            }
        }
    }

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        walletManager = business.walletManager
    )

    private val log = newLogger(loggerFactory)

    /**
     * Get the LNUrl for this source. Throw exception if source is malformed, or invalid.
     * Will execute an HTTP request for some urls and parse the response into an actionable LNUrl object.
     */
    suspend fun extractLnurl(source: String): LNUrl {
        return when (val result = interactiveExtractLnurl(source)) {
            is Either.Left -> result.value
            is Either.Right -> continueLnurlAsync(result.value).await()
        }
    }

    /**
     * Attempts to extract a Bech32 URL from the string.
     * On success:
     * - if the LnUrl is a simple Auth, it's returned immediately
     * - otherwise the Url is returned (call `continueLnurlAsync` to fetch & parse the Url)
     *
     * Throws an exception if source is malformed, or invalid.
     */
    fun interactiveExtractLnurl(source: String): Either<LNUrl.Auth, Url> {
        var url = try {
            LNUrl.parseBech32Url(source)
        } catch (e: Exception) {
            log.info { "cannot parse source=$source as bech32 lnurl: ${e.message ?: e::class}" }
            null
        }
        url = url ?: try {
            LNUrl.parseNonBech32Url(source)
        } catch (e: Exception) {
            log.info { "cannot parse source=$source as non-bech32 lnurl: ${e.message ?: e::class}" }
            null
        }
        url = url ?: try {
            LNUrl.parseInternetIdentifier(source)
        } catch (e: Exception) {
            log.info { "cannot parse source=$source as lnurl-id: ${e.message ?: e::class}" }
            null
        }

        if (url == null) {
            throw LNUrl.Error.Invalid
        }
        return when (url.parameters["tag"]) {
            // auth urls must not be called just yet
            LNUrl.Tag.Auth.label -> {
                val k1 = url.parameters["k1"]
                if (k1.isNullOrBlank()) {
                    throw LNUrl.Error.Auth.MissingK1
                } else {
                    Either.Left(LNUrl.Auth(url, k1))
                }
            }
            else -> Either.Right(url)
        }
    }

    /**
     * Executes the HTTP request for the LnUrl and parses the response
     * into an actionable LNUrl object.
     */
    fun continueLnurlAsync(url: Url): Deferred<LNUrl> = async {
        val response: HttpResponse = try {
            httpClient.get(url)
        } catch (err: Throwable) {
            throw LNUrl.Error.RemoteFailure.CouldNotConnect(origin = url.host)
        }
        try {
            val json = LNUrl.handleLNUrlResponse(response)
            return@async LNUrl.parseLNUrlResponse(url, json)
        } catch (e: Exception) {
            when (e) {
                is LNUrl.Error.RemoteFailure -> throw e
                else -> throw LNUrl.Error.RemoteFailure.Unreadable(url.host)
            }
        }
    }

    /**
     * May throw errors of type:
     * - LNUrl.Error.RemoteFailure
     * - LNUrl.Error.PayInvoice
     */
    fun requestPayInvoiceAsync(
        lnurlPay: LNUrl.Pay,
        amount: MilliSatoshi,
        comment: String?
    ): Deferred<LNUrl.PayInvoice> = async {

        val builder = URLBuilder(lnurlPay.callback)
        builder.appendParameter(name = "amount", value = amount.msat.toString())
        if (comment != null && comment.isNotEmpty()) {
            builder.appendParameter(name = "comment", value = comment)
        }
        val callback = builder.build()
        val origin = callback.host

        val response: HttpResponse = try {
            httpClient.get(callback)
        } catch (err: Throwable) {
            throw LNUrl.Error.RemoteFailure.CouldNotConnect(origin)
        }

        // may throw: LNUrl.Error.RemoteFailure
        val json = LNUrl.handleLNUrlResponse(response)

        // may throw: LNUrl.Error.PayInvoice
        val invoice = LNUrl.parseLNUrlPayResponse(origin, json)

        // From the [spec](https://github.com/fiatjaf/lnurl-rfc/blob/luds/06.md):
        //
        // - LN WALLET Verifies that h tag in provided invoice is a hash of
        //   metadata string converted to byte array in UTF-8 encoding.
        //
        // Note: h tag == descriptionHash

        val expectedHash = Crypto.sha256(lnurlPay.metadata.raw.encodeToByteArray())
        val actualHash = invoice.paymentRequest.descriptionHash?.toByteArray()
        if (!expectedHash.contentEquals(actualHash)) {
            throw LNUrl.Error.PayInvoice.InvalidHash(origin)
        }

        // - LN WALLET Verifies that amount in provided invoice equals an
        //   amount previously specified by user.

        if (amount != invoice.paymentRequest.amount) {
            throw LNUrl.Error.PayInvoice.InvalidAmount(origin)
        }

        return@async invoice
    }

    /**
     * May throw errors of type:
     * - LNUrl.Error.RemoteFailure
     */
    fun sendWithdrawInvoiceAsync(
        lnurlWithdraw: LNUrl.Withdraw,
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
            throw LNUrl.Error.RemoteFailure.CouldNotConnect(origin)
        }

        // may throw: LNUrl.Error.RemoteFailure
        //
        // - LNUrl.Error.RemoteFailure.Code:
        //     if non-2XX status code is returned
        // - LNUrl.Error.RemoteFailure.Unreadable:
        //     if response isn't valid JSON
        // - LNUrl.Error.RemoteFailure.Detailed:
        //     if {"status": "ERROR", ...} is returned
        //
        LNUrl.handleLNUrlResponse(response)

        // According to the spec:
        //
        // > LN SERVICE sends a {"status": "OK"} or
        // > {"status": "ERROR", "reason": "error details..."} JSON response
        // > and then attempts to pay the invoices asynchronously.
        //
        // At this point:
        // - the server returned a 2XX response
        // - the resposne body was valid JSON
        // - and the JSON did NOT contain {"status": "ERROR", ...}
        //
        // We could verify the response contains {"status": "OK"}.
        // But it doesn't really matter if the server obeyed the spec perfectly.
        // At this point the server has enough information to send a payment.
        // And then the user will be told to wait for the incoming payment.
    }

    suspend fun requestAuth(auth: LNUrl.Auth, publicSuffixList: PublicSuffixList) {
        val wallet = walletManager.wallet.filterNotNull().first()

        val domain = publicSuffixList.eTldPlusOne(auth.url.host) ?: throw LNUrl.Error.Auth.CouldNotDetermineDomain
        val key = wallet.lnurlAuthLinkingKey(domain)
        val signedK1 = Crypto.compact2der(
            Crypto.sign(
                data = ByteVector32.fromValidHex(auth.k1),
                privateKey = key
            )
        ).toHex()

        val builder = URLBuilder(auth.url)
        builder.appendParameter(name = "sig", value = signedK1)
        builder.appendParameter(name = "key", value = key.publicKey().toString())
        val url = builder.build()

        val response: HttpResponse = try {
            httpClient.get(url)
        } catch (t: Throwable) {
            throw LNUrl.Error.RemoteFailure.CouldNotConnect(origin = url.host)
        }

        LNUrl.handleLNUrlResponse(response) // throws on any/all non-success
    }
}

fun URLBuilder.appendParameter(name: String, value: String) {
    @OptIn(InternalAPI::class)
    this.parameters.append(name = name, value = value)
}
