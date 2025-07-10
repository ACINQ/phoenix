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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.LocalExchangeRatesMap
import fr.acinq.phoenix.android.LocalFiatCurrencies
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.converters.AmountConversionResult
import fr.acinq.phoenix.android.utils.converters.AmountConverter
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toFiat
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toUnit
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPlainString
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.converters.ComplexAmount
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.FiatCurrency

private enum class SlotsEnum { Input, Unit, DashedLine }

/**
 * This input is designed to be in the center stage of a screen. It uses a customised basic input
 * instead of a standard, material-design input.
 */
@Composable
fun AmountHeroInput(
    initialAmount: MilliSatoshi?,
    onAmountChange: (ComplexAmount?) -> Unit,
    validationErrorMessage: String,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    dropdownModifier: Modifier = Modifier,
    inputTextSize: TextUnit = 16.sp,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val prefBitcoinUnits = LocalBitcoinUnits.current
    val prefFiatCurrencies = LocalFiatCurrencies.current

    val rates = LocalExchangeRatesMap.current
    var unit: CurrencyUnit by remember { mutableStateOf(prefBitcoinUnits.primary) }
    val units = remember(prefBitcoinUnits, prefFiatCurrencies) { prefBitcoinUnits.all + prefFiatCurrencies.primary + prefFiatCurrencies.others }

    var inputValue by remember(initialAmount) {
        mutableStateOf(TextFieldValue(
            when (val u = unit) {
                is FiatCurrency -> {
                    val rate = rates[unit]
                    if (rate != null) {
                        initialAmount?.toFiat(rate.price).toPlainString(limitDecimal = true)
                    } else "?!"
                }
                is BitcoinUnit -> {
                    initialAmount?.toUnit(u).toPlainString()
                }
                else -> "?!"
            }
        ))
    }
    var inputAmount by remember { mutableStateOf(initialAmount) }
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
                    inPrimary?.let {
                        "$inBtc / $inPrimary"
                    } ?: inBtc
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

    var internalErrorMessage: String by remember { mutableStateOf(validationErrorMessage) }
    val errorMessage = validationErrorMessage.ifBlank { internalErrorMessage.ifBlank { null } }

    fun updateFieldsForInputChange() {
        internalErrorMessage = ""
        val res = AmountConverter.convertToComplexAmount(
            input = inputValue.text,
            unit = unit,
            rate = rates[unit],
        )
        when (res) {
            is AmountConversionResult.Error -> {
                inputAmount = null
                internalErrorMessage = when (res) {
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

    val input: @Composable () -> Unit = {
        BasicTextField(
            value = inputValue,
            onValueChange = { newValue ->
                inputValue = newValue
                updateFieldsForInputChange()
            },
            modifier = inputModifier
                .clipScrollableContainer(Orientation.Horizontal)
                .defaultMinSize(minWidth = 32.dp) // for good ux
                .width(IntrinsicSize.Min), // make the textfield fits its content
            textStyle = MaterialTheme.typography.body1.copy(
                fontSize = inputTextSize,
                color = if (errorMessage == null) MaterialTheme.colors.primary else negativeColor,
                fontWeight = FontWeight.Light,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() }),
            singleLine = true,
            enabled = enabled,
            cursorBrush = SolidColor(MaterialTheme.colors.primary.copy(alpha = 0.7f))
        )
    }

    val unitDropdown: @Composable () -> Unit = {
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
                                inputValue = TextFieldValue(fiat.value.toPlainString(limitDecimal = true))
                            }
                        }
                        is BitcoinUnit -> {
                            inputValue = TextFieldValue(newAmount.amount.toUnit(newUnit).toPlainString())
                        }
                    }
                }
                unit = newUnit
                updateFieldsForInputChange()
            },
            canAddMoreUnits = true,
            onDismiss = { },
            modifier = dropdownModifier,
            enabled = enabled,
        )
    }

    val dashedLine: @Composable () -> Unit = {
        val dotColor = if (errorMessage == null) MaterialTheme.colors.primary else negativeColor
        Canvas(modifier = Modifier.fillMaxWidth()) {
            drawLine(
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 20f)),
                start = Offset(x = 0f, y = 0f),
                end = Offset(x = size.width, y = 0f),
                cap = StrokeCap.Round,
                color = dotColor,
                strokeWidth = 7f
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubcomposeLayout(modifier = Modifier) { constraints ->
            val unitSlotPlaceables: List<Placeable> = subcompose(SlotsEnum.Unit, unitDropdown).map { it.measure(constraints) }
            val unitWidth = unitSlotPlaceables.maxOf { it.width }
            val unitHeight = unitSlotPlaceables.maxOf { it.height }

            val inputSlotPlaceables: List<Placeable> = subcompose(SlotsEnum.Input, input).map {
                it.measure(constraints.copy(maxWidth = constraints.maxWidth - unitWidth))
            }
            val inputWidth = inputSlotPlaceables.maxOf { it.width }
            val inputHeight = inputSlotPlaceables.maxOf { it.height }

            // dashed line width is input's width + unit's width
            val layoutWidth = inputWidth + unitWidth
            val dashedLinePlaceables = subcompose(SlotsEnum.DashedLine, dashedLine).map {
                it.measure(constraints.copy(minWidth = layoutWidth, maxWidth = layoutWidth))
            }
            val dashedLineHeight = dashedLinePlaceables.maxOf { it.height }
            val layoutHeight = listOf(inputHeight, unitHeight).maxOrNull() ?: (0 + dashedLineHeight)

            val inputBaseline = inputSlotPlaceables.maxOfOrNull { it[FirstBaseline] } ?: 0
            val unitBaseline = unitSlotPlaceables.maxOfOrNull { it[FirstBaseline] } ?: 0

            layout(layoutWidth, layoutHeight) {
                var x = 0
                var y = 0
                inputSlotPlaceables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width
                    y = maxOf(y, it.height)
                }
                unitSlotPlaceables.forEach {
                    it.placeRelative(x, inputBaseline - unitBaseline)
                    x += it.width
                    y = maxOf(y, it.height)
                }
                dashedLinePlaceables.forEach {
                    it.placeRelative(0, y)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.body1.copy(color = negativeColor, fontSize = 14.sp, textAlign = TextAlign.Center),
                modifier = Modifier.sizeIn(maxWidth = 300.dp, minHeight = 28.dp)
            )
        } else {
            Text(
                text = convertedValue.takeIf { it.isNotBlank() }?.let { stringResource(id = R.string.utils_converted_amount, it) } ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
                modifier = Modifier.sizeIn(minHeight = 28.dp)
            )
        }
    }
}