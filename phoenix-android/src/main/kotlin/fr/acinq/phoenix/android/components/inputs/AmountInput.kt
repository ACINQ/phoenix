/*
 * Copyright 2022 ACINQ SAS
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

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalExchangeRatesMap
import fr.acinq.phoenix.android.LocalFiatCurrencies
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toFiat
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toUnit
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPlainString
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.converters.AmountConversionResult
import fr.acinq.phoenix.android.utils.converters.AmountConverter
import fr.acinq.phoenix.android.utils.converters.ComplexAmount
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.outlinedTextFieldColors
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency

@Composable
fun AmountInput(
    amount: MilliSatoshi?,
    onAmountChange: (ComplexAmount?) -> Unit,
    modifier: Modifier = Modifier,
    staticLabel: String?,
    placeholder: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    forceUnit: CurrencyUnit? = null,
) {

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colors.primary.copy(alpha = 0.7f),
        backgroundColor = Color.Transparent
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val prefBitcoinUnits = LocalBitcoinUnits.current
    val prefFiatCurrencies = LocalFiatCurrencies.current

    val units = remember(prefBitcoinUnits, prefFiatCurrencies) { forceUnit?.let { listOf(it) } ?: (prefBitcoinUnits.all + prefFiatCurrencies.primary + prefFiatCurrencies.others) }
    var unit: CurrencyUnit by remember { mutableStateOf(prefBitcoinUnits.primary) }

    val rates = LocalExchangeRatesMap.current
    var inputValue: String by remember { mutableStateOf(amount?.toUnit(prefBitcoinUnits.primary).toPlainString()) }
    var inputAmount by remember { mutableStateOf(amount) }
    val convertedValue: String by remember(inputAmount, unit) {
        val s = when (unit) {
            is FiatCurrency -> {
                val inBtc = inputAmount?.toPrettyString(prefBitcoinUnits.primary, withUnit = true)
                val fiat = prefFiatCurrencies.primary
                val inPrimary = if (unit != fiat) {
                    rates[fiat]?.price?.let { rate ->
                        inputAmount?.toFiat(rate)?.toPrettyString(unit = fiat, withUnit = true)
                    }
                } else null

                inBtc?.let {
                    inPrimary?.let { "$inBtc / $inPrimary" } ?: inBtc
                }
            }
            is BitcoinUnit -> {
                val fiat = prefFiatCurrencies.primary
                rates[fiat]?.price?.let { rate ->
                    inputAmount?.toFiat(rate)?.toPrettyString(unit = fiat, withUnit = true)
                }
            }
            else -> null
        }
        mutableStateOf(s ?: "")
    }
    var errorMessage: String by remember { mutableStateOf("") }

    fun updateFieldsForInputChange() {
        errorMessage = ""
        val res = AmountConverter.convertToComplexAmount(
            input = inputValue,
            unit = unit,
            rate = rates[unit],
        )
        when (res) {
            is AmountConversionResult.Error -> {
                inputAmount = null
                errorMessage = when (res) {
                    is AmountConversionResult.Error.InvalidInput -> context.getString(R.string.validation_invalid_number)
                    is AmountConversionResult.Error.RateUnavailable -> context.getString(R.string.utils_no_conversion)
                    is AmountConversionResult.Error.AmountTooLarge -> context.getString(R.string.send_error_amount_too_large)
                    is AmountConversionResult.Error.AmountNegative -> context.getString(R.string.send_error_amount_negative)
                }
            }
            null -> {
                inputAmount = null
                onAmountChange(null)
            }
            is ComplexAmount -> {
                inputAmount = res.amount
                onAmountChange(res)
            }
        }
    }

    Box(modifier = modifier.enableOrFade(enabled)) {
        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
            OutlinedTextField(
                value = inputValue,
                onValueChange = { newValue ->
                    inputValue = newValue
                    updateFieldsForInputChange()
                },
                label = null,
                placeholder = placeholder,
                trailingIcon = {
                    UnitDropdown(
                        amount = inputAmount,
                        unit = unit,
                        units = units,
                        onUnitChange = { newAmount, newUnit ->
                            if (newAmount != null) {
                                when (newUnit) {
                                    is FiatCurrency -> {
                                        val fiat = newAmount.fiat
                                        if (fiat != null && fiat.currency == newUnit) {
                                            inputValue = fiat.value.toPlainString(limitDecimal = true)
                                        }
                                    }
                                    is BitcoinUnit -> {
                                        inputValue = newAmount.amount.toUnit(newUnit).toPlainString()
                                    }
                                }
                            }
                            unit = newUnit
                            updateFieldsForInputChange()
                        },
                        onDismiss = { },
                        canAddMoreUnits = forceUnit == null,
                        internalPadding = PaddingValues(start = 8.dp, top = 16.dp, bottom = 16.dp, end = 12.dp),
                        modifier = Modifier.height(IntrinsicSize.Min)
                    )
                },
                isError = errorMessage.isNotEmpty(),
                enabled = enabled,
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = outlinedTextFieldColors(),
                interactionSource = interactionSource,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 8.dp, top = if (staticLabel != null) 12.dp else 0.dp)
            )
        }

        staticLabel?.let {
            Text(
                text = staticLabel,
                maxLines = 1,
                style = when {
                    errorMessage.isNotBlank() -> MaterialTheme.typography.body2.copy(color = negativeColor, fontSize = 14.sp)
                    isFocused -> MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.primary, fontSize = 14.sp)
                    else -> MaterialTheme.typography.body2.copy(fontSize = 14.sp)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colors.surface)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        val subMessage = errorMessage.ifBlank {
            convertedValue.takeIf { it.isNotBlank() }?.let { stringResource(id = R.string.utils_converted_amount, it) } ?: ""
        }

        if (subMessage.isNotBlank()) {
            Text(
                text = subMessage,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = when {
                    errorMessage.isNotBlank() -> MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.onPrimary, fontSize = 13.sp)
                    else -> MaterialTheme.typography.subtitle2.copy(fontSize = 13.sp)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (errorMessage.isNotBlank()) negativeColor else MaterialTheme.colors.surface)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}
