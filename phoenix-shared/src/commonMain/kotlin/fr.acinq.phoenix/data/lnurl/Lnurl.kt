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

package fr.acinq.phoenix.data.lnurl

import co.touchlab.kermit.Logger
import fr.acinq.bitcoin.Bech32
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.utils.Parser
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.logging.info
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.json.*

/**
 * This class describes the various types of Lnurls supported by phoenix:
 * - auth
 * - pay
 * - withdraw
 *
 * It also contains the possible errors related to the Lnurl flow:
 * errors that break the specs, or errors raised when the data returned
 * by the Lnurl service are not valid.
 *
 * A companion object contains the utility methods that parse the urls, read a response
 * from a Lnurl service, and transform this response into a valid Lnurl object.
 */
sealed interface Lnurl {

    /**
     * Some lnurls must be executed to be of any use, they don't contain any info by themselves. Those
     * lnurls are usually not visible to the user and are called immediately.
     */
    data class Request(override val initialUrl: Url, val tag: Tag?) : Lnurl

    /**
     * Qualified lnurls objects contain all the necessary data needed from the lnurl service for the user
     * to decide how to proceed.
     */
    sealed interface Qualified : Lnurl

    val initialUrl: Url

    enum class Tag(val label: String) {
        Auth("login"),
        Withdraw("withdrawRequest"),
        Pay("payRequest")
    }

