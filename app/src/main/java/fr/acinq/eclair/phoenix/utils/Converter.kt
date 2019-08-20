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
import fr.acinq.bitcoin.Btc
import fr.acinq.bitcoin.BtcAmount
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.`package$`
import fr.acinq.eclair.CoinUtils
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.`CoinUtils$`
import fr.acinq.eclair.phoenix.R
import org.slf4j.LoggerFactory
import scala.Option
import scala.math.`BigDecimal$`
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.regex.Pattern

object Converter {

  private val log = LoggerFactory.getLogger(Converter::class.java)

  private val DECIMAL_SEPARATOR = DecimalFormat().decimalFormatSymbols.decimalSeparator.toString()
  private var FIAT_FORMAT: NumberFormat = NumberFormat.getInstance()

  init {
    FIAT_FORMAT.minimumFractionDigits = 2
    FIAT_FORMAT.maximumFractionDigits = 2
  }

  fun printAmountRaw(amount: BtcAmount, context: Context): String = CoinUtils.rawAmountInUnit(amount, Prefs.getCoinUnit(context)).bigDecimal().toPlainString()
  fun printAmountRaw(amount: MilliSatoshi, context: Context): String = CoinUtils.rawAmountInUnit(amount, Prefs.getCoinUnit(context)).bigDecimal().toPlainString()

  fun printAmountPretty(amount: MilliSatoshi, context: Context, withUnit: Boolean = false, withSign: Boolean = false, isOutgoing: Boolean = true): Spanned {
    val unit = Prefs.getCoinUnit(context)
    val formatted = `CoinUtils$`.`MODULE$`.formatAmountInUnit(amount, unit, false)
    return formatAnyAmount(context, formatted, unit.code(), withUnit, withSign, isOutgoing)
  }

  fun printAmountPretty(amount: BtcAmount, context: Context, withUnit: Boolean = false, withSign: Boolean = false, isOutgoing: Boolean = true): Spanned {
    val unit = Prefs.getCoinUnit(context)
    val formatted = `CoinUtils$`.`MODULE$`.formatAmountInUnit(amount, unit, false)
    return formatAnyAmount(context, formatted, unit.code(), withUnit, withSign, isOutgoing)
  }

  fun printFiatRaw(context: Context, amount: MilliSatoshi): String {
    return FIAT_FORMAT.format(convertMsatToFiat(context, amount))
  }

  fun printFiatPretty(context: Context, amount: MilliSatoshi, withUnit: Boolean = false, withSign: Boolean = false, isOutgoing: Boolean = true): Spanned {
    val fiat = Prefs.getFiatCurrency(context)
    return formatAnyAmount(context, printFiatRaw(context, amount), fiat, withUnit, withSign, isOutgoing)
  }

  private fun formatAnyAmount(context: Context, amount: String, unit: String, withUnit: Boolean = false, withSign: Boolean = false, isOutgoing: Boolean = true): Spanned {
    val formattedParts = amount.split(Pattern.quote(DECIMAL_SEPARATOR).toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val prefix = if (withSign) context.getString(if (isOutgoing) R.string.paymentholder_sent_prefix else R.string.paymentholder_received_prefix) else ""

    return if (formattedParts.size == 2) {
      if (withUnit) {
        Html.fromHtml(context.getString(R.string.utils_pretty_amount_with_unit, prefix, formattedParts[0] + DECIMAL_SEPARATOR, formattedParts[1], unit), Html.FROM_HTML_MODE_COMPACT)
      } else {
        Html.fromHtml(context.getString(R.string.utils_pretty_amount, prefix, formattedParts[0] + DECIMAL_SEPARATOR, formattedParts[1]), Html.FROM_HTML_MODE_COMPACT)
      }
    } else {
      if (withUnit) {
        Html.fromHtml(context.getString(R.string.utils_pretty_amount_with_unit, prefix, amount, "", unit), Html.FROM_HTML_MODE_COMPACT)
      } else {
        Html.fromHtml(context.getString(R.string.utils_pretty_amount, prefix, amount, ""), Html.FROM_HTML_MODE_COMPACT)
      }
    }
  }

  /**
   * Converts bitcoin amount to the fiat currency preferred by the user.
   *
   * @param amountMsat amount in milli satoshis
   * @param fiatCode   fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  fun convertMsatToFiat(context: Context, amount: MilliSatoshi): BigDecimal {
    val fiat = Prefs.getFiatCurrency(context)
    val rate = Prefs.getExchangeRate(context, fiat)

    return `package$`.`MODULE$`.satoshi2btc(amount.truncateToSatoshi()).amount().`$times`(scala.math.BigDecimal.decimal(rate)).bigDecimal()
  }

  /**
   * Converts fiat amount to bitcoin amount in Msat.
   *
   * @param fiatAmount amount in fiat
   * @param fiatCode   fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  fun convertFiatToMsat(context: Context, amount: String): MilliSatoshi {
    val fiat = Prefs.getFiatCurrency(context)
    val rate = Prefs.getExchangeRate(context, fiat)
    return any2Msat(Btc(`BigDecimal$`.`MODULE$`.apply(amount).`$div`(scala.math.BigDecimal.decimal(rate))))
  }

  /**
   * Converts a string to an optional MilliSatoshi. If string is empty, or amount is 0, returns none. Input is assumed to be in the user's preferred coin unit.
   *
   */
  fun string2Msat_opt(input: String, context: Context): Option<MilliSatoshi> {
    return if (input.isBlank()) {
      Option.apply(null)
    } else {
      val amount: MilliSatoshi = CoinUtils.convertStringAmountToMsat(input, Prefs.getCoinUnit(context).code())
      Option.apply(if (amount.amount() == 0L) null else amount)
    }
  }

  /**
   * Converts a string to an optional MilliSatoshi. If string is empty, or amount is 0, returns none.
   * Unit should match CoinUnit.code()/label(), otherwise an exception is thrown.
   */
  fun string2Msat_opt(amount: String, unit: String): Option<MilliSatoshi> {
    return if (amount.isBlank()) {
      Option.apply(null)
    } else {
      val res: MilliSatoshi = CoinUtils.convertStringAmountToMsat(amount, unit)
      Option.apply(if (res.amount() == 0L) null else res)
    }
  }

  /**
   * If string cannot be converted (eg not numeric), returns none. Input is assumed to be in the user's preferred coin unit.
   */
  fun string2Msat_opt_safe(input: String, context: Context): Option<MilliSatoshi> {
    return try {
      string2Msat_opt(input, context)
    } catch (e: Exception) {
      log.error("could not convert amount to numeric/millisatoshi", e)
      Option.apply(null)
    }
  }

  fun any2Msat(amount: BtcAmount): MilliSatoshi {
    return MilliSatoshi.toMilliSatoshi(amount)
  }

  fun msat2sat(amount: MilliSatoshi): Satoshi = amount.truncateToSatoshi()
}
