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

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.Try
import fr.acinq.phoenix.data.lnurl.Lnurl.Companion.format
import fr.acinq.phoenix.data.lnurl.Lnurl.Companion.log
import fr.acinq.phoenix.db.cloud.b64Decode
import fr.acinq.phoenix.utils.loggerExtensions.*
import io.ktor.http.*
import kotlinx.serialization.json.*

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
        val paymentRequest: PaymentRequest,
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
            origin: String,
            json: JsonObject
        ): Invoice {
            try {
                val pr = json["pr"]?.jsonPrimitive?.content ?: throw LnurlError.Pay.Invoice.Malformed(origin, "missing pr")
                val paymentRequest = when (val res = PaymentRequest.read(pr)) {
                    is Try.Success -> res.result
                    is Try.Failure -> throw LnurlError.Pay.Invoice.Malformed(origin, res.error.message ?: res.error::class.toString())
                }

                val successAction = parseSuccessAction(origin, json)
                return Invoice(intent.initialUrl, paymentRequest, successAction)
            } catch (t: Throwable) {
                when (t) {
                    is LnurlError.Pay.Invoice -> throw t
                    else -> throw LnurlError.Pay.Invoice.Malformed(origin, "unknown error")
                }
            }
        }

        private fun parseSuccessAction(
            origin: String,
            json: JsonObject
        ): Invoice.SuccessAction? {
            val obj = try {
                json["successAction"]?.jsonObject // throws on Non-JsonObject (e.g. JsonNull)
            } catch (t: Throwable) {
                null
            } ?: return null

            return when (obj["tag"]?.jsonPrimitive?.content) {
                Invoice.SuccessAction.Tag.Message.label -> {
                    val message = obj["message"]?.jsonPrimitive?.content ?: return null
                    if (message.isBlank() || message.length > 144) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.message: bad length")
                    }
                    Invoice.SuccessAction.Message(message)
                }
                Invoice.SuccessAction.Tag.Url.label -> {
                    val description = obj["description"]?.jsonPrimitive?.content ?: return null
                    if (description.length > 144) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.url.description: bad length")
                    }
                    val urlStr = obj["url"]?.jsonPrimitive?.content ?: return null
                    val url = Url(urlStr)
                    Invoice.SuccessAction.Url(description, url)
                }
                Invoice.SuccessAction.Tag.Aes.label -> {
                    val description = obj["description"]?.jsonPrimitive?.content ?: return null
                    if (description.length > 144) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.description: bad length")
                    }
                    val ciphertextStr = obj["ciphertext"]?.jsonPrimitive?.content ?: return null
                    val ciphertext = ByteVector(ciphertextStr.b64Decode())
                    if (ciphertext.size() > (4 * 1024)) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.ciphertext: bad length")
                    }
                    val ivStr = obj["iv"]?.jsonPrimitive?.content ?: return null
                    if (ivStr.length != 24) {
                        throw LnurlError.Pay.Invoice.Malformed(origin, "success.aes.iv: bad length")
                    }
                    val iv = ByteVector(ivStr.b64Decode())
                    Invoice.SuccessAction.Aes(description, ciphertext = ciphertext, iv = iv)
                }
                else -> null
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
                    log.warning { "could not decode raw meta=$it: ${e.message}" }
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
            log.error(e) { "could not decode raw meta=$raw: " }
            throw LnurlError.Pay.Intent.InvalidMetadata(raw)
        }
    }
}