    companion object {
        internal val format: Json = Json { ignoreUnknownKeys = true }

        /**
         * Attempts to extract a [Lnurl] from a string.
         *
         * @param source can be a bech32 lnurl, a non-bech32 lnurl, or a lightning address.
         * @return a [LnurlAuth] if the source is a login lnurl, or an [Url] if it is a payment/withdrawal lnurl.
         *
         * Throws an exception if the source is malformed or invalid.
         */
        fun extractLnurl(source: String, logger: Logger): Lnurl {
            val input = Parser.trimMatchingPrefix(source, Parser.lightningPrefixes + Parser.bitcoinPrefixes + Parser.lnurlPrefixes)
            val url: Url = try {
                logger.debug { "parsing as lnurl source=$source" }
                parseBech32Url(input)
            } catch (bech32Ex: Exception) {
                try {
                    if (lud17Schemes.any { input.startsWith(it, ignoreCase = true) }) {
                        parseNonBech32Lud17(input, logger)
                    } else {
                        parseNonBech32Http(input)
                    }
                } catch (nonBech32Ex: Exception) {
                    logger.info { "cannot parse source as non-bech32 lnurl: ${nonBech32Ex.message ?: nonBech32Ex::class} or as a bech32 lnurl: ${bech32Ex.message ?: bech32Ex::class}" }
                    throw LnurlError.Invalid(cause = nonBech32Ex)
                }
            }
            val tag = url.parameters["tag"]?.let {
                when (it) {
                    Tag.Auth.label -> Tag.Auth
                    Tag.Withdraw.label -> Tag.Withdraw
                    Tag.Pay.label -> Tag.Pay
                    else -> null // ignore unknown tags and handle the lnurl as a `request` to be executed immediately
                }
            }
            return when (tag) {
                Tag.Auth -> {
                    val k1 = url.parameters["k1"]
                    if (k1.isNullOrBlank()) {
                        throw LnurlError.Auth.MissingK1
                    } else {
                        LnurlAuth(url, k1)
                    }
                }
                else -> Request(url, tag)
            }
        }

        /** Lnurls are originally bech32 encoded. If unreadable, throw an exception. */
        private fun parseBech32Url(source: String): Url {
            val (hrp, data) = Bech32.decode(source)
            val payload = Bech32.five2eight(data, 0).decodeToString()
            val url = URLBuilder(payload).build()
            if (!url.protocol.isSecure()) throw LnurlError.UnsafeResource
            return url
        }

        /** Lnurls sometimes hide in regular http urls, under the lightning parameter. */
        private fun parseNonBech32Http(source: String): Url {
            val urlBuilder = URLBuilder(source)
            val lightningParam = urlBuilder.parameters["lightning"]
            return if (!lightningParam.isNullOrBlank()) {
                // this url contains a lnurl fallback which takes priority - and must be bech32 encoded
                parseBech32Url(lightningParam)
            } else {
                if (!urlBuilder.protocol.isSecure()) throw LnurlError.UnsafeResource
                urlBuilder.build()
            }
        }

        private val lud17Schemes = listOf(
            "phoenix:lnurlp://", "phoenix:lnurlp:",
            "lnurlp://", "lnurlp:",
            "phoenix:lnurlw://", "phoenix:lnurlw:",
            "lnurlw://", "lnurlw:",
            "phoenix:keyauth://", "phoenix:keyauth:",
            "keyauth://", "keyauth:",
        )

        /** Converts LUD-17 lnurls (using a custom lnurl scheme like lnurlc, lnurlp, keyauth) into a regular http url. */
        private fun parseNonBech32Lud17(source: String, logger: Logger): Url {
            val matchingPrefix = lud17Schemes.firstOrNull { source.startsWith(it, ignoreCase = true) }
            val stripped = if (matchingPrefix != null) {
                source.drop(matchingPrefix.length)
            } else {
                throw IllegalArgumentException("source does not use a lud17 scheme: $source")
            }
            logger.debug { "lud-17 scheme found - transforming input into an http request" }
            return URLBuilder(stripped).apply {
                encodedPath.split("/", ignoreCase = true, limit = 2).let {
                    this.host = it.first()
                    this.encodedPath = "/${it.drop(1).joinToString()}"
                }
                protocol = if (this.host.endsWith(".onion")) {
                    URLProtocol.HTTP
                } else {
                    URLProtocol.HTTPS
                }
            }.build()
        }

        /**
         * Processes a HTTP response replied by a lnurl service and returns a [JsonObject].
         *
         * Throw:
         * - [LnurlError.RemoteFailure.Code] if service returns a non-2XX code
         * - [LnurlError.RemoteFailure.Unreadable] if response is not valid JSON
         * - [LnurlError.RemoteFailure.Detailed] if service reports an internal error message (`{ status: "error", reason: "..." }`)
         */
        suspend fun processLnurlResponse(response: HttpResponse, logger: Logger): JsonObject {
            val url = response.request.url
            val json: JsonObject = try {
                // From the LUD-01 specs:
                // > HTTP Status Codes and Content-Type:
                // > Neither status codes or any HTTP Header has any meaning. Servers may use
                // > whatever they want. Clients should ignore them [...] and just parse the
                // > response body as JSON, then interpret it accordingly.
                Json.decodeFromString(response.bodyAsText(Charsets.UTF_8))
            } catch (e: Exception) {
                logger.error(e) { "unhandled response from url=$url: " }
                throw LnurlError.RemoteFailure.Unreadable(url.host)
            }

            logger.debug { "lnurl service=${url.host} returned response=${json.toString().take(100)}" }
            return if (json["status"]?.jsonPrimitive?.content?.trim()?.equals("error", true) == true) {
                val errorMessage = json["reason"]?.jsonPrimitive?.content?.trim() ?: ""
                if (errorMessage.isNotEmpty()) {
                    logger.error { "lnurl service=${url.host} returned error=$errorMessage" }
                    throw LnurlError.RemoteFailure.Detailed(url.host, errorMessage.take(90).replace("<", ""))
                } else if (!response.status.isSuccess()) {
                    throw LnurlError.RemoteFailure.Code(url.host, response.status)
                } else {
                    throw LnurlError.RemoteFailure.Unreadable(url.host)
                }
            } else {
                json
            }
        }

        /** Converts a lnurl JSON response to a [Lnurl] object. */
        fun parseLnurlJson(url: Url, json: JsonObject): Lnurl {
            val callback = URLBuilder(json["callback"]?.jsonPrimitive?.content ?: throw LnurlError.MissingCallback).build()
            if (!callback.protocol.isSecure()) throw LnurlError.UnsafeResource
            val tag = json["tag"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: throw LnurlError.NoTag
            return when (tag) {
                Tag.Withdraw.label -> {
                    val k1 = json["k1"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: throw LnurlError.Withdraw.MissingK1
                    val minWithdrawable = json["minWithdrawable"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0f }?.toLong()?.msat
                        ?: json["minWithdrawable"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.msat
                        ?: 0.msat
                    val maxWithdrawable = json["maxWithdrawable"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0f }?.toLong()?.msat
                        ?: json["maxWithdrawable"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.msat
                        ?: minWithdrawable
                    val dDesc = json["defaultDescription"]?.jsonPrimitive?.content ?: ""
                    LnurlWithdraw(
                        initialUrl = url,
                        callback = callback,
                        k1 = k1,
                        defaultDescription = dDesc,
                        minWithdrawable = minWithdrawable.coerceAtMost(maxWithdrawable),
                        maxWithdrawable = maxWithdrawable
                    )
                }
                Tag.Pay.label -> {
                    val minSendable = json["minSendable"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0f }?.toLong()?.msat
                        ?: json["minSendable"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }?.msat
                        ?: throw LnurlError.Pay.Intent.InvalidMin
                    val maxSendable = json["maxSendable"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0f }?.toLong()?.msat
                        ?: json["maxSendable"]?.jsonPrimitive?.longOrNull?.coerceAtLeast(minSendable.msat)?.msat
                        ?: throw LnurlError.Pay.Intent.MissingMax
                    val metadata = LnurlPay.parseMetadata(json["metadata"]?.jsonPrimitive?.content ?: throw LnurlError.Pay.Intent.MissingMetadata)
                    val maxCommentLength = json["commentAllowed"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
                    LnurlPay.Intent(
                        initialUrl = url,
                        callback = callback,
                        minSendable = minSendable,
                        maxSendable = maxSendable,
                        metadata = metadata,
                        maxCommentLength = maxCommentLength
                    )
                }
                else -> throw LnurlError.UnhandledTag(tag)
            }
        }
    }
}
