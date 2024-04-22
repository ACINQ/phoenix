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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.UserTheme
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import fr.acinq.phoenix.android.utils.label
import fr.acinq.phoenix.android.utils.labels
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.managers.AppConfigurationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DisplayPrefsView() {
    val nc = navController
    val userPrefs = userPrefs
    val scope = rememberCoroutineScope()
    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.prefs_display_title))
        Card {
            BitcoinUnitPreference(userPrefs = userPrefs, scope = scope)
            FiatCurrencyPreference(userPrefs = userPrefs, scope = scope)
            UserThemePreference(userPrefs = userPrefs, scope = scope)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AppLocaleSetting()
            }
        }
    }
}

@Composable
private fun BitcoinUnitPreference(userPrefs: UserPrefsRepository, scope: CoroutineScope) {
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
        subtitle = { TextWithIcon(text = currentPref.label(), icon = R.drawable.ic_bitcoin) },
        enabled = prefsEnabled,
        selectedItem = currentPref,
        preferences = preferences,
        onPreferenceSubmit = {
            prefsEnabled = false
            scope.launch {
                userPrefs.saveBitcoinUnit(it.item)
                prefsEnabled = true
            }
        }
    )
}

@Composable
private fun FiatCurrencyPreference(userPrefs: UserPrefsRepository, scope: CoroutineScope) {
    var prefEnabled by remember { mutableStateOf(true) }

    val preferences = FiatCurrency.values.map {
        val (title, desc) = it.labels()
        PreferenceItem(item = it, title = title, description = desc)
    }

    val appConfigurationManager = business.appConfigurationManager

    val currentPref = LocalFiatCurrency.current
    ListPreferenceButton(
        title = stringResource(id = R.string.prefs_display_fiat_label),
        subtitle = { Text(text = currentPref.labels().first) },
        enabled = prefEnabled,
        selectedItem = currentPref,
        preferences = preferences,
        onPreferenceSubmit = {
            prefEnabled = false
            scope.launch {
                userPrefs.saveFiatCurrency(it.item)
                appConfigurationManager.updatePreferredFiatCurrencies(AppConfigurationManager.PreferredFiatCurrencies(primary = it.item, others = emptySet()))
                prefEnabled = true
            }
        }
    )
}

@Composable
private fun UserThemePreference(userPrefs: UserPrefsRepository, scope: CoroutineScope) {
    var prefEnabled by remember { mutableStateOf(true) }
    val preferences = UserTheme.values().map {
        PreferenceItem(it, title = it.label())
    }
    val currentPref by userPrefs.getUserTheme.collectAsState(initial = UserTheme.SYSTEM)
    ListPreferenceButton(
        title = stringResource(id = R.string.prefs_display_theme_label),
        subtitle = { Text(text = currentPref.label()) },
        enabled = prefEnabled,
        selectedItem = currentPref,
        preferences = preferences,
        onPreferenceSubmit = {
            prefEnabled = false
            scope.launch {
                userPrefs.saveUserTheme(it.item)
                prefEnabled = true
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AppLocaleSetting() {
    val context = LocalContext.current
    SettingInteractive(
        title = stringResource(id = R.string.prefs_locale_label),
        description = Locale.getDefault().displayLanguage.replaceFirstChar { it.uppercase() }, // context.getSystemService(LocaleManager::class.java).applicationLocales.get(0).language,
        onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        }
    )
}