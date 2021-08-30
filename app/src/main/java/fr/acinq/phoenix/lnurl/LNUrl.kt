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

package fr.acinq.phoenix.lnurl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Parcelable
import android.util.Base64
import fr.acinq.bitcoin.Bech32
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.utils.Wallet
import kotlinx.android.parcel.Parcelize
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.math.max

sealed class LNUrlError(message: String = "") : RuntimeException(message) {
  data class PayInvalidMeta(val meta: String) : LNUrlError()
  object AuthMissingK1 : LNUrlError("missing parameter k1 in LNURL-auth url")
  data class WithdrawAtLeastMinSat(val min: MilliSatoshi) : LNUrlError()
  data class WithdrawAtMostMaxSat(val max: MilliSatoshi) : LNUrlError()
  data class UnhandledTag(val tag: String) : LNUrlError("unhandled LNURL tag=$tag")
  sealed class RemoteFailure : LNUrlError("service returned an error") {
    abstract val origin: String

    data class Generic(override val origin: String) : RemoteFailure()
    data class CouldNotConnect(override val origin: String) : RemoteFailure()
    data class Unreadable(override val origin: String) : RemoteFailure()
    data class Detailed(override val origin: String, val reason: String) : RemoteFailure()
    data class Code(override val origin: String, val code: Int) : RemoteFailure()
  }
}

interface LNUrl {

  companion object Util {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    val httpClient = Wallet.httpClient.newBuilder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .writeTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .build()

    /**
     * Decodes a Bech32 string and transform it into a https request.
     */
    private fun decodeUrl(source: String): HttpUrl {
      val res = Bech32.decode(source)
      val payload = String(Bech32.five2eight(res._2), Charsets.UTF_8)
      log.info("reading serialized lnurl with hrp=${res._1} and payload=$payload")
      val url = HttpUrl.get(payload)
      require(url.isHttps) { "invalid url=${url}, should be https" }
      return url
    }

    fun extractLNUrl(source: String): LNUrl {
      val url = decodeUrl(source)
      // special flow for login: do not send GET to url just yet
      return if (url.queryParameter("tag") == "login") {
        val k1 = url.queryParameter("k1")
        if (k1.isNullOrBlank()) {
          throw LNUrlError.AuthMissingK1
        } else {
          LNUrlAuth(url.toString(), k1)
        }
      } else {
        // otherwise execute GET to url to retrieve details from remote server
        val json = executeMetadataQuery(url)
        val tag = json.getString("tag")
        val callback = HttpUrl.get(json.getString("callback"))
        require(callback.isHttps) { "invalid callback=${url}, should be https" }
        require(callback.host() == url.host()) { "callback must use the same host than the original lnurl" }
        return when (tag) {
          "withdrawRequest" -> {
            val walletIdentifier = json.getString("k1").takeIf { it.isNotBlank() } ?: throw RuntimeException("missing k1")
            val minWithdrawable = MilliSatoshi(json.optLong("minWithdrawable").takeIf { it > 0 } ?: 1)
            val maxWithdrawable = MilliSatoshi(json.getLong("maxWithdrawable").coerceAtLeast(minWithdrawable.toLong()))
            val desc = json.getString("defaultDescription")
            LNUrlWithdraw(url.toString(), callback.toString(), walletIdentifier, desc, minWithdrawable, maxWithdrawable)
          }
          "payRequest" -> {
            val minSendable = MilliSatoshi(json.getLong("minSendable"))
            val maxSendable = MilliSatoshi(json.getLong("maxSendable"))
            val rawMetadata = buildLNUrlPayMeta(json.getString("metadata"))
            val maxCommentLength = json.optLong("commentAllowed").takeIf { it > 0 }
            LNUrlPay(callback.toString(), minSendable, maxSendable, rawMetadata, maxCommentLength)
          }
          else -> throw LNUrlError.UnhandledTag(tag)
        }
      }
    }

    private fun executeMetadataQuery(url: HttpUrl): JSONObject {
      log.info("retrieving metadata from LNURL=$url")
      val request = Request.Builder().url(url).build()
      return httpClient.newCall(request).execute().use { handleLNUrlRemoteResponse(it) }
    }

    fun handleLNUrlRemoteResponse(response: Response): JSONObject {
      val body = response.body()
      val origin = response.request().url().run { topPrivateDomain() ?: host() }
      try {
        if (response.isSuccessful && body != null) {
          val json = JSONObject(body.string())
          log.debug("remote lnurl service responded with: $json")
          if (json.has("status") && json.getString("status").trim().equals("error", true)) {
            log.error("lnurl service responded with error: $json")
            val message = if (json.has("reason")) json.getString("reason") else "N/A"
            throw LNUrlError.RemoteFailure.Detailed(origin, message.take(160).replace("<", ""))
          } else {
            return json
          }
        } else if (!response.isSuccessful) {
          throw LNUrlError.RemoteFailure.Code(origin, response.code())
        } else {
          throw LNUrlError.RemoteFailure.Generic(origin)
        }
      } catch (e: Exception) {
        log.error("failed to read LNUrl response: ", e)
        when (e) {
          is LNUrlError.RemoteFailure -> throw e
          else -> throw LNUrlError.RemoteFailure.Unreadable(origin)
        }
      } finally {
        body?.close()
      }
    }

    private fun buildLNUrlPayMeta(raw: String): LNUrlPayMetadata = try {
      val array = JSONArray(raw)
      var plainText: String? = null
      var rawImage: String? = null
      for (i in 0 until array.length()) {
        val rawMeta = array.getJSONArray(i)
        try {
          when (rawMeta.getString(0)) {
            "text/plain" -> plainText = rawMeta.getString(1)
            "image/png;base64" -> rawImage = rawMeta.getString(1)
            "image/jpeg;base64" -> rawImage = rawMeta.getString(1)
            else -> throw RuntimeException("unhandled metadata type=${rawMeta[i]}")
          }
        } catch (e: Exception) {
          log.warn("could not process raw meta=$rawMeta at index=$i: ${e.message}")
        }
      }
      LNUrlPayMetadata(raw, plainText!!, rawImage?.let { decodeBase64Image(it) })
    } catch (e: Exception) {
      log.error("could not read raw meta=$raw: ", e)
      throw LNUrlError.PayInvalidMeta(raw)
    }

    private fun decodeBase64Image(source: String): Bitmap? = try {
      val imageBytes = Base64.decode(source, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
      log.debug("lnurl-pay service provided an unreadable image")
      null
    }
  }
}

@Parcelize
class LNUrlAuth(val url: String, val k1: String) : LNUrl, Parcelable

@Parcelize
class LNUrlWithdraw(val origin: String, val callback: String, val walletIdentifier: String,
  val description: String, val minWithdrawable: MilliSatoshi, val maxWithdrawable: MilliSatoshi) : LNUrl, Parcelable

@Parcelize
class LNUrlPay(val callbackUrl: String, val minSendable: MilliSatoshi, val maxSendable: MilliSatoshi, val rawMetadata: LNUrlPayMetadata, val maxCommentLength: Long?) : LNUrl, Parcelable

@Parcelize
data class LNUrlPayMetadata(val raw: String, val plainText: String, val image: Bitmap?) : Parcelable
