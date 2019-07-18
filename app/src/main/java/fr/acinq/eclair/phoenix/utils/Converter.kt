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

import android.content.Context
import android.text.Html
import android.text.Spanned
import fr.acinq.bitcoin.BtcAmount
import fr.acinq.bitcoin.MilliSatoshi
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.CoinUtils
import fr.acinq.eclair.`CoinUtils$`
import fr.acinq.eclair.phoenix.R
import org.slf4j.LoggerFactory
import scala.Option
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.regex.Pattern

object Converter {

  private val log = LoggerFactory.getLogger(Converter::class.java)

  private val DECIMAL_SEPARATOR = DecimalFormat().decimalFormatSymbols.decimalSeparator.toString()

  fun rawAmount(amount: BtcAmount, context: Context): BigDecimal {
    return CoinUtils.rawAmountInUnit(amount, Prefs.getCoin(context)).bigDecimal()
  }

  fun rawAmountPrint(amount: BtcAmount, context: Context): String = rawAmount(amount, context).toPlainString()

  fun formatAmount(amount: BtcAmount, context: Context, withSign: Boolean = false, isOutgoing: Boolean = true): Spanned {
    val unit = Prefs.getCoin(context)
    val formatted = `CoinUtils$`.`MODULE$`.formatAmountInUnit(amount, unit, false)
    val formattedParts = formatted.split(Pattern.quote(DECIMAL_SEPARATOR).toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val prefix = if (withSign) context.getString(if (isOutgoing) R.string.paymentholder_sent_prefix else R.string.paymentholder_received_prefix) else ""

    return if (formattedParts.size == 2) {
      Html.fromHtml(context.getString(R.string.utils_pretty_amount, prefix, formattedParts[0] + DECIMAL_SEPARATOR, formattedParts[1]), Html.FROM_HTML_MODE_COMPACT)
    } else {
      Html.fromHtml(context.getString(R.string.utils_pretty_amount, prefix, formatted, ""), Html.FROM_HTML_MODE_COMPACT)
    }
  }

  fun string2Msat(input: String, context: Context): MilliSatoshi = CoinUtils.convertStringAmountToMsat(input, Prefs.getCoin(context).code())

  /**
   * Converts a string to an optional MilliSatoshi. If string is empty, or amount is 0, returns none.
   *
   */
  fun string2Msat_opt(input: String, context: Context): Option<MilliSatoshi> {
    return if (input.isBlank()) {
      Option.apply(null)
    } else {
      val amount: MilliSatoshi = string2Msat(input, context)
      Option.apply(if (amount.amount() == 0L) null else amount)
    }
  }

  /**
   * If string cannot be converted (eg not numeric), returns none.
   */
  fun string2Msat_opt_safe(input: String, context: Context): Option<MilliSatoshi> {
    return try {
      string2Msat_opt(input, context)
    } catch (e: Exception) {
      log.error("could not convert amount to numeric/millisatoshi", e)
      Option.apply(null)
    }
  }

  fun sat2msat(amount: Satoshi) = fr.acinq.bitcoin.`package$`.`MODULE$`.satoshi2millisatoshi(amount)
}
