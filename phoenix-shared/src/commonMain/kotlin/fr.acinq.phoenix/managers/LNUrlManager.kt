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
import fr.acinq.lightning.utils.Either
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.LNUrl
import fr.acinq.phoenix.utils.PublicSuffixList
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class LNUrlManager(
    loggerFactory: LoggerFactory,
    private val httpClient: HttpClient,
    private val walletManager: WalletManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        httpClient = business.httpClient,
        walletManager = business.walletManager
    )

    private val log = newLogger(loggerFactory)

    /**
     * Get the LNUrl for this source. Throw exception if source is malformed, or invalid.
     * Will execute an HTTP request for some urls and parse the response into an actionable LNUrl object.
     */
    suspend fun extractLNUrl(source: String): LNUrl {
        return when (val result = interactiveExtractLNUrl(source)) {
            is Either.Left -> result.value
            is Either.Right -> continueLnUrl(result.value)
        }
    }

    /**
     * Attempts to extract a Bech32 URL from the string.
     * On success:
     * - if the LnUrl is a simple Auth, it's returned immediately
     * - otherwise the Url is returned (call `continueLnUrl` to fetch & parse the Url)
     *
     * Throws an exception if source is malformed, or invalid.
     */
    fun interactiveExtractLNUrl(source: String): Either<LNUrl.Auth, Url> {
        val url = try {
            LNUrl.parseBech32Url(source)
        } catch (e1: Exception) {
            log.debug { "cannot parse source=$source as a bech32 lnurl" }
            try {
                LNUrl.parseNonBech32Url(source)
            } catch (e2: Exception) {
                log.error { "cannot extract lnurl from source=$source: ${e1.message ?: e1::class} / ${e2.message ?: e2::class}"}
                throw LNUrl.Error.Invalid
            }
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
    suspend fun continueLnUrl(url: Url): LNUrl {
        val json = LNUrl.handleLNUrlResponse(httpClient.get(url))
        return LNUrl.parseLNUrlMetadata(json)
    }

    suspend fun requestAuth(auth: LNUrl.Auth, publicSuffixList: PublicSuffixList) {
        val wallet = walletManager.wallet.filterNotNull().first()
        
        val domain = publicSuffixList.eTldPlusOne(auth.url.host) ?: throw LNUrl.Error.CouldNotDetermineDomain
        val key = wallet.lnurlAuthLinkingKey(domain)
        val signedK1 = Crypto.compact2der(Crypto.sign(
            data = ByteVector32.fromValidHex(auth.k1),
            privateKey = key
        )).toHex()

        val builder = URLBuilder(auth.url)
        builder.parameters.append(name = "sig", value = signedK1)
        builder.parameters.append(name = "key", value = key.publicKey().toString())
        val url = builder.build()

        val response: HttpResponse = try {
            httpClient.get(url)
        } catch (sre: io.ktor.client.features.ServerResponseException) {
            // ktor throws an exception when we get a non-200 response from the server.
            // That's not what we want. We'd like to handle the JSON error ourselves.
            sre.response
        } catch (t: Throwable) {
            throw LNUrl.Error.RemoteFailure.CouldNotConnect(origin = url.host)
        }

        LNUrl.handleLNUrlResponse(response) // throws on any/all non-success
    }
}