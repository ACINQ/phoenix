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

package fr.acinq.phoenix.data

import fr.acinq.bitcoin.Bech32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.msat
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

/**
 * This class describes the various types of LNUrls supported by phoenix:
 * - auth
 * - pay
 * - withdraw
 *
 * It also contains the possible errors related to the LNUrl flow: erros that break the specs, or errors
 * raised when the data returned by the LNUrl service are not valid.
 *
 * A companion object contains utility methods to parse the urls, read a response from a LNURL service, and
 * transform this response in a valid LNUrl object.
 */
sealed class LNUrl {

    data class Auth(
        val url: Url,
        val k1: String
    ) : LNUrl() {
        
        enum class Action {
            Register, Login, Link, Auth
        }

        fun action(): Action? {
            return url.parameters.get("action")?.let { action ->
                when (action.toLowerCase()) {
                    "register" -> Action.Register
                    "login" -> Action.Login
                    "link" -> Action.Link
                    "auth" -> Action.Auth
                    else -> null
                }
            }
        }
    }

    data class Withdraw(
        val callback: Url,
        val walletIdentifier: String,
        val description: String,
        val minWithdrawable: MilliSatoshi,
        val maxWithdrawable: MilliSatoshi
    ) : LNUrl()

    data class Pay(
        val callback: Url,
        val minSendable: MilliSatoshi,
        val maxSendable: MilliSatoshi,
        val rawMetadata: Metadata,
        val maxCommentLength: Long?
    ) : LNUrl() {
        data class Metadata(val raw: String, val plainText: String, val image: ByteArray?)
    }

    enum class Tag(val label: String) {
        Auth("login"),
        Withdraw("withdrawRequest"),
        Pay("payRequest")
    }

    sealed class Error(override val message: String? = null) : RuntimeException(message) {
        val details: String by lazy { "LNUrl error=${message ?: this::class.simpleName ?: "N/A"} in url=" }

        sealed class Auth(override val message: String?) : Error(message) {
            object MissingK1 : Auth("missing k1 parameter")
        }

        sealed class Withdraw(override val message: String?) : Error(message) {
            object MissingK1 : Withdraw("missing k1 parameter in auth metadata")
            object MissingDescription : Withdraw("missing description parameter in auth metadata")
            data class AmountAtLeast(val min: MilliSatoshi) : Withdraw("amount must be at least $min")
            data class AmountAtMost(val min: MilliSatoshi) : Withdraw("amount must be at least $min")
        }

        sealed class Pay(override val message: String?) : Error(message) {
            data class InvalidMeta(val meta: String) : Error("invalid meta=$meta")
            object InvalidMin : Error("invalid minimum amount")
            object MissingMax : Error("missing maximum amount parameter")
            object MissingMetadata : Error("missing metadata parameter")
        }

        object Invalid : Error("url cannot be parsed as a bech32 or as a human readable url")
        object NoTag : Error("no tag field found")
        data class UnhandledTag(val tag: String) : Error("unhandled tag=$tag")
        object UnsafeCallback : Error("resource should be https")
        object MissingCallback : Error("missing callback in metadata response")

        object MissingPublicSuffixList : Error("missing public suffix list")
        object CouldNotDetermineDomain : Error("could not determine domain")

        sealed class RemoteFailure(override val message: String) : Error(message) {
            abstract val origin: String

            data class CouldNotConnect(override val origin: String) : RemoteFailure("could not connect to $origin")
            data class Unreadable(override val origin: String) : RemoteFailure("unreadable response from $origin")
            data class Detailed(override val origin: String, val reason: String) : RemoteFailure("error=$reason from $origin")
            data class Code(override val origin: String, val code: HttpStatusCode) : RemoteFailure("error code=$code from $origin")
        }
    }

