/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android.settings

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.UserTheme
import fr.acinq.phoenix.android.utils.label
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DisplayPrefsView() {
    val nc = navController
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SettingScreen {
        SettingHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.prefs_display_title))
        Card {
            BitcoinUnitPreference(context = context, scope = scope)
            FiatCurrencyPreference(context = context, scope = scope)
            UserThemePreference(context = context, scope = scope)
        }
    }
}

@Composable
private fun BitcoinUnitPreference(context: Context, scope: CoroutineScope) {
    var prefsEnabled by remember { mutableStateOf(true) }
    val preferences = listOf(
        PreferenceItem(item = BitcoinUnit.Sat, title = BitcoinUnit.Sat.label(), description = stringResource(id = R.string.prefs_display_coin_sat_desc)),
        PreferenceItem(item = BitcoinUnit.Bit, title = BitcoinUnit.Bit.label(), description = stringResource(id = R.string.prefs_display_coin_bit_desc)),
        PreferenceItem(item = BitcoinUnit.MBtc, title = BitcoinUnit.MBtc.label(), description = stringResource(id = R.string.prefs_display_coin_mbtc_desc)),
        PreferenceItem(item = BitcoinUnit.Btc, title = BitcoinUnit.Btc.label()),
    )
    val currentPref = LocalBitcoinUnit.current
    ListPreferenceButton(
        title = stringResource(id = R.string.prefs_display_coin_label),
        subtitle = currentPref.label(),
        enabled = prefsEnabled,
        selectedItem = currentPref,
        preferences = preferences,
        onPreferenceSubmit = {
            prefsEnabled = false
            scope.launch {
                UserPrefs.saveBitcoinUnit(context, it.item)
                prefsEnabled = true
            }
        }
    )
}

@Composable
private fun FiatCurrencyPreference(context: Context, scope: CoroutineScope) {
    var prefEnabled by remember { mutableStateOf(true) }

    val preferences = FiatCurrency.values.map {
        PreferenceItem(it, it.label())
    }

    val currentPref = LocalFiatCurrency.current
    ListPreferenceButton(
        title = stringResource(id = R.string.prefs_display_fiat_label),
        subtitle = currentPref.label(),
        enabled = prefEnabled,
        selectedItem = currentPref,
        preferences = preferences,
        onPreferenceSubmit = {
            prefEnabled = false
            scope.launch {
                UserPrefs.saveFiatCurrency(context, it.item)
                prefEnabled = true
            }
        }
    )
}

@Composable
private fun UserThemePreference(context: Context, scope: CoroutineScope) {
    var prefEnabled by remember { mutableStateOf(true) }
    val preferences = UserTheme.values().map {
        PreferenceItem(it, title = it.label())
    }
    val currentPref by UserPrefs.getUserTheme(context).collectAsState(initial = UserTheme.SYSTEM)
    ListPreferenceButton(
        title = stringResource(id = R.string.prefs_display_theme_label),
        subtitle = currentPref.label(),
        enabled = prefEnabled,
        selectedItem = currentPref,
        preferences = preferences,
        onPreferenceSubmit = {
            prefEnabled = false
            scope.launch {
                UserPrefs.saveUserTheme(context, it.item)
                prefEnabled = true
            }
        }
    )
}
