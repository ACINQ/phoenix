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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import java.util.*

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

inline fun <T1: Any, T2: Any, R: Any> safeLet(p1: T1?, p2: T2?, block: (T1, T2)->R?): R? {
    return if (p1 != null && p2 != null) block(p1, p2) else null
}

@Composable
fun BitcoinUnit.label(): String = when (this) {
    BitcoinUnit.Sat -> stringResource(id = R.string.prefs_display_coin_sat_label)
    BitcoinUnit.Bit -> stringResource(id = R.string.prefs_display_coin_bit_label)
    BitcoinUnit.MBtc -> stringResource(id = R.string.prefs_display_coin_mbtc_label)
    BitcoinUnit.Btc -> stringResource(id = R.string.prefs_display_coin_btc_label)
}

@Composable
fun FiatCurrency.label(): String = remember(key1 = this.name) {
    "(" + this.name + ") " + if (this.name.length == 3) Currency.getInstance(this.name).displayName else ""
}

@Composable
fun UserTheme.label(): String {
    val context = LocalContext.current
    return remember(key1 = this.name) {
        when (this) {
            UserTheme.DARK -> context.getString(R.string.prefs_display_theme_dark_label)
            UserTheme.LIGHT -> context.getString(R.string.prefs_display_theme_light_label)
            UserTheme.SYSTEM -> context.getString(R.string.prefs_display_theme_system_label)
        }
    }
}