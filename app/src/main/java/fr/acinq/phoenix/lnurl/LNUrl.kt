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

import android.os.Parcelable
import fr.acinq.bitcoin.Bech32
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.utils.Wallet
import kotlinx.android.parcel.Parcelize
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.math.max

class LNUrlUnhandledTag(tag: String) : RuntimeException("unhandled LNURL tag=$tag")

sealed class LNUrlRemoteFailure : java.lang.RuntimeException() {
  object Generic : LNUrlRemoteFailure()
  object CouldNotConnect : LNUrlRemoteFailure()
  object Unreadable : LNUrlRemoteFailure()
  data class Detailed(val reason: String) : LNUrlRemoteFailure()
  data class Code(val code: Int) : LNUrlRemoteFailure()
}

class LNUrlAuthMissingK1 : RuntimeException("missing parameter k1 in LNURL-auth url")
class LNUrlWithdrawAtLeastMinSat(val min: MilliSatoshi) : RuntimeException()
class LNUrlWithdrawAtMostMaxSat(val max: MilliSatoshi) : RuntimeException()

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
        if (k1 == null) {
          throw LNUrlAuthMissingK1()
        } else {
          LNUrlAuth(url.host()!!, url.toString(), k1)
        }
      } else {
        // otherwise execute GET to url to retrieve details from remote server
        val json = executeMetadataQuery(url)
        val tag = json.getString("tag")
        val callback = HttpUrl.get(json.getString("callback"))
        require(callback.isHttps) { "invalid callback=${url}, should be https" }
        return when (tag) {
          "withdrawRequest" -> {
            val walletIdentifier = json.getString("k1")
            val maxWithdrawable = MilliSatoshi(json.getLong("maxWithdrawable"))
            val minWithdrawable = MilliSatoshi(max(maxWithdrawable.toLong(), if (json.has("minWithdrawable")) json.getLong("minWithdrawable") else 1))
            val desc = json.getString("defaultDescription")
            LNUrlWithdraw(url.toString(), callback.toString(), walletIdentifier, desc, minWithdrawable, maxWithdrawable)
          }
          else -> throw LNUrlUnhandledTag(tag)
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
      try {
        if (response.isSuccessful && body != null) {
          val json = JSONObject(body.string())
          log.debug("remote lnurl service responded with: $json")
          if (json.has("status") && json.getString("status").trim().equals("error", true)) {
            log.error("lnurl service responded with error: $json")
            val message = if (json.has("reason")) json.getString("reason") else "N/A"
            throw LNUrlRemoteFailure.Detailed(message)
          } else {
            return json
          }
        } else if (!response.isSuccessful) {
          throw LNUrlRemoteFailure.Code(response.code())
        } else {
          throw LNUrlRemoteFailure.Generic
        }
      } catch (e: JSONException) {
        log.error("failed to read LNUrl response: ", e)
        throw LNUrlRemoteFailure.Unreadable
      } finally {
        body?.close()
      }
    }
  }
}

@Parcelize
class LNUrlAuth(val host: String, val authEndpoint: String, val k1: String) : LNUrl, Parcelable

@Parcelize
class LNUrlWithdraw(val origin: String, val callback: String, val walletIdentifier: String,
  val description: String, val minWithdrawable: MilliSatoshi, val maxWithdrawable: MilliSatoshi) : LNUrl, Parcelable
