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

package fr.acinq.phoenix.android.components.inputs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.LocalExchangeRatesMap
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toFiat
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.converters.ComplexAmount
import fr.acinq.phoenix.android.utils.converters.FiatAmount
import fr.acinq.phoenix.android.utils.extensions.label
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency


@Composable
fun UnitDropdown(
    amount: MilliSatoshi?,
    unit: CurrencyUnit,
    units: List<CurrencyUnit>,
    canAddMoreUnits: Boolean,
    onUnitChange: (ComplexAmount?, CurrencyUnit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    internalPadding: PaddingValues = PaddingValues(8.dp),
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    var selectedUnit by remember { mutableStateOf(unit) }
    var showAddCurrencyDialog by remember { mutableStateOf(false) }
    val exchangeRates = LocalExchangeRatesMap.current

    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        Button(
            text = selectedUnit.displayCode,
            icon = if (enabled) R.drawable.ic_chevron_down else null,
            onClick = { expanded = true },
            padding = internalPadding,
            space = if (enabled) 8.dp else 0.dp,
            enabled = enabled,
            enabledEffect = false,
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onDismiss()
            },
            properties = PopupProperties(focusable = false),
        ) {
            Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                units.forEachIndexed { index, unit ->
                    DropdownMenuItem(
                        onClick = {
                            selectedUnit = unit
                            val newAmount = when (unit) {
                                is FiatCurrency -> {
                                    val rate = exchangeRates[unit]
                                    if (rate != null && amount != null) {
                                        ComplexAmount(amount, FiatAmount(amount.toFiat(rate.price), unit, rate))
                                    } else {
                                        null
                                    }
                                }
                                is BitcoinUnit -> amount?.let { ComplexAmount(it, null) }
                                else -> null
                            }

                            expanded = false
                            onDismiss()

                            onUnitChange(newAmount, units[index])
                        }
                    ) {
                        when (unit) {
                            is FiatCurrency -> Text(text = unit.label().first, style = MaterialTheme.typography.body1)
                            is BitcoinUnit -> TextWithIcon(unit.label(), icon = R.drawable.ic_bitcoin, textStyle = MaterialTheme.typography.body1)
                        }
                    }
                }
            }

            if (canAddMoreUnits) {
                Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                    BorderButton(
                        text = stringResource(R.string.unitdropdown_more),
                        icon = R.drawable.ic_settings,
                        onClick = { showAddCurrencyDialog = true ; expanded = false },
                        modifier = Modifier.fillMaxSize(),
                        borderColor = borderColor,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }

        if (showAddCurrencyDialog) {
            CurrencyConverter(
                initialAmount = amount,
                initialUnit = selectedUnit,
                onDone = { newAmount, newUnit ->
                    selectedUnit = newUnit
                    onUnitChange(newAmount, newUnit)
                    expanded = false
                    onDismiss()
                    showAddCurrencyDialog = false
                }
            )
        }
    }
}
