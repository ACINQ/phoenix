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

package fr.acinq.eclair.phoenix.utils

import android.net.Uri
import fr.acinq.bitcoin.Bech32
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scodec.bits.BitVector

class LNUrl(source: String) {

  val uri: Uri

  init {
    uri = read(source)
  }

  fun isLogin(): Boolean {
    return uri.getQueryParameter(TAG_PARAM) == "login"
  }

  companion object Utils {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val TAG_PARAM: String = "tag"

    /**
     * Tries to read a Bech32 string and transform it into a https request.
     */
    private fun read(source: String): Uri {
      val res = Bech32.decode(source)
      val payload = String(res._2.map { BitVector.fromByte(it, 5) }.fold(BitVector.empty(), { acc, bitVector -> acc.`$plus$plus`(bitVector) }).toByteArray())
      log.info("reading serialized lnurl with hrp=${res._1} and payload=$payload")
      val uri = Uri.parse(payload)
      require(uri.scheme == "https") { "invalid lnurl scheme=${uri.scheme}, should be https" }
      return uri
    }
  }
}

