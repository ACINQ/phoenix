/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.utils.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import java.util.Currency

@Composable
fun BitcoinUnit.label(): String = when (this) {
    BitcoinUnit.Sat -> stringResource(id = R.string.prefs_display_coin_sat_label)
    BitcoinUnit.Bit -> stringResource(id = R.string.prefs_display_coin_bit_label)
    BitcoinUnit.MBtc -> stringResource(id = R.string.prefs_display_coin_mbtc_label)
    BitcoinUnit.Btc -> stringResource(id = R.string.prefs_display_coin_btc_label)
}

@Composable
fun FiatCurrency.labels(): Pair<String, String> {
    val context = LocalContext.current
    return remember(key1 = displayCode) {
        val fullName = when {
            // use the free market rates as default. Name for official rates gets a special tag, as those rates are usually inaccurate.
            this == FiatCurrency.ARS -> context.getString(R.string.currency_ars_official)
            this == FiatCurrency.ARS_BM -> context.getString(R.string.currency_ars_bm)
            this == FiatCurrency.CUP -> context.getString(R.string.currency_cup_official)
            this == FiatCurrency.CUP_FM -> context.getString(R.string.currency_cup_fm)
            this == FiatCurrency.LBP -> context.getString(R.string.currency_lbp_official)
            this == FiatCurrency.LBP_BM -> context.getString(R.string.currency_lbp_bm)
            // use the JVM API otherwise to get the name
            displayCode.length == 3 -> try {
                Currency.getInstance(displayCode).displayName
            } catch (e: Exception) {
                "N/A"
            }
            else -> "N/A"
        }
        "$flag $displayCode" to fullName
    }
}
