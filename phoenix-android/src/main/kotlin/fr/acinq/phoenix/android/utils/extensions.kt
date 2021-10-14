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

package fr.acinq.phoenix.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency

/**
 * Utility method rebinding any exceptions thrown by a method into another exception, using the origin exception as the root cause.
 * Helps with pattern matching.
 */
inline fun <T> tryWith(exception: Exception, action: () -> T): T = try {
    action.invoke()
} catch (t: Exception) {
    exception.initCause(t)
    throw exception
}

@Composable
fun BitcoinUnit.label(): String = when (this) {
    BitcoinUnit.Sat -> stringResource(id = R.string.prefs_display_coin_sat_label)
    BitcoinUnit.Bit -> stringResource(id = R.string.prefs_display_coin_bit_label)
    BitcoinUnit.MBtc -> stringResource(id = R.string.prefs_display_coin_mbtc_label)
    BitcoinUnit.Btc -> stringResource(id = R.string.prefs_display_coin_btc_label)
}

@Composable
fun FiatCurrency.label(): String = when (this) {
    FiatCurrency.AUD -> stringResource(R.string.prefs_display_fiat_AUD)
    FiatCurrency.BRL -> stringResource(R.string.prefs_display_fiat_BRL)
    FiatCurrency.CAD -> stringResource(R.string.prefs_display_fiat_CAD)
    FiatCurrency.CHF -> stringResource(R.string.prefs_display_fiat_CHF)
    FiatCurrency.CLP -> stringResource(R.string.prefs_display_fiat_CLP)
    FiatCurrency.CNY -> stringResource(R.string.prefs_display_fiat_CNY)
    FiatCurrency.CZK -> stringResource(R.string.prefs_display_fiat_CZK)
    FiatCurrency.DKK -> stringResource(R.string.prefs_display_fiat_DKK)
    FiatCurrency.EUR -> stringResource(R.string.prefs_display_fiat_EUR)
    FiatCurrency.GBP -> stringResource(R.string.prefs_display_fiat_GBP)
    FiatCurrency.HKD -> stringResource(R.string.prefs_display_fiat_HKD)
    FiatCurrency.HRK -> stringResource(R.string.prefs_display_fiat_HRK)
    FiatCurrency.HUF -> stringResource(R.string.prefs_display_fiat_HUF)
    FiatCurrency.INR -> stringResource(R.string.prefs_display_fiat_INR)
    FiatCurrency.ISK -> stringResource(R.string.prefs_display_fiat_ISK)
    FiatCurrency.JPY -> stringResource(R.string.prefs_display_fiat_JPY)
    FiatCurrency.KRW -> stringResource(R.string.prefs_display_fiat_KRW)
    FiatCurrency.MXN -> stringResource(R.string.prefs_display_fiat_MXN)
    FiatCurrency.NZD -> stringResource(R.string.prefs_display_fiat_NZD)
    FiatCurrency.PLN -> stringResource(R.string.prefs_display_fiat_PLN)
    FiatCurrency.RON -> stringResource(R.string.prefs_display_fiat_RON)
    FiatCurrency.RUB -> stringResource(R.string.prefs_display_fiat_RUB)
    FiatCurrency.SEK -> stringResource(R.string.prefs_display_fiat_SEK)
    FiatCurrency.SGD -> stringResource(R.string.prefs_display_fiat_SGD)
    FiatCurrency.THB -> stringResource(R.string.prefs_display_fiat_THB)
    FiatCurrency.TWD -> stringResource(R.string.prefs_display_fiat_TWD)
    FiatCurrency.USD -> stringResource(R.string.prefs_display_fiat_USD)
}