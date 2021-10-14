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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Prefs
import fr.acinq.phoenix.android.utils.label
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PreferencesView() {
    val nc = navController
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column {
        ScreenHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.prefs_display_title))
        ScreenBody(Modifier.padding(0.dp)) {
            BitcoinUnitPreference(context = context, scope = scope)
            FiatCurrencyPreference(context = context, scope = scope)
        }
    }
}

@Composable
private fun <T> ListPreferenceButton(
    title: String,
    subtitle: String,
    enabled: Boolean,
    selectedItem: T,
    preferences: List<PreferenceItem<T>>,
    onPreferenceSubmit: (PreferenceItem<T>) -> Unit,
) {
    var showPreferenceDialog by remember { mutableStateOf(false) }
    Clickable(onClick = {
        if (enabled) {
            showPreferenceDialog = true
        }
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 50.dp, top = 10.dp, bottom = 10.dp, end = 16.dp)
                .alpha(if (enabled) 1f else 0.5f)
        ) {
            Text(text = title)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = mutedTypo())
        }
    }

    if (showPreferenceDialog) {
        ListPreferenceDialog(
            initialPrefIndex = preferences.map { it.item }.indexOf(selectedItem).takeIf { it >= 0 },
            preferences = preferences,
            onSubmit = {
                showPreferenceDialog = false
                onPreferenceSubmit(it)
            },
            onCancel = { showPreferenceDialog = false }
        )
    }
}

private data class PreferenceItem<T>(val item: T, val title: String, val description: String? = null)

@Composable
private fun <T> ListPreferenceDialog(
    initialPrefIndex: Int?,
    preferences: List<PreferenceItem<T>>,
    onSubmit: (PreferenceItem<T>) -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismiss = onCancel,
        buttons = { Button(onClick = onCancel, text = stringResource(id = R.string.btn_cancel), padding = PaddingValues(16.dp)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            LazyColumn {
                itemsIndexed(preferences) { index, item ->
                    PreferenceDialogItem(
                        item = item,
                        selected = index == initialPrefIndex,
                        onClick = onSubmit
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> PreferenceDialogItem(
    item: PreferenceItem<T>,
    selected: Boolean,
    onClick: (PreferenceItem<T>) -> Unit,
) {
    Clickable(onClick = { onClick(item) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            RadioButton(selected = selected, onClick = { })
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = item.title)
                item.description?.let {
                    Text(text = it, style = mutedTypo())
                }
            }
        }
    }
}

// -------- actual preferences below -------- //

@Composable
private fun BitcoinUnitPreference(context: Context, scope: CoroutineScope) {
    var prefsEnabled by remember { mutableStateOf(true) }
    val preferences = listOf(
        PreferenceItem(item = BitcoinUnit.Sat, title = BitcoinUnit.Sat.label(), description = stringResource(id = R.string.prefs_display_coin_sat_desc)),
        PreferenceItem(item = BitcoinUnit.Bit, title = BitcoinUnit.Bit.label(), description = stringResource(id = R.string.prefs_display_coin_sat_desc)),
        PreferenceItem(item = BitcoinUnit.MBtc, title = BitcoinUnit.MBtc.label(), description = stringResource(id = R.string.prefs_display_coin_sat_desc)),
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
                Prefs.saveBitcoinUnit(context, it.item)
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
                Prefs.saveFiatCurrency(context, it.item)
                prefEnabled = true
            }
        }
    )
}