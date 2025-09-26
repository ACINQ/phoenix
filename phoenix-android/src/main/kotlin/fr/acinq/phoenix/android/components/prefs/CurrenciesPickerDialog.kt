/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.components.prefs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.utils.extensions.getLabel
import fr.acinq.phoenix.android.utils.mutedTextFieldColors
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency

@Composable
fun CurrenciesPickerDialog(
    currencies: List<CurrencyUnit>,
    onSelected: (CurrencyUnit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colors.background,
        isContentScrollable = false,
        internalPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(text = stringResource(R.string.currencypicker_title), style = MaterialTheme.typography.h4)
        Spacer(Modifier.height(16.dp))

        val currenciesMap = remember(currencies) {
            currencies.associateWith {
                when (it) {
                    is FiatCurrency -> it.getLabel(context).second
                    is BitcoinUnit -> it.getLabel(context)
                }
            }
        }

        var search by remember { mutableStateOf("") }
        val filteredCurrencies = remember(currenciesMap, search) {
            if (search.isBlank()) {
                currenciesMap
            } else {
                currenciesMap.filter { it.key.displayCode.contains(search, ignoreCase = true) || it.value.contains(search, ignoreCase = true) }
            }
        }

        TextInput(
            text = search,
            onTextChange = { search = it },
            staticLabel = null,
            leadingIcon = { PhoenixIcon(resourceId = R.drawable.ic_inspect, tint = MaterialTheme.typography.caption.color) },
            placeholder = { Text(text = stringResource(id = R.string.prefs_filter_name)) },
            singleLine = true,
            textFieldColors = mutedTextFieldColors(),
            shape = RoundedCornerShape(16.dp),
        )

        if (filteredCurrencies.isEmpty()) {
            Text(text = stringResource(R.string.currencypicker_no_match), style = MaterialTheme.typography.caption, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.background(color = MaterialTheme.colors.surface, shape = RoundedCornerShape(16.dp))) {
                items(filteredCurrencies.toList()) { (currency, label) ->
                    when (currency) {
                        is BitcoinUnit -> BtcCard(unit = currency, description = label, onClick = onSelected)
                        is FiatCurrency -> FiatCard(fiatCurrency = currency, description = label, onClick = onSelected)
                    }

                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BtcCard(
    unit: BitcoinUnit,
    description: String,
    onClick: (BitcoinUnit) -> Unit
) {
    Clickable(onClick = { onClick(unit) }) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            TextWithIcon(icon = R.drawable.ic_bitcoin, text = unit.displayCode)
            Spacer(Modifier.width(4.dp))
            Text(text = description, style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun FiatCard(
    fiatCurrency: FiatCurrency,
    description: String,
    onClick: (FiatCurrency) -> Unit
) {
    Clickable(onClick = { onClick(fiatCurrency) }) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = "${fiatCurrency.flag} ${fiatCurrency.displayCode}")
            Spacer(Modifier.width(4.dp))
            Text(text = description, style = MaterialTheme.typography.caption)
        }
    }
}