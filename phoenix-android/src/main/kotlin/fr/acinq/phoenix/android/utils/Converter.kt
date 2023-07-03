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

package fr.acinq.phoenix.android.utils


import android.text.Html
import android.text.Spanned
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.data.*
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class MSatDisplayPolicy {
    HIDE, SHOW, SHOW_IF_ZERO_SATS
}

object Converter {

    private val log = LoggerFactory.getLogger(Converter::class.java)

    private val DECIMAL_SEPARATOR = DecimalFormat().decimalFormatSymbols.decimalSeparator.toString()
    private var SAT_FORMAT_WITH_MILLIS: NumberFormat = DecimalFormat("###,###,###,##0.###")
    private var SAT_FORMAT: NumberFormat = DecimalFormat("###,###,###,##0").apply { roundingMode = RoundingMode.DOWN }
    private var BIT_FORMAT_WITH_MILLIS: NumberFormat = DecimalFormat("###,###,###,##0.00###")
    private var BIT_FORMAT: NumberFormat = DecimalFormat("###,###,###,##0.00").apply { roundingMode = RoundingMode.DOWN }
    private var MBTC_FORMAT_WITH_MILLIS: NumberFormat = DecimalFormat("###,###,###,##0.00000###")
    private var MBTC_FORMAT: NumberFormat = DecimalFormat("###,###,###,##0.00000").apply { roundingMode = RoundingMode.DOWN }
    private var BTC_FORMAT_WITH_MILLIS: NumberFormat = DecimalFormat("###,###,###,##0.00000000###")
    private var BTC_FORMAT: NumberFormat = DecimalFormat("###,###,###,##0.00000000").apply { roundingMode = RoundingMode.DOWN }
    private var FIAT_FORMAT: NumberFormat = NumberFormat.getInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        roundingMode = RoundingMode.CEILING // prevent converting very small bitcoin amounts to 0 in fiat
    }

    private fun getCoinFormat(unit: BitcoinUnit, withMillis: Boolean) = when {
        unit == BitcoinUnit.Sat && withMillis-> SAT_FORMAT_WITH_MILLIS
        unit == BitcoinUnit.Sat -> SAT_FORMAT
        unit == BitcoinUnit.Bit && withMillis-> BIT_FORMAT_WITH_MILLIS
        unit == BitcoinUnit.Bit -> BIT_FORMAT
        unit == BitcoinUnit.MBtc && withMillis -> MBTC_FORMAT_WITH_MILLIS
        unit == BitcoinUnit.MBtc -> MBTC_FORMAT
        unit == BitcoinUnit.Btc && withMillis -> BTC_FORMAT_WITH_MILLIS
        else -> BTC_FORMAT
    }

    /** Converts a [Double] amount to [MilliSatoshi], assuming that this amount is in fiat. */
    fun Double.toMilliSatoshi(fiatRate: Double): MilliSatoshi = (this / fiatRate).toMilliSatoshi(BitcoinUnit.Btc)

    /** Converts a [Double] amount to [MilliSatoshi], assuming that this amount is in Bitcoin. */
    fun Double.toMilliSatoshi(unit: BitcoinUnit): MilliSatoshi = when (unit) {
        BitcoinUnit.Sat -> this.toBigDecimal().movePointRight(3).toLong().msat
        BitcoinUnit.Bit -> this.toBigDecimal().movePointRight(5).toLong().msat
        BitcoinUnit.MBtc -> this.toBigDecimal().movePointRight(8).toLong().msat
        BitcoinUnit.Btc -> this.toBigDecimal().movePointRight(11).toLong().msat
    }

    /** Converts [MilliSatoshi] to another Bitcoin unit. */
    fun MilliSatoshi.toUnit(unit: BitcoinUnit): Double = when (unit) {
        BitcoinUnit.Sat -> this.msat.toBigDecimal().movePointLeft(3).toDouble()
        BitcoinUnit.Bit -> this.msat.toBigDecimal().movePointLeft(5).toDouble()
        BitcoinUnit.MBtc -> this.msat.toBigDecimal().movePointLeft(8).toDouble()
        BitcoinUnit.Btc -> this.msat.toBigDecimal().movePointLeft(11).toDouble()
    }

    /** Format the double as a String using [DecimalFormat]. */
    fun Double?.toPlainString(): String = this?.takeIf { it > 0 }?.run {
        DecimalFormat("0.########").format(this)
    } ?: ""

    /** Converts [MilliSatoshi] to a fiat amount. */
    fun MilliSatoshi.toFiat(rate: Double): Double = this.toUnit(BitcoinUnit.Btc) * rate

    fun Double?.toPrettyString(unit: CurrencyUnit, withUnit: Boolean = false, mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.HIDE): String = (this?.let { amount ->
        when {
            unit == BitcoinUnit.Sat && amount < 1.0 && mSatDisplayPolicy == MSatDisplayPolicy.SHOW_IF_ZERO_SATS -> {
                SAT_FORMAT_WITH_MILLIS.format(amount)
            }
            unit is BitcoinUnit -> getCoinFormat(unit, withMillis = mSatDisplayPolicy == MSatDisplayPolicy.SHOW).format(amount)
            unit is FiatCurrency -> amount.takeIf { it >= 0 }?.let { FIAT_FORMAT.format(it) }
            else -> "?!"
        }
    } ?: "N/A").run {
        if (withUnit) "$this $unit" else this
    }

    fun MilliSatoshi.toPrettyStringWithFallback(unit: CurrencyUnit, rate: ExchangeRate.BitcoinPriceRate? = null, withUnit: Boolean = false, mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.HIDE): String {
        return if (rate == null) {
            toPrettyString(BitcoinUnit.Sat, null, withUnit, mSatDisplayPolicy)
        } else {
            toPrettyString(unit, rate, withUnit, mSatDisplayPolicy)
        }
    }

    fun MilliSatoshi.toPrettyString(unit: CurrencyUnit, rate: ExchangeRate.BitcoinPriceRate? = null, withUnit: Boolean = false, mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.HIDE): String = when {
        unit is BitcoinUnit -> this.toUnit(unit).toPrettyString(unit, withUnit, mSatDisplayPolicy)
        unit is FiatCurrency && rate != null -> this.toFiat(rate.price).toPrettyString(unit, withUnit)
        else -> "?!"
    }

    fun Satoshi.toPrettyString(unit: CurrencyUnit, rate: ExchangeRate.BitcoinPriceRate? = null, withUnit: Boolean = false, mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.HIDE): String {
        return this.toMilliSatoshi().toPrettyString(unit, rate, withUnit, mSatDisplayPolicy)
    }

    /** Converts this millis timestamp into a relative string date. */
    @Composable
    fun Long.toRelativeDateString(): String {
        val now = System.currentTimeMillis()
        val delay: Long = this - now
        return if (abs(delay) < 60 * 1000L) { // less than 1 minute ago
            stringResource(id = R.string.utils_date_just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(this, now, delay).toString()
        }
    }

    /** Converts this millis timestamp into a pretty, absolute string date time using the locale format. */
    fun Long.toAbsoluteDateTimeString(): String = DateFormat.getDateTimeInstance().format(Date(this))

    /** Converts this millis timestamp into a pretty, absolute string date using the locale format. */
    fun Long.toAbsoluteDateString(): String = DateFormat.getDateInstance().format(Date(this))

    /** Converts this millis timestamp into an year-month-day string. */
    fun Long.toBasicAbsoluteDateString(): String = SimpleDateFormat("yyyy-MM-dd").format(Date(this))

    @Composable
    public fun html(id: Int): Spanned {
        return html(stringResource(id = id))
    }

    public fun html(source: String): Spanned {
        return Html.fromHtml(
            source, Html.FROM_HTML_MODE_COMPACT
                    and Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING
                    and Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
                    and Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST
                    and Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM
        )
    }

    /** Convert a per millionths Long to a percentage Long. For example, 100 per millionths is 0.01%. */
    val TrampolineFees.proportionalFeeAsPercentage: Double
        get() = feeProportional.toBigDecimal().divide(BigDecimal.valueOf(10_000)).toDouble()

    /** Convert a per millionths Long to a percentage Long. For example, 100 per millionths is 0.01%. */
    val TrampolineFees.proportionalFeeAsPercentageString: String
        get() = DecimalFormat("0.####").format(this.proportionalFeeAsPercentage)

    /** Convert a percentage Long to a fee per millionths Long. For example, 0.01% becomes 100. */
    fun percentageToPerMillionths(percent: Double): Long {
        return (percent * 10_000L).toLong().coerceAtLeast(0L)
    }
}
