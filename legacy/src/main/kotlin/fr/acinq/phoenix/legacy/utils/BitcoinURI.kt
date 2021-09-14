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

package fr.acinq.phoenix.legacy.utils

import fr.acinq.bitcoin.Btc
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.payment.PaymentRequest
import org.slf4j.LoggerFactory
import scala.math.BigDecimal
import java.net.URI
import java.net.URLDecoder

fun URI.getParams(): Map<String, String> {
  return query?.split("&")
    ?.mapNotNull {
      it.split("=")
        .takeIf { keyValuePair ->
          keyValuePair.size == 2 && keyValuePair.none { p -> p.isBlank() } // 2 elements and none are empty
        }
    }
    ?.associateBy({ URLDecoder.decode(it[0], Charsets.UTF_8.name()) }, { URLDecoder.decode(it[1], Charsets.UTF_8.name()) })
    ?: emptyMap()
}

class BitcoinURI(val raw: String) {
  val log = LoggerFactory.getLogger(this::class.java)

  val address: String
  val message: String?
  val label: String?
  val lightning: PaymentRequest?
  val amount: Satoshi?

  init {
    val uri = tryWith(BitcoinURIParseException("cannot parse $raw")) {
      URI(if (raw.startsWith("bitcoin://", ignoreCase = true)) raw.removeRange(8, 10) else raw)
        .run {
          if (scheme != null && !scheme.equals("bitcoin", ignoreCase = true)) {
            throw BitcoinURIParseException("invalid scheme=${scheme}")
          }
          URI(rawSchemeSpecificPart)
        }
    }
    this.address = try {
      `package$`.`MODULE$`.addressToPublicKeyScript(uri.path, Wallet.getChainHash())
      uri.path
    } catch (e: Exception) {
      throw BitcoinURIParseException("invalid address", e)
    }

    // parse query into list. If a key is duplicated, last value in query wins.
    val params = uri.getParams()
    log.debug("params=$params retrieved from query=${uri.query}")

    // -- read label/message field parameter
    this.label = params["label"]
    this.message = params["message"]

    // -- read and deserializes lightning payment request field
    this.lightning = params["lightning"]?.takeIf { it.isNotBlank() }?.run {
      try {
        PaymentRequest.read(this)
      } catch (e: Exception) {
        null
      }
    }

    // -- read and validate amount field parameter. Amount is in BTC in the URI, and is converted to Satoshi,
    this.amount = params["amount"]?.toBigDecimalOrNull()?.takeIf { it > java.math.BigDecimal.ZERO }?.run {
      try {
        Btc.apply(BigDecimal.javaBigDecimal2bigDecimal(this)).toSatoshi()
      } catch (e: Exception) {
        null
      }
    }

    // -- check for required parameters (none are handled by this wallet ATM)
    // see https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki
    if (params.any { it.key.startsWith("req-") }) {
      throw BitcoinURIParseException("unhandled required params=$params")
    }
  }

  override fun toString(): String {
    return "${javaClass.simpleName} [ address=$address, amount=$amount, label=$label, message=$message, lightning=$lightning ]"
  }

}
