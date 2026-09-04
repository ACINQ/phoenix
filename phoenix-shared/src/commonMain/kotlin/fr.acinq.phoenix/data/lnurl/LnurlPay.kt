/*
 * Copyright 2022 ACINQ SAS
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
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.data.lnurl.Lnurl.Companion.format
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

sealed class LnurlPay : Lnurl.Qualified {

    /**
     * Response from a lnurl service to describe what kind of payment is expected.
     * First step of the lnurl-pay flow.
     */
    data class Intent(
        override val initialUrl: Url,
        val callback: Url,
        val minSendable: MilliSatoshi,
        val maxSendable: MilliSatoshi,
        val metadata: Metadata,
        val maxCommentLength: Long?
    ) : LnurlPay() {
        data class Metadata(
            val raw: String,
            val plainText: String,
            val longDesc: String?,
            val imagePng: String?, // base64 encoded png
            val imageJpg: String?, // base64 encoded jpg
            val identifier: String?,
            val email: String?,
            val unknown: JsonArray?
        ) {
            val lnid: String? by lazy { email ?: identifier }

            override fun toString(): String {
                return "Metadata(plainText=$plainText, longDesc=${longDesc?.take(50)}, identifier=$identifier, email=$email, imagePng=${imagePng?.take(10)}, imageJpg=${imageJpg?.take(10)})"
            }
        }

        override fun toString(): String {
            return "Intent(minSendable=$minSendable, maxSendable=$maxSendable, metadata=$metadata, maxCommentLength=$maxCommentLength, initialUrl=$initialUrl, callback=$callback)".take(100)
        }
    }

    /**
     * Invoice returned by a lnurl service after user states what they want to pay.
     * Second step of the lnurl-payment flow.
     */
    data class Invoice(
        override val initialUrl: Url,
        val invoice: Bolt11Invoice,
        val successAction: SuccessAction?
    ) : LnurlPay() {
        sealed class SuccessAction {
            data class Message(
                val message: String
            ) : SuccessAction()

            data class Url(
                val description: String,
                val url: io.ktor.http.Url
            ) : SuccessAction()

            data class Aes(
                val description: String,
                val ciphertext: ByteVector,
                val iv: ByteVector
            ) : SuccessAction() {
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


    companion object {

        /** Parses json into a [LnurlPay.Invoice] object. Throws an [LnurlError.PayInvoice] exception if unreadable. */
        fun parseLnurlPayInvoice(
            intent: Intent,
            json: JsonObject
        ): Invoice {
            try {
                val pr = json["pr"]?.jsonPrimitive?.content ?: throw LnurlError.Pay.Invoice.Malformed(intent.callback.host, "missing pr")
                val invoice = when (val res = Bolt11Invoice.read(pr)) {
                    is Try.Success -> res.result
                    is Try.Failure -> throw LnurlError.Pay.Invoice.Malformed(intent.callback.host, res.error.message ?: res.error::class.toString())
                }

                val successAction = parseSuccessAction(intent.callback.host, json)
                return Invoice(intent.initialUrl, invoice, successAction)
            } catch (t: Throwable) {
                when (t) {
                    is LnurlError.Pay.Invoice -> throw t
                    else -> throw LnurlError.Pay.Invoice.Malformed(intent.callback.host, "unknown error")
                }
            }
        }

        /**
         * See LUD09/LUD10.
         * @param origin the lnurl-pay callback's host
         */
        private fun parseSuccessAction(
            origin: String,
            json: JsonObject
        ): Invoice.SuccessAction? {
            val obj = when (val successAction = json["successAction"]) {
                null -> return null
                is JsonNull -> return null
                is JsonObject -> successAction.jsonObject
                else -> throw LnurlError.Pay.Invoice.Malformed(origin, "success: invalid successAction content")
            }

            return when (val tag = obj["tag"]?.jsonPrimitive?.content) {
                Invoice.SuccessAction.Tag.Message.label -> {
                    val message = obj["message"]?.jsonPrimitive?.content
                    if (message.isNullOrBlank() || message.length > 144) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.message.message: missing or bad length")
                    }
                    Invoice.SuccessAction.Message(message)
                }
                Invoice.SuccessAction.Tag.Url.label -> {
                    val description = obj["description"]?.jsonPrimitive?.content
                    if (description.isNullOrBlank() || description.length > 144) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.url.description: missing or bad length")
                    }
                    val urlStr = obj["url"]?.jsonPrimitive?.content ?: throw LnurlError.Pay.Invoice.Malformed(origin, "success.url.url: missing url")
                    val url = try {
                        Url(urlStr)
                    } catch (_: Exception) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.url.url: invalid url")
                    }
                    if (!url.protocol.isSecure()) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.url.url: TLS required")
                    }
                    if (url.host != origin) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.url.url: callback host mismatch")
                    }
                    Invoice.SuccessAction.Url(description, url)
                }
                Invoice.SuccessAction.Tag.Aes.label -> {
                    val description = obj["description"]?.jsonPrimitive?.content
                    if (description.isNullOrBlank() || description.length > 144) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.description: bad length")
                    }
                    val ciphertextStr = obj["ciphertext"]?.jsonPrimitive?.content ?: throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.ciphertext: missing")
                    val ciphertext = try {
                        Base64.decode(ciphertextStr).toByteVector()
                    } catch (_: Exception) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.ciphertext: invalid b64")
                    }
                    if (ciphertext.size() > (4 * 1024)) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.ciphertext: bad length")
                    }
                    val ivStr = obj["iv"]?.jsonPrimitive?.content
                    if (ivStr.isNullOrBlank() || ivStr.length != 24) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.iv: missing or bad length")
                    }
                    val iv = try {
                        Base64.decode(ivStr).toByteVector()
                    } catch (_: Exception) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.iv: invalid b64")
                    }
                    Invoice.SuccessAction.Aes(description, ciphertext = ciphertext, iv = iv)
                }
                else -> {
                    throw LnurlError.Pay.Invoice.Malformed(origin, "unhandled tag action: $tag")
                }
            }
        }

        /** Decode a serialized [Lnurl.Pay.Metadata] object. */
        fun parseMetadata(raw: String): LnurlPay.Intent.Metadata = try {
            val array = format.decodeFromString<JsonArray>(raw)
            var plainText: String? = null
            var longDesc: String? = null
            var imagePng: String? = null
            var imageJpg: String? = null
            var identifier: String? = null
            var email: String? = null
            val unknown = mutableListOf<JsonElement>()
            array.forEach {
                try {
                    when (it.jsonArray[0].jsonPrimitive.content) {
                        "text/plain" -> plainText = it.jsonArray[1].jsonPrimitive.content
                        "text/long-desc" -> longDesc = it.jsonArray[1].jsonPrimitive.content
                        "image/png;base64" -> imagePng = it.jsonArray[1].jsonPrimitive.content
                        "image/jpeg;base64" -> imageJpg = it.jsonArray[1].jsonPrimitive.content
                        "text/identifier" -> identifier = it.jsonArray[1].jsonPrimitive.content
                        "text/email" -> email = it.jsonArray[1].jsonPrimitive.content
                        else -> unknown.add(it)
                    }
                } catch (e: Exception) {
                    Logger.w("LnurlPay") { "could not decode raw lnurlpay-meta=$it: ${e.message}" }
                }
            }
            LnurlPay.Intent.Metadata(
                raw = raw,
                plainText = plainText!!,
                longDesc = longDesc,
                imagePng = imagePng,
                imageJpg = imageJpg,
                identifier = identifier,
                email = email,
                unknown = unknown.takeIf { it.isNotEmpty() }?.let {
                    JsonArray(it.toList())
                }
            )
        } catch (e: Exception) {
            Logger.e("LnurlPay") { "could not decode raw lnurlpay-meta=$raw: ${e.message}" }
            throw LnurlError.Pay.Intent.InvalidMetadata(raw)
        }
    }
}