    companion object {
        private val log = newLogger(LoggerFactory.default)
        private val format: Json = Json { ignoreUnknownKeys = true }

        /** LNUrls are originally bech32 encoded. If unreadable, throw an exception. */
        fun parseBech32Url(source: String): Url {
            val (hrp, data) = Bech32.decode(source)
            val payload = Bech32.five2eight(data, 0).decodeToString()
            log.debug { "reading serialized lnurl with hrp=$hrp and payload=$payload" }
            val url = URLBuilder(payload).build()
            if (!url.protocol.isSecure()) throw Error.UnsafeCallback
            return url
        }

        /** Convert human readable LNUrls (using a custom lnurl scheme like lnurlc, lnurlp, etc...) into a regular http url. */
        fun parseNonBech32Url(source: String): Url {
            return URLBuilder(source).apply {
                encodedPath.drop(1).split("/", ignoreCase = true, limit = 2).let {
                    this.host = it.first()
                    this.encodedPath = "/${it.drop(1).joinToString()}"
                }
                protocol = when (protocol.name) {
                    "lnurlp", "lnurlw", "keyauth" -> if (this.host.endsWith(".onion")) {
                        URLProtocol.HTTP
                    } else {
                        URLProtocol.HTTPS
                    }
                    else -> throw IllegalArgumentException("unreadable url=$source")
                }
            }.build()
        }

        /**
         * Read the response from a LNUrl service and throw an [LNUrl.Error] exception if the service returns a http error code,
         * an invalid JSON body, or a response of such format: `{ status: "error", reason: "..." }`.
         */
        suspend fun handleLNUrlResponse(response: HttpResponse): JsonObject {
            val url = response.request.url
            return try {
                if (response.status.isSuccess()) {
                    val json: JsonObject = response.receive()
                    log.debug { "lnurl service=${url.host} returned response=$json" }
                    if (json.get("status")?.jsonPrimitive?.content?.trim()?.equals("error", true) == true) {
                        log.error { "lnurl service=${url.host} returned error=$json" }
                        val message = json.get("reason")?.jsonPrimitive?.content ?: ""
                        throw Error.RemoteFailure.Detailed(url.host, message.take(160).replace("<", ""))
                    } else {
                        json
                    }
                } else {
                    throw Error.RemoteFailure.Code(url.host, response.status)
                }
            } catch (e: Exception) {
                log.error(e) { "unhandled response from $url: " }
                when (e) {
                    is Error.RemoteFailure -> throw e
                    else -> throw Error.RemoteFailure.Unreadable(url.host)
                }
            }
        }

        /** Convert a lnurl metadata json response into a [LNUrl] object. */
        fun parseLNUrlMetadata(json: JsonObject): LNUrl {
            val callback = URLBuilder(json["callback"]?.jsonPrimitive?.content ?: throw Error.MissingCallback).build()
            if (!callback.protocol.isSecure()) throw Error.UnsafeCallback
            val tag = json["tag"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: throw Error.NoTag
            return when (tag) {
                Tag.Withdraw.label -> {
                    val walletIdentifier = json["k1"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: throw Error.Withdraw.MissingK1
                    val minWithdrawable = json["minWithdrawable"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.msat ?: 0.msat
                    val maxWithdrawable = json["maxWithdrawable"]?.jsonPrimitive?.long?.coerceAtLeast(minWithdrawable.msat)?.msat ?: minWithdrawable
                    val desc = json["defaultDescription"]?.jsonPrimitive?.content ?: throw Error.Withdraw.MissingDescription
                    Withdraw(callback, walletIdentifier, desc, minWithdrawable, maxWithdrawable)

                }
                Tag.Pay.label -> {
                    val minSendable = json["minSendable"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.msat ?: throw Error.Pay.InvalidMin
                    val maxSendable = json["maxSendable"]?.jsonPrimitive?.long?.coerceAtLeast(minSendable.msat)?.msat ?: throw Error.Pay.MissingMax
                    val rawMetadata = decodeLNUrlPayMeta(json["metadata"]?.jsonPrimitive?.content ?: throw Error.Pay.MissingMetadata)
                    val maxCommentLength = json["commentAllowed"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
                    Pay(callback, minSendable, maxSendable, rawMetadata, maxCommentLength)
                }
                else -> throw Error.UnhandledTag(tag)
            }
        }

        /** Decode a serialized [LNUrl.Pay.Metadata] object. */
        private fun decodeLNUrlPayMeta(raw: String): Pay.Metadata = try {
            val array = format.decodeFromString<JsonArray>(raw)
            var plainText: String? = null
            var rawImage: String? = null
            array.forEach {
                try {
                    when (it.jsonArray[0].jsonPrimitive.content) {
                        "text/plain" -> plainText = it.jsonArray[1].jsonPrimitive.content
                        "image/png;base64" -> rawImage = it.jsonArray[1].jsonPrimitive.content
                        "image/jpeg;base64" -> rawImage = it.jsonArray[1].jsonPrimitive.content
                        else -> throw RuntimeException("unhandled metadata type=$it")
                    }
                } catch (e: Exception) {
                    log.warning { "could not decode raw meta=$it: ${e.message}" }
                }
            }
            Pay.Metadata(raw, plainText!!, rawImage?.encodeToByteArray())
        } catch (e: Exception) {
            log.error(e) { "could not decode raw meta=$raw: " }
            throw Error.Pay.InvalidMeta(raw)
        }
    }
}
