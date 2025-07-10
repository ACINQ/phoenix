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

package fr.acinq.phoenix.android.utils.converters


import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toFiat
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toUnit
import fr.acinq.phoenix.data.*
import org.slf4j.LoggerFactory
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat

enum class MSatDisplayPolicy {
    HIDE, SHOW, SHOW_IF_ZERO_SATS
}

object AmountFormatter {

    private var SAT_FORMAT_WITH_MILLIS: NumberFormat = DecimalFormat("###,###,###,##0.###")
    private var SAT_FORMAT: NumberFormat = DecimalFormat("###,###,###,##0").apply { roundingMode = RoundingMode.DOWN }
    private var BIT_FORMAT_WITH_MILLIS: NumberFormat = DecimalFormat("###,###,###,##0.00###")
    private var BIT_FORMAT: NumberFormat = DecimalFormat("###,###,###,##0.00").apply { roundingMode = RoundingMode.DOWN }
    private var MBTC_FORMAT_WITH_MILLIS: NumberFormat = DecimalFormat("###,###,###,##0.00000###")
    private var MBTC_FORMAT: NumberFormat = DecimalFormat("###,###,###,##0.00000").apply { roundingMode = RoundingMode.DOWN }
    private var BTC_FORMAT_WITH_MILLIS: NumberFormat = DecimalFormat("###,###,###,##0.00000000###")
    private var BTC_FORMAT: NumberFormat = DecimalFormat("###,###,###,##0.00000000").apply { roundingMode = RoundingMode.DOWN }

    /** Fiat format has always 2 decimals, with rounding. */
    var FIAT_FORMAT: NumberFormat = NumberFormat.getInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        roundingMode = RoundingMode.CEILING // prevent converting very small bitcoin amounts to 0 in fiat
    }
    /** Fiat format but at most 2 decimals and without the thousand grouping. Useful when field is not read-only, where grouping would cause issues. */
    var FIAT_FORMAT_WRITABLE: NumberFormat = NumberFormat.getInstance().apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        roundingMode = RoundingMode.CEILING
        isGroupingUsed = false
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

    /** Format the [Double] as a String using [DecimalFormat]. */
    fun Double?.toPlainString(limitDecimal: Boolean = false): String = this?.takeIf { it > 0 }?.run {
        val df = if (limitDecimal) DecimalFormat("0.00") else DecimalFormat("0.########")
        df.format(this)
    } ?: ""

    fun Double?.toPrettyString(
        unit: CurrencyUnit,
        withUnit: Boolean = false,
        mSatDisplayPolicy: MSatDisplayPolicy = MSatDisplayPolicy.HIDE,
    ): String {
        val amount = this?.let {
            when {
                unit == BitcoinUnit.Sat && it < 1.0 && mSatDisplayPolicy == MSatDisplayPolicy.SHOW_IF_ZERO_SATS -> {
                    SAT_FORMAT_WITH_MILLIS.format(it)
                }
                unit is BitcoinUnit -> {
                    getCoinFormat(unit, withMillis = mSatDisplayPolicy == MSatDisplayPolicy.SHOW).format(it)
                }
                unit is FiatCurrency -> {
                    it.takeIf { it >= 0 }?.let { FIAT_FORMAT.format(it) }
                }
                else -> "?!"
            }
        } ?: "N/A"
        return if (withUnit) {
            "$amount ${unit.displayCode}"
        } else {
            amount
        }
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
}
