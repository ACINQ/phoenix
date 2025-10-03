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
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalFiatCurrencies
import fr.acinq.phoenix.android.LocalUserPrefs
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.prefs.ListPreferenceButton
import fr.acinq.phoenix.android.components.prefs.PreferenceItem
import fr.acinq.phoenix.android.components.settings.Setting
import fr.acinq.phoenix.android.utils.UserTheme
import fr.acinq.phoenix.android.utils.datastore.PreferredBitcoinUnits
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.extensions.label
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.PreferredFiatCurrencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DisplayPrefsView(
    walletId: WalletId,
    business: PhoenixBusiness,
    onBackClick: () -> Unit,
) {
    val userPrefs = LocalUserPrefs.current
    val scope = rememberCoroutineScope()
    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.prefs_display_title))
        if (userPrefs != null) {
            Card {
                BitcoinUnitPreference(userPrefs = userPrefs, scope = scope)
                FiatCurrencyPreference(business = business, walletId = walletId, userPrefs = userPrefs, scope = scope)
                UserThemePreference(userPrefs = userPrefs, scope = scope)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    AppLocaleSetting()
                }
            }
        }
    }
}

@Composable
private fun BitcoinUnitPreference(userPrefs: UserPrefs, scope: CoroutineScope) {
    var prefsEnabled by remember { mutableStateOf(true) }
    val preferences = listOf(
        PreferenceItem(item = BitcoinUnit.Sat, title = "${BitcoinUnit.Sat.label()} (${BitcoinUnit.Sat.displayCode})", description = stringResource(id = R.string.prefs_display_coin_sat_desc)),
        PreferenceItem(item = BitcoinUnit.Bit, title = "${BitcoinUnit.Bit.label()} (${BitcoinUnit.Bit.displayCode})", description = stringResource(id = R.string.prefs_display_coin_bit_desc)),
        PreferenceItem(item = BitcoinUnit.MBtc, title = "${BitcoinUnit.MBtc.label()} (${BitcoinUnit.MBtc.displayCode})", description = stringResource(id = R.string.prefs_display_coin_mbtc_desc)),
        PreferenceItem(item = BitcoinUnit.Btc, title = "${BitcoinUnit.Btc.label()} (${BitcoinUnit.Btc.displayCode})"),
    )
    val currentPref = LocalBitcoinUnits.current.primary
    ListPreferenceButton(
        title = stringResource(id = R.string.prefs_display_coin_label),
        subtitle = { TextWithIcon(text = currentPref.label(), icon = R.drawable.ic_bitcoin) },
        enabled = prefsEnabled,
        selectedItem = currentPref,
        preferences = preferences,
        onPreferenceSubmit = {
            prefsEnabled = false
            scope.launch {
                val previousBitcoinUnits = userPrefs.getBitcoinUnits.first()
                val newBitcoinUnits = PreferredBitcoinUnits(primary = it.item, others = previousBitcoinUnits.others - it.item)
                userPrefs.saveBitcoinUnits(newBitcoinUnits)
                prefsEnabled = true
            }
        }
    )
}

@Composable
private fun FiatCurrencyPreference(business: PhoenixBusiness, walletId: WalletId, userPrefs: UserPrefs, scope: CoroutineScope) {
    var prefEnabled by remember { mutableStateOf(true) }

    val preferences = FiatCurrency.values.map {
        val (title, desc) = it.label()
        PreferenceItem(item = it, title = title, description = desc)
    }

    val appConfigManager = business.appConfigurationManager
    val currencyManager = business.phoenixGlobal.currencyManager

    val currentPref = LocalFiatCurrencies.current.primary
    ListPreferenceButton(
        title = stringResource(id = R.string.prefs_display_fiat_label),
        subtitle = { Text(text = currentPref.label().first) },
        enabled = prefEnabled,
        selectedItem = currentPref,
        preferences = preferences,
        onPreferenceSubmit = {
            prefEnabled = false
            scope.launch {
                val previousFiatCurrencies = userPrefs.getFiatCurrencies.first()
                val prefCurrencies = PreferredFiatCurrencies(primary = it.item, others = previousFiatCurrencies.others - it.item)
                userPrefs.saveFiatCurrencyList(prefCurrencies)
                appConfigManager.updatePreferredFiatCurrencies(prefCurrencies)
                currencyManager.startMonitoringCurrencies(walletId = walletId.nodeIdHash, currencies = prefCurrencies)
                prefEnabled = true
            }
        }
    )
}

@Composable
private fun UserThemePreference(userPrefs: UserPrefs, scope: CoroutineScope) {
    var prefEnabled by remember { mutableStateOf(true) }
    val preferences = UserTheme.entries.map {
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
    Setting(
        title = stringResource(id = R.string.prefs_locale_label),
        description = Locale.getDefault().displayLanguage.replaceFirstChar { it.uppercase() }, // context.getSystemService(LocaleManager::class.java).applicationLocales.get(0).language,
        onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        }
    )
}

@Composable
private fun UserTheme.label(): String {
    val context = LocalContext.current
    return remember(key1 = this.name) {
        when (this) {
            UserTheme.DARK -> context.getString(R.string.prefs_display_theme_dark_label)
            UserTheme.LIGHT -> context.getString(R.string.prefs_display_theme_light_label)
            UserTheme.SYSTEM -> context.getString(R.string.prefs_display_theme_system_label)
        }
    }
}