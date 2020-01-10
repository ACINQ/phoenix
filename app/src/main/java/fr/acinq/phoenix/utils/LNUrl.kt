/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.phoenix.utils

import android.os.Parcelable
import fr.acinq.bitcoin.Bech32
import fr.acinq.eclair.MilliSatoshi
import kotlinx.android.parcel.Parcelize
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.max

class LNUrlUnhandledTag(tag: String) : RuntimeException("unhandled LNURL tag=$tag")
class LNUrlRemoteFailure(message: String) : RuntimeException(message)
class LNUrlRemoteError(message: String) : RuntimeException(message)
class LNUrlAuthMissingK1 : RuntimeException("missing parameter k1 in LNURL-auth url")

interface LNUrl {

  companion object Util {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

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
          LNUrlAuth(url.toString(), k1)
        }
      } else {
        // otherwise execute GET to url to retrieve details from remote server
        val json = getMetadataFromBaseUrl(url)
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

    private fun getMetadataFromBaseUrl(url: HttpUrl): JSONObject {
      log.info("retrieving metadata from LNURL=$url")
      return handleLNUrlRemoteResponse(Wallet.httpClient.newCall(Request.Builder().url(url).build()).execute())
    }

    public fun handleLNUrlRemoteResponse(response: Response): JSONObject {
      val body = response.body()
      if (response.isSuccessful && body != null) {
        val json = JSONObject(body.string())
        if (json.has("status") && json.getString("status").trim().equals("error", true)) {
          val message = if (json.has("reason")) json.getString("reason") else "N/A"
          throw LNUrlRemoteError(message)
        } else {
          return json
        }
      } else if (!response.isSuccessful) {
        throw LNUrlRemoteFailure(response.code().toString())
      } else {
        throw LNUrlRemoteFailure("empty body")
      }
    }
  }
}

@Parcelize
class LNUrlAuth(val callback: String, val k1: String) : LNUrl, Parcelable

@Parcelize
class LNUrlWithdraw(val origin: String, val callback: String, val walletIdentifier: String, val description: String, val minWithdrawable: MilliSatoshi, val maxWithdrawable: MilliSatoshi) : LNUrl,
                                                                                                                                                                                             Parcelable {

}
