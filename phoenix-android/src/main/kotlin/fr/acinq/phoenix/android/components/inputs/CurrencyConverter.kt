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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalExchangeRatesMap
import fr.acinq.phoenix.android.LocalFiatCurrencies
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.buttons.TransparentFilledButton
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.dialogs.PopupDialog
import fr.acinq.phoenix.android.components.prefs.CurrenciesPickerDialog
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toFiat
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toUnit
import fr.acinq.phoenix.android.utils.converters.AmountConversionResult
import fr.acinq.phoenix.android.utils.converters.AmountConverter
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.FIAT_FORMAT_WRITABLE
import fr.acinq.phoenix.android.utils.converters.ComplexAmount
import fr.acinq.phoenix.android.utils.converters.DateFormatter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.datastore.PreferredBitcoinUnits
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.managers.AppConfigurationManager
import kotlinx.coroutines.launch
import java.text.DecimalFormat


@Composable
fun CurrencyConverter(
    initialAmount: MilliSatoshi?,
    initialUnit: CurrencyUnit,
    onDone: (ComplexAmount?, CurrencyUnit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val userPrefs = userPrefs
    val appConfigManager = business.appConfigurationManager

    val primaryBtcUnit = LocalBitcoinUnits.current.primary
    val otherBtcUnits = LocalBitcoinUnits.current.others
    val primaryFiat = LocalFiatCurrencies.current.primary
    val otherFiats = LocalFiatCurrencies.current.others
    val primaryRate = primaryFiat.let { LocalExchangeRatesMap.current[it] }
    val otherRates = LocalFiatCurrencies.current.others.associateWith { fiat -> LocalExchangeRatesMap.current[fiat] }

    var activeUnit by remember { mutableStateOf(initialUnit) }
    var amount by remember { mutableStateOf(initialAmount?.let { ComplexAmount(it, null) }) }

    ModalBottomSheet(
        onDismiss = { onDone(amount, activeUnit) },
        skipPartiallyExpanded = true,
        dragHandle = null,
        containerColor = MaterialTheme.colors.background,
        internalPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var showNewCurrenciesList by remember { mutableStateOf(false) }

        Spacer(Modifier.height(24.dp))
        Row {
            TransparentFilledButton(icon = R.drawable.ic_reset, onClick = { amount = null }, padding = PaddingValues(10.dp, 8.dp), modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Text(text = stringResource(R.string.converter_title), style = MaterialTheme.typography.h4, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            TransparentFilledButton(
                text = stringResource(R.string.converter_done_button),
                icon = R.drawable.ic_check,
                iconTint = MaterialTheme.colors.primary,
                onClick = { onDone(amount, activeUnit) },
                space = 8.dp,
                padding = PaddingValues(10.dp, 8.dp),
                modifier = Modifier.alignByBaseline()
            )
        }
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // primary btc
            BtcConverterInput(amount = amount?.amount, unit = primaryBtcUnit, onAmountUpdate = { amount = it; activeUnit = primaryBtcUnit }, onDelete = null)
            // other btc
            otherBtcUnits.forEach { unit ->
                BtcConverterInput(amount = amount?.amount, unit = unit, onAmountUpdate = { amount = it; activeUnit = unit }, onDelete = {
                    scope.launch {
                        val newBtcUnits = PreferredBitcoinUnits(primary = primaryBtcUnit, others = otherBtcUnits - unit)
                        userPrefs.saveBitcoinUnits(newBtcUnits)
                    }
                })
            }
            HSeparator(width = 150.dp)
            // primary fiat
            FiatConverterInput(amount = amount?.amount, fiat = primaryFiat, rate = primaryRate, onAmountUpdate = { amount = it ; activeUnit = primaryFiat }, onDelete = null)
            // other fiat
            otherRates.forEach { (fiat, rate) ->
                FiatConverterInput(amount = amount?.amount, fiat = fiat, rate = rate, onAmountUpdate = { amount = it ; activeUnit = fiat }, onDelete = {
                    scope.launch {
                        val newFiatCurrenciesConf = AppConfigurationManager.PreferredFiatCurrencies(primary = primaryFiat, others = otherFiats - fiat)
                        appConfigManager.updatePreferredFiatCurrencies(current = newFiatCurrenciesConf)
                        userPrefs.saveFiatCurrencyList(newFiatCurrenciesConf)
                    }
                })
            }
            TransparentFilledButton(text = stringResource(R.string.converter_add_new_button), icon = R.drawable.ic_plus, onClick = { showNewCurrenciesList = true }, iconTint = MaterialTheme.colors.primary)
            Spacer(Modifier.height(48.dp))
        }

        if (showNewCurrenciesList) {
            CurrenciesPickerDialog(
                currencies = BitcoinUnit.values - primaryBtcUnit - otherBtcUnits.toSet() + FiatCurrency.values - primaryFiat - otherFiats,
                onDismiss = { showNewCurrenciesList = false },
                onSelected = { currency ->
                    scope.launch {
                        when (currency) {
                            is FiatCurrency -> {
                                val newFiatPref = AppConfigurationManager.PreferredFiatCurrencies(primary = primaryFiat, others = otherFiats + currency)
                                appConfigManager.updatePreferredFiatCurrencies(current = newFiatPref)
                                userPrefs.saveFiatCurrencyList(newFiatPref)
                            }
                            is BitcoinUnit -> {
                                val newBtcPref = PreferredBitcoinUnits(primary = primaryBtcUnit, others = otherBtcUnits + currency)
                                userPrefs.saveBitcoinUnits(newBtcPref)
                            }
                        }
                    }
                    showNewCurrenciesList = false
                }
            )
        }
    }
}

@Composable
private fun BtcConverterInput(
    amount: MilliSatoshi?,
    unit: BitcoinUnit,
    onAmountUpdate: (ComplexAmount?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ConverterInput(
            value = amount?.toUnit(unit)?.let {
                DecimalFormat(
                    when (unit) {
                        BitcoinUnit.Sat -> "0.###"
                        BitcoinUnit.Bit -> "0.#####"
                        BitcoinUnit.MBtc -> "0.########"
                        BitcoinUnit.Btc -> "0.###########"
                    }
                ).format(it)
            },
            onValueChange = { btcAmount ->
                when (val res = AmountConverter.convertToComplexAmount(btcAmount, unit = unit, rate = null)) {
                    is AmountConversionResult.Error -> Unit
                    is ComplexAmount -> onAmountUpdate(res)
                    null -> onAmountUpdate(null)
                }
            },
            modifier = Modifier.weight(1f),
            backgroundColor = MaterialTheme.colors.surface,
            placeholder = { Text(text = stringResource(R.string.converter_placeholder_label, unit.displayCode), style = MaterialTheme.typography.caption) },
            leadingContent = { Image(painter = painterResource(id = R.drawable.ic_bitcoin), contentDescription = unit.name, modifier = Modifier.size(20.dp)) },
            trailingContent = { Text(text = unit.displayCode) },
        )
        onDelete?.let {
            Spacer(Modifier.width(8.dp))
            TransparentFilledButton(icon = R.drawable.ic_remove, onClick = it, padding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(IntrinsicSize.Min))
        }
    }
}

@Composable
private fun FiatConverterInput(
    amount: MilliSatoshi?,
    fiat: FiatCurrency,
    rate: ExchangeRate.BitcoinPriceRate?,
    onAmountUpdate: (ComplexAmount?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (rate == null) {
            ConverterInput(
                value = "",
                onValueChange = {},
                backgroundColor = MaterialTheme.colors.surface,
                readonly = true,
                placeholder = { Text(text = stringResource(R.string.utils_no_conversion), style = MaterialTheme.typography.caption) },
            )
        } else {
            ConverterInput(
                value = amount?.toFiat(rate.price)?.let { FIAT_FORMAT_WRITABLE.format(it) },
                onValueChange = { fiatAmount ->
                    when (val res = AmountConverter.convertToComplexAmount(fiatAmount, unit = fiat, rate = rate)) {
                        is AmountConversionResult.Error -> Unit
                        is ComplexAmount -> onAmountUpdate(res)
                        null -> onAmountUpdate(null)
                    }
                },
                limitDecimal = true,
                modifier = Modifier.weight(1f),
                backgroundColor = MaterialTheme.colors.surface,
                placeholder = { Text(text = stringResource(R.string.converter_placeholder_label, fiat.displayCode), style = MaterialTheme.typography.caption) },
                leadingContent = {
                    var showTimestamp by remember { mutableStateOf(false) }
                    if (showTimestamp) {
                        PopupDialog(onDismiss = { showTimestamp = false }, message = stringResource(R.string.converter_timestamp_refresh, rate.timestampMillis.toAbsoluteDateTimeString()))
                    }
                    Clickable(onClick = { showTimestamp = true }) {
                        Text(text = fiat.flag, modifier = Modifier.size(20.dp), textAlign = TextAlign.Right)
                    }
                },
                trailingContent = { Text(text = fiat.displayCode) }
            )
        }
        onDelete?.let {
            Spacer(Modifier.width(8.dp))
            TransparentFilledButton(icon = R.drawable.ic_remove, onClick = it, padding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(IntrinsicSize.Min))
        }
    }
}
