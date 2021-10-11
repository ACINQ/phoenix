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
import fr.acinq.bitcoin.ByteVector
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.db.cloud.b64Decode
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

/**
 * This class describes the various types of LNUrls supported by phoenix:
 * - auth
 * - pay
 * - withdraw
 *
 * It also contains the possible errors related to the LNUrl flow:
 * errors that break the specs, or errors raised when the data returned
 * by the LNUrl service are not valid.
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
            return url.parameters["action"]?.let { action ->
                when (action.lowercase()) {
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
        val lnurl: Url,
        val callback: Url,
        val walletIdentifier: String,
        val description: String,
        val minWithdrawable: MilliSatoshi,
        val maxWithdrawable: MilliSatoshi
    ) : LNUrl()

    data class Pay(
        val lnurl: Url,
        val callback: Url,
        val minSendable: MilliSatoshi,
        val maxSendable: MilliSatoshi,
        val metadata: Metadata,
        val maxCommentLength: Long?
    ) : LNUrl() {
        data class Metadata(
            val raw: String,
            val plainText: String,
            val longDesc: String?,
            val imagePng: ByteVector?,
            val imageJpg: ByteVector?,
            val unknown: JsonArray?
        )
    }

    data class PayInvoice(
        val paymentRequest: PaymentRequest,
        val successAction: SuccessAction?
    ): LNUrl() {
        sealed class SuccessAction {
            data class Message(
                val message: String
            ): SuccessAction()
            data class Url(
                val description: String,
                val url: io.ktor.http.Url
            ): SuccessAction()
            data class Aes(
                val description: String,
                val ciphertext: ByteVector,
                val iv: ByteVector
            ): SuccessAction() {
                data class Decrypted(
                    val description: String,
                    val plaintext: String
                )
            }

            enum class Tag(val label: String) {
                Message("message"),
                Url("url"),
                Aes("aes")
            }
        }
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
            object InvalidMin : Pay("invalid minimum amount")
            object MissingMax : Pay("missing maximum amount parameter")
            object MissingMetadata : Pay("missing metadata parameter")
            data class InvalidMetadata(val meta: String) : Pay("invalid meta=$meta")
        }

        sealed class PayInvoice(override val message: String?) : Error(message) {
            abstract val origin: String

            data class Malformed(override val origin: String): PayInvoice("malformed json")
            data class MissingPr(override val origin: String): PayInvoice("missing pr value")
            data class MalformedPr(override val origin: String): PayInvoice("malformed pr value")
            data class InvalidHash(override val origin: String): PayInvoice("paymentRequest.h value doesn't match metadata hash")
            data class InvalidAmount(override val origin: String): PayInvoice("paymentRequest.amount doesn't match user input")
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
         * Read the response from a LNUrl service and throw an [LNUrl.Error.RemoteFailure] exception if the service returns a http error code,
         * an invalid JSON body, or a response of such format: `{ status: "error", reason: "..." }`.
         */
        suspend fun handleLNUrlResponse(response: HttpResponse): JsonObject {
            val url = response.request.url
            return try {
                if (response.status.isSuccess()) {
                    val json: JsonObject = Json.decodeFromString(response.readText(Charsets.UTF_8))
                    log.debug { "lnurl service=${url.host} returned response=$json" }
                    if (json["status"]?.jsonPrimitive?.content?.trim()?.equals("error", true) == true) {
                        log.error { "lnurl service=${url.host} returned error=$json" }
                        val message = json["reason"]?.jsonPrimitive?.content ?: ""
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

        /** Convert a lnurl json response into a [LNUrl] object. */
        fun parseLNUrlResponse(lnurl: Url, json: JsonObject): LNUrl {
            val callback = URLBuilder(json["callback"]?.jsonPrimitive?.content ?: throw Error.MissingCallback).build()
            if (!callback.protocol.isSecure()) throw Error.UnsafeCallback
            val tag = json["tag"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: throw Error.NoTag
            return when (tag) {
                Tag.Withdraw.label -> {
                    val walletIdentifier = json["k1"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: throw Error.Withdraw.MissingK1
                    val minWithdrawable = json["minWithdrawable"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.msat ?: 0.msat
                    val maxWithdrawable = json["maxWithdrawable"]?.jsonPrimitive?.long?.coerceAtLeast(minWithdrawable.msat)?.msat ?: minWithdrawable
                    val desc = json["defaultDescription"]?.jsonPrimitive?.content ?: throw Error.Withdraw.MissingDescription
                    Withdraw(
                        lnurl = lnurl,
                        callback = callback,
                        walletIdentifier = walletIdentifier,
                        description = desc,
                        minWithdrawable = minWithdrawable,
                        maxWithdrawable = maxWithdrawable
                    )
                }
                Tag.Pay.label -> {
                    val minSendable = json["minSendable"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.msat ?: throw Error.Pay.InvalidMin
                    val maxSendable = json["maxSendable"]?.jsonPrimitive?.long?.coerceAtLeast(minSendable.msat)?.msat ?: throw Error.Pay.MissingMax
                    val metadata = decodeLNUrlPayMetadata(json["metadata"]?.jsonPrimitive?.content ?: throw Error.Pay.MissingMetadata)
                    val maxCommentLength = json["commentAllowed"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
                    Pay(
                        lnurl = lnurl,
                        callback = callback,
                        minSendable = minSendable,
                        maxSendable = maxSendable,
                        metadata = metadata,
                        maxCommentLength = maxCommentLength
                    )
                }
                else -> throw Error.UnhandledTag(tag)
            }
        }

        /** Decode a serialized [LNUrl.Pay.Metadata] object. */
        @OptIn(ExperimentalSerializationApi::class)
        internal fun decodeLNUrlPayMetadata(raw: String): Pay.Metadata =
            Helper.decodeLNUrlPayMetadata(raw = raw, log = log)

        /**
         * Reads the json response from a LNUrl.Pay request,
         * and attempts to parse a PayInvoice object.
         *
         * Throws an [LNUrl.Error.PayInvoice] exception for invalid responses.
         */
        fun parseLNUrlPayResponse(
            origin: String,
            json: JsonObject
        ): PayInvoice {
            try {
                val pr =
                    json["pr"]?.jsonPrimitive?.content ?: throw Error.PayInvoice.MissingPr(origin)
                val paymentRequest = try {
                    PaymentRequest.read(pr) // <- throws
                } catch (t: Throwable) {
                    throw Error.PayInvoice.MalformedPr(origin)
                }
                val successAction = extractSuccessAction(origin, json)
                return PayInvoice(paymentRequest, successAction)
            } catch (t: Throwable) {
                when (t) {
                    is Error.PayInvoice -> throw t
                    else -> throw Error.PayInvoice.Malformed(origin)
                }
            }
        }

        private fun extractSuccessAction(
            origin: String,
            json: JsonObject
        ): PayInvoice.SuccessAction? {
            val obj = json["successAction"]?.jsonObject ?: return null

            return when (obj["tag"]?.jsonPrimitive?.content) {
                PayInvoice.SuccessAction.Tag.Message.label -> {
                    val message = obj["message"]?.jsonPrimitive?.content ?: return null
                    if (message.isBlank() || message.length > 144) {
                        throw Error.PayInvoice.Malformed(origin)
                    }
                    PayInvoice.SuccessAction.Message(message)
                }
                PayInvoice.SuccessAction.Tag.Url.label -> {
                    val description = obj["description"]?.jsonPrimitive?.content ?: return null
                    if (description.length > 144) {
                        throw Error.PayInvoice.Malformed(origin)
                    }
                    val urlStr = obj["url"]?.jsonPrimitive?.content ?: return null
                    val url = Url(urlStr)
                    PayInvoice.SuccessAction.Url(description, url)
                }
                PayInvoice.SuccessAction.Tag.Aes.label -> {
                    val description = obj["description"]?.jsonPrimitive?.content ?: return null
                    if (description.length > 144) {
                        throw Error.PayInvoice.Malformed(origin)
                    }
                    val ciphertextStr = obj["ciphertext"]?.jsonPrimitive?.content ?: return null
                    val ciphertext = ByteVector(ciphertextStr.b64Decode())
                    if (ciphertext.size() > (4*1024)) {
                        throw Error.PayInvoice.Malformed(origin)
                    }
                    val ivStr = obj["iv"]?.jsonPrimitive?.content ?: return null
                    if (ivStr.length != 24) {
                        throw Error.PayInvoice.Malformed(origin)
                    }
                    val iv = ByteVector(ivStr.b64Decode())
                    PayInvoice.SuccessAction.Aes(description, ciphertext = ciphertext, iv = iv)
                }
                else -> null
            }
        }
    }

    /* Why do we have a separate Helper object ?
     *
     * Since the `companion object` has a logger, this somehow freezes the companion object,
     * which prevents us from calling any LNUrl.staticFunctions() from a background thread.
     *
     * > Uncaught Kotlin exception:
     * >   kotlin.native.IncorrectDereferenceException:
     * >   Trying to access top level value not marked as @ThreadLocal or @SharedImmutable
     * >   from non-main thread
     *
     * This is partly due to Kotlin-native imposing its unworkable threading model on us.
     * And partly due to something in the logger which triggers a freeze.
     *
     * This Helper is a temporary workaround until we fix the problem properly.
     */
    object Helper {
        @OptIn(ExperimentalSerializationApi::class)
        internal fun decodeLNUrlPayMetadata(
            raw: String,
            log: Logger? = null
        ): Pay.Metadata = try {
            val format = Json { ignoreUnknownKeys = true }
            val array = format.decodeFromString<JsonArray>(raw)
            var plainText: String? = null
            var longDesc: String? = null
            var imagePng: String? = null
            var imageJpg: String? = null
            val unknown = mutableListOf<JsonElement>()
            array.forEach {
                try {
                    when (it.jsonArray[0].jsonPrimitive.content) {
                        "text/plain" -> plainText = it.jsonArray[1].jsonPrimitive.content
                        "text/long-desc" -> longDesc = it.jsonArray[1].jsonPrimitive.content
                        "image/png;base64" -> imagePng = it.jsonArray[1].jsonPrimitive.content
                        "image/jpeg;base64" -> imageJpg = it.jsonArray[1].jsonPrimitive.content
                        else -> unknown.add(it)
                    }
                } catch (e: Exception) {
                    log?.warning { "could not decode raw meta=$it: ${e.message}" }
                }
            }
            Pay.Metadata(
                raw = raw,
                plainText = plainText!!,
                longDesc = longDesc,
                imagePng = imagePng?.let { ByteVector(it.encodeToByteArray()) },
                imageJpg = imageJpg?.let { ByteVector(it.encodeToByteArray()) },
                unknown = unknown.takeIf { it.isNotEmpty() }?.let {
                    JsonArray(it.toList())
                }
            )
        } catch (e: Exception) {
            log?.error(e) { "could not decode raw meta=$raw: " }
            throw Error.Pay.InvalidMetadata(raw)
        }
    }
}
