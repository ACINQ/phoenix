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
import com.google.common.base.Strings
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.BtcUnit
import fr.acinq.eclair.CoinUtils
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.payment.PaymentRequest

class BitcoinURI(input: String) {

  public val address: String
  public val message: String?
  public val label: String?
  public val lightning: PaymentRequest?
  public val amount: Satoshi?

  init {

    val uri = Uri.parse(stripScheme(input))

    // -- should have no scheme (bitcoin: and bitcoin:// are stripped)
    if (uri.scheme != null) {
      throw BitcoinURIParseException("scheme is not valid")
    }

    // -- read and validate address
    val path = uri.path
    if (Strings.isNullOrEmpty(path)) {
      throw BitcoinURIParseException("address is missing")
    }
    try {
      `package$`.`MODULE$`.addressToPublicKeyScript(path, Wallet.getChainHash())
    } catch (e: IllegalArgumentException) {
      throw BitcoinURIParseException(e.localizedMessage, e)
    }

    this.address = path

    // -- read label/message field parameter
    this.label = uri.getQueryParameter("label")
    this.message = uri.getQueryParameter("message")

    // -- read and validate lightning payment request field
    val lightningParam = uri.getQueryParameter("lightning")
    if (Strings.isNullOrEmpty(lightningParam)) {
      this.lightning = null
    } else {
      this.lightning = PaymentRequest.read(lightningParam, true)
    }

    // -- read and validate amount field parameter. Amount is in BTC in the URI, and is converted to Satoshi,
    val amountParam = uri.getQueryParameter("amount")
    if (Strings.isNullOrEmpty(amountParam)) {
      this.amount = null
    } else {
      this.amount = CoinUtils.convertStringAmountToSat(amountParam, BtcUnit.code())
    }

    // -- check required (yet unused) parameters
    // see https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki
    for (p in uri.queryParameterNames) {
      if (p.startsWith("req-")) {
        throw BitcoinURIParseException("unhandled required param: $p")
      }
    }
  }

  private fun stripScheme(uri: String): String {
    for (prefix in BITCOIN_PREFIXES) {
      if (uri.toLowerCase().startsWith(prefix)) {
        return uri.substring(prefix.length)
      }
    }
    return uri
  }

  override fun toString(): String {
    return javaClass.simpleName + "[ " +
      "address: " + this.address + ", " +
      "amount: " + this.amount + ", " +
      "label: " + this.label + ", " +
      "message: " + this.message + ", " +
      "lightning: " + this.lightning + " ]"
  }

  companion object {
    private val BITCOIN_PREFIXES = listOf("bitcoin://", "bitcoin:")
  }
}
