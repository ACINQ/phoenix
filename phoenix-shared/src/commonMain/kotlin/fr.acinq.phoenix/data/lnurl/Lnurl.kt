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

import fr.acinq.bitcoin.Bech32
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.utils.Parser
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

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
        internal val log = newLogger(LoggerFactory.default)
        internal val format: Json = Json { ignoreUnknownKeys = true }

        /**
         * Attempts to extract a [Lnurl] from a string.
         *
         * @param source can be a bech32 lnurl, a non-bech32 lnurl, or a lightning address.
         * @return a [LnurlAuth] if the source is a login lnurl, or an [Url] if it is a payment/withdrawal lnurl.
         *
         * Throws an exception if the source is malformed or invalid.
         */
        fun extractLnurl(source: String): Lnurl {
            val input = Parser.trimMatchingPrefix(source, listOf("lightning://", "lightning:", "lnurl:"))
            val url: Url = try {
                parseBech32Url(input)
            } catch (bech32Ex: Exception) {
                log.info { "cannot parse source=$input as bech32 lnurl: ${bech32Ex.message ?: bech32Ex::class}" }
                try {
                    if (lud17Schemes.any { input.startsWith(it, ignoreCase = true) }) {
                        parseNonBech32Lud17(input)
                    } else if (input.contains('@')) {
                        parseInternetIdentifier(input)
                    } else {
                        parseNonBech32Http(input)
                    }
                } catch (nonBech32Ex: Exception) {
                    log.info { "cannot parse source=$input as non-bech32 lnurl: ${nonBech32Ex.message ?: nonBech32Ex::class}" }
                    throw LnurlError.Invalid(cause = nonBech32Ex)
                }
            }
            val tag = url.parameters["tag"]?.let {
                when (it) {
                    Tag.Auth.label -> Tag.Auth
                    Tag.Withdraw.label -> Tag.Withdraw
                    Tag.Pay.label -> Tag.Pay
                    else -> throw LnurlError.UnhandledTag(it)
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
        internal fun parseBech32Url(source: String): Url {
            val (hrp, data) = Bech32.decode(source)
            val payload = Bech32.five2eight(data, 0).decodeToString()
            log.debug { "reading serialized lnurl with hrp=$hrp and payload=$payload" }
            val url = URLBuilder(payload).build()
            if (!url.protocol.isSecure()) throw LnurlError.UnsafeResource
            return url
        }

        /** Lnurls sometimes hide in regular http urls, under the lightning parameter. */
        internal fun parseNonBech32Http(source: String): Url {
            val urlBuilder = URLBuilder(source)
            if (!urlBuilder.protocol.isSecure()) throw LnurlError.UnsafeResource
            val lightningParam = urlBuilder.parameters["lightning"]
            return if (!lightningParam.isNullOrBlank()) {
                parseBech32Url(lightningParam)
            } else {
                val tag = urlBuilder.parameters["tag"]
                if (Tag.values().any { it.label == tag }) {
                    urlBuilder.build()
                } else {
                    throw IllegalArgumentException("this url has no valid tag nor lnurl fallback")
                }
            }
        }

        private val lud17Schemes = listOf("lnurlp", "lnurlw", "keyauth")

        /** Converts LUD-17 lnurls (using a custom lnurl scheme like lnurlc, lnurlp, etc...) into a regular http url. */
        internal fun parseNonBech32Lud17(source: String): Url {
            return URLBuilder(source).apply {
                encodedPath.split("/", ignoreCase = true, limit = 2).let {
                    this.host = it.first()
                    this.encodedPath = "/${it.drop(1).joinToString()}"
                }
                protocol = when {
                    lud17Schemes.contains(protocol.name) -> if (this.host.endsWith(".onion")) {
                        URLProtocol.HTTP
                    } else {
                        URLProtocol.HTTPS
                    }
                    else -> throw IllegalArgumentException("unreadable url=$source")
                }
            }.build()
        }

        /** LUD-16 support: https://github.com/fiatjaf/lnurl-rfc/blob/luds/16.md */
        internal fun parseInternetIdentifier(source: String): Url {

            // Ignore excess input, including additional lines, and leading/trailing whitespace
            val line = source.lines().firstOrNull { it.isNotBlank() }?.trim() ?: throw RuntimeException("identifier has an empty leading line")
            val token = line.split("\\s+".toRegex()).firstOrNull() ?: throw RuntimeException("identifier has invalid chars")

            // The format is: <username>@<domain.tld>
            //
            // The username is technically limited to: a-z0-9-_.
            // But we don't enforce it, as it's a bit restrictive for a global audience.
            //
            // Note that, in the real world, users will type with capital letters.
            // So we need to auto-convert to lowercase.

            val components = token.split("@")
            if (components.size != 2) {
                throw RuntimeException("identifier must contain one @ delimiter")
            }

            val username = components[0].lowercase()
            val domain = components[1]

            // May throw an exception if domain is invalid
            return Url("https://$domain/.well-known/lnurlp/$username")
        }

        /**
         * Processes a HTTP response replied by a lnurl service and returns a [JsonObject].
         *
         * Throw:
         * - [LnurlError.RemoteFailure.Code] if service returns a non-2XX code
         * - [LnurlError.RemoteFailure.Unreadable] if response is not valid JSON
         * - [LnurlError.RemoteFailure.Detailed] if service reports an internal error message (`{ status: "error", reason: "..." }`)
         */
        suspend fun processLnurlResponse(response: HttpResponse): JsonObject {
            val url = response.request.url
            return try {
                if (response.status.isSuccess()) {
                    val json: JsonObject = Json.decodeFromString(response.bodyAsText(Charsets.UTF_8))
                    log.debug { "lnurl service=${url.host} returned response=$json" }
                    if (json["status"]?.jsonPrimitive?.content?.trim()?.equals("error", true) == true) {
                        log.error { "lnurl service=${url.host} returned error=$json" }
                        val message = json["reason"]?.jsonPrimitive?.content ?: ""
                        throw LnurlError.RemoteFailure.Detailed(url.host, message.take(160).replace("<", ""))
                    } else {
                        json
                    }
                } else {
                    throw LnurlError.RemoteFailure.Code(url.host, response.status)
                }
            } catch (e: Exception) {
                log.error(e) { "unhandled response from url=$url: " }
                when (e) {
                    is LnurlError.RemoteFailure -> throw e
                    else -> throw LnurlError.RemoteFailure.Unreadable(url.host)
                }
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
                    val minWithdrawable = json["minWithdrawable"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.msat ?: 0.msat
                    val maxWithdrawable = json["maxWithdrawable"]?.jsonPrimitive?.long?.coerceAtLeast(minWithdrawable.msat)?.msat ?: minWithdrawable
                    val dDesc = json["defaultDescription"]?.jsonPrimitive?.content ?: ""
                    LnurlWithdraw(
                        initialUrl = url,
                        callback = callback,
                        k1 = k1,
                        defaultDescription = dDesc,
                        minWithdrawable = minWithdrawable,
                        maxWithdrawable = maxWithdrawable
                    )
                }
                Tag.Pay.label -> {
                    val minSendable = json["minSendable"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.msat ?: throw LnurlError.Pay.Intent.InvalidMin
                    val maxSendable = json["maxSendable"]?.jsonPrimitive?.long?.coerceAtLeast(minSendable.msat)?.msat ?: throw LnurlError.Pay.Intent.MissingMax
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
