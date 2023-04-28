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

import android.content.Context
import android.os.Build
import android.text.Html
import android.text.Spanned
import fr.acinq.bitcoin.scala.Btc
import fr.acinq.bitcoin.scala.BtcAmount
import fr.acinq.bitcoin.scala.Satoshi
import fr.acinq.bitcoin.scala.`package$`
import fr.acinq.eclair.*
import fr.acinq.phoenix.legacy.R
import org.slf4j.LoggerFactory
import scala.Option
import scala.math.`BigDecimal$`
import scodec.bits.ByteVector
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
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
    FIAT_FORMAT.roundingMode = RoundingMode.CEILING // prevent converting very small bitcoin amounts to 0 in fiat
  }

  fun refreshCoinPattern(context: Context) {
    when (Prefs.getCoinUnit(context)) {
      `SatUnit$`.`MODULE$` -> CoinUtils.setCoinPattern("###,###,###,##0")
      `BitUnit$`.`MODULE$` -> CoinUtils.setCoinPattern("###,###,###,##0.##")
      `MBtcUnit$`.`MODULE$` -> CoinUtils.setCoinPattern("###,###,###,##0.#####")
      else -> CoinUtils.setCoinPattern("###,###,###,##0.###########")
    }
    CoinUtils.COIN_FORMAT().roundingMode = RoundingMode.DOWN
  }

  fun printAmountRawForceUnit(amount: BtcAmount, unit: CoinUnit): String = CoinUtils.rawAmountInUnit(amount, unit).bigDecimal().toPlainString()
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

  private fun printFiatRaw(context: Context, amount: MilliSatoshi): String {
    return convertMsatToFiat(context, amount)?.let { return FIAT_FORMAT.format(it) } ?: context.getString(R.string.legacy_utils_unknown_amount)
  }

  fun printFiatPretty(context: Context, amount: MilliSatoshi, withUnit: Boolean = false, withSign: Boolean = false, isOutgoing: Boolean = true): Spanned {
    val fiat = Prefs.getFiatCurrency(context)
    return formatAnyAmount(context, printFiatRaw(context, amount), fiat, withUnit, withSign, isOutgoing)
  }

  private fun formatAnyAmount(context: Context, amount: String, unit: String, withUnit: Boolean = false, withSign: Boolean = false, isOutgoing: Boolean = true): Spanned {
    val formattedParts = amount.split(Pattern.quote(DECIMAL_SEPARATOR).toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val prefix = if (withSign) context.getString(if (isOutgoing) R.string.legacy_paymentholder_sent_prefix else R.string.legacy_paymentholder_received_prefix) else ""

    val output = if (formattedParts.size == 2) {
      if (withUnit) {
        context.getString(R.string.legacy_utils_pretty_amount_with_unit, prefix, formattedParts[0] + DECIMAL_SEPARATOR, formattedParts[1], unit)
      } else {
        context.getString(R.string.legacy_utils_pretty_amount, prefix, formattedParts[0] + DECIMAL_SEPARATOR, formattedParts[1])
      }
    } else {
      if (withUnit) {
        context.getString(R.string.legacy_utils_pretty_amount_with_unit, prefix, amount, "", unit)
      } else {
        context.getString(R.string.legacy_utils_pretty_amount, prefix, amount, "")
      }
    }
    return html(output)
  }

  public fun html(source: String): Spanned {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT
        and Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING
        and Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
        and Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST
        and Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM)
    } else {
      Html.fromHtml(source)
    }
  }

  /**
   * Converts bitcoin amount to the fiat currency preferred by the user.
   *
   * @param amountMsat amount in milli satoshis
   * @param fiatCode   fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  private fun convertMsatToFiat(context: Context, amount: MilliSatoshi): BigDecimal? {
    val fiat = Prefs.getFiatCurrency(context)
    val rate = Prefs.getExchangeRate(context, fiat)
    return if (rate < 0) {
      null
    } else {
      `package$`.`MODULE$`.satoshi2btc(amount.truncateToSatoshi()).toBigDecimal().`$times`(scala.math.BigDecimal.decimal(rate)).bigDecimal()
    }
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
    return if (rate < 0) {
      MilliSatoshi(0)
    } else {
      any2Msat(Btc(`BigDecimal$`.`MODULE$`.apply(amount).`$div`(scala.math.BigDecimal.decimal(rate))))
    }
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
      Option.apply(if (amount.toLong() == 0L) null else amount)
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
      Option.apply(if (res.toLong() == 0L) null else res)
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

  fun toAscii(b: ByteVector): String? {
    val bytes: ByteArray = b.toArray()
    return String(bytes, StandardCharsets.US_ASCII)
  }

  /** Convert a raw stringified percentage to a fee per millionths (decimals after the 4th are ignored). For example, 0.01% becomes 100. */
  fun percentageToPerMillionths(percent: String): Long {
    return (DecimalFormat().parse(percent.trim())!!.toDouble() * 1000000 / 100)
      .toLong()
      .coerceAtLeast(0)
  }

  /** Convert a per millionths Long to a percentage String (max 4 decimals). */
  fun perMillionthsToPercentageString(perMillionths: Long): String {
    return DecimalFormat("0.00##").apply { roundingMode = RoundingMode.FLOOR }.run {
      format(perMillionths
        .coerceAtLeast(0)
        .toBigDecimal()
        .divide(BigDecimal.valueOf(1000000))
        .multiply(BigDecimal(100)))
    }
  }
}
