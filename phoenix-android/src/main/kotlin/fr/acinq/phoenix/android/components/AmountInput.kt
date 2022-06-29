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

package fr.acinq.phoenix.android.components

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.utils.Converter.toFiat
import fr.acinq.phoenix.android.utils.Converter.toMilliSatoshi
import fr.acinq.phoenix.android.utils.Converter.toPlainString
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toUnit
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.outlinedTextFieldColors
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.CurrencyUnit
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import org.slf4j.LoggerFactory

data class ComplexAmount(
    val amount: MilliSatoshi,
    val fiatValue: Double?,
    val fiatCurrency: FiatCurrency?
)

object AmountInputHelper {
    val log = LoggerFactory.getLogger(this::class.java)

    /**
     * This methods converts a Double using a given [CurrencyUnit] (can be fiat or bitcoin) into a
     * [ComplexAmount] object, using the provided fiat rate for conversion. A string containing the
     * converted value (which can be in fiat or in bitcoin) is returned by the `onConverted` callback.
     *
     * Some validation rules are applied and if they fail, the `onError` callback returns the message
     * that should be displayed.
     */
    fun convertToComplexAmount(
        context: Context,
        input: String?,
        unit: CurrencyUnit,
        prefBitcoinUnit: BitcoinUnit,
        rate: ExchangeRate.BitcoinPriceRate?,
        onConverted: (String) -> Unit,
        onError: (String) -> Unit
    ): ComplexAmount? {
        log.debug("amount input update [ amount=$input unit=$unit with rate=$rate ]")
        onConverted("")
        onError("")
        if (input.isNullOrBlank()) {
            return null
        }
        val amount = input.toDoubleOrNull()
        if (amount == null) {
            onError(context.getString(R.string.validation_invalid_number))
            return null
        }
        return when (unit) {
            is FiatCurrency -> {
                if (rate == null) {
                    log.warn("cannot convert fiat amount to bitcoin with a null rate")
                    onConverted(context.getString(R.string.utils_no_conversion))
                    null
                } else {
                    val msat = amount.toMilliSatoshi(rate.price)
                    if (msat.toUnit(BitcoinUnit.Btc) > 21e6) {
                        onError(context.getString(R.string.send_error_amount_too_large))
                        null
                    } else if (msat < 0.msat) {
                        onError(context.getString(R.string.send_error_amount_negative))
                        null
                    } else {
                        onConverted(msat.toPrettyString(prefBitcoinUnit, withUnit = true))
                        ComplexAmount(msat, amount, unit)
                    }
                }
            }
            is BitcoinUnit -> {
                val msat = amount.toMilliSatoshi(unit)
                if (msat.toUnit(BitcoinUnit.Btc) > 21e6) {
                    onError(context.getString(R.string.send_error_amount_too_large))
                    null
                } else if (msat < 0.msat) {
                    onError(context.getString(R.string.send_error_amount_negative))
                    null
                } else if (rate == null) {
                    // conversion is not possible but that should not stop a payment from happening
                    onConverted(context.getString(R.string.utils_no_conversion))
                    ComplexAmount(amount = msat, fiatValue = null, fiatCurrency = null)
                } else {
                    val fiat = msat.toFiat(rate.price)
                    onConverted(fiat.toPrettyString(rate.fiatCurrency, withUnit = true))
                    ComplexAmount(amount = msat, fiatValue = fiat, fiatCurrency = rate.fiatCurrency)
                }
            }
            else -> {
                null
            }
        }
    }
}

@Composable
fun AmountInput(
    initialAmount: MilliSatoshi?,
    onAmountChange: (ComplexAmount?) -> Unit,
    modifier: Modifier = Modifier,
    convertedValueModifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {

    val context = LocalContext.current
    val prefBitcoinUnit = LocalBitcoinUnit.current
    val prefFiat = LocalFiatCurrency.current
    val rate = fiatRate
    val units = listOf<CurrencyUnit>(BitcoinUnit.Sat, BitcoinUnit.Bit, BitcoinUnit.MBtc, BitcoinUnit.Btc, prefFiat)
    val focusManager = LocalFocusManager.current

    var unit: CurrencyUnit by remember { mutableStateOf(prefBitcoinUnit) }
    var inputValue: String by remember { mutableStateOf(initialAmount?.toUnit(prefBitcoinUnit).toPlainString()) }
    var convertedValue: String by remember { mutableStateOf(initialAmount?.toPrettyString(prefFiat, rate, withUnit = true) ?: "") }
    var errorMessage: String by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = inputValue,
            onValueChange = { newValue ->
                inputValue = newValue
                AmountInputHelper.convertToComplexAmount(
                    context = context,
                    input = inputValue,
                    unit = unit,
                    prefBitcoinUnit = prefBitcoinUnit,
                    rate = rate,
                    onConverted = { convertedValue = it },
                    onError = { errorMessage = it }
                ).let { onAmountChange(it) }
            },
            label = label,
            placeholder = placeholder,
            trailingIcon = {
                UnitDropdown(
                    selectedUnit = unit,
                    units = units,
                    onUnitChange = { newValue ->
                        unit = newValue
                        AmountInputHelper.convertToComplexAmount(
                            context = context,
                            input = inputValue,
                            unit = unit,
                            prefBitcoinUnit = prefBitcoinUnit,
                            rate = rate,
                            onConverted = { convertedValue = it },
                            onError = { errorMessage = it }
                        ).let { onAmountChange(it) }
                    },
                    onDismiss = { },
                    internalPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
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
                autoCorrect = false,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = outlinedTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier.enableOrFade(enabled)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = errorMessage.ifBlank {
                convertedValue.takeIf { it.isNotBlank() }?.let { stringResource(id = R.string.utils_converted_amount, it) } ?: ""
            },
            maxLines = 1,
            style = if (errorMessage.isNotBlank()) MaterialTheme.typography.body1.copy(color = negativeColor(), fontSize = 14.sp) else MaterialTheme.typography.caption.copy(fontSize = 14.sp),
            modifier = convertedValueModifier.padding(horizontal = 8.dp)
        )
    }
}

/**
 * This input is designed to be in the center stage of a screen and use a customised basic input
 * instead of a standard, material-design input.
 */
@Composable
fun AmountHeroInput(
    initialAmount: MilliSatoshi?,
    onAmountChange: (ComplexAmount?) -> Unit,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    dropdownModifier: Modifier = Modifier,
    inputTextSize: TextUnit = 16.sp,
    unitTextSize: TextUnit = 16.sp,
) {
    val context = LocalContext.current
    val prefBitcoinUnit = LocalBitcoinUnit.current
    val prefFiat = LocalFiatCurrency.current
    val rate = fiatRate
    val units = listOf<CurrencyUnit>(BitcoinUnit.Sat, BitcoinUnit.Bit, BitcoinUnit.MBtc, BitcoinUnit.Btc, prefFiat)
    val focusManager = LocalFocusManager.current

    var unit: CurrencyUnit by remember { mutableStateOf(prefBitcoinUnit) }
    var inputValue: String by remember { mutableStateOf(initialAmount?.toUnit(prefBitcoinUnit).toPlainString()) }
    var convertedValue: String by remember { mutableStateOf(initialAmount?.toPrettyString(prefFiat, rate, withUnit = true) ?: "") }
    var errorMessage: String by remember { mutableStateOf("") }

    // references for constraint layout
    val amountRef = "amount_input"
    val unitRef = "unit_dropdown"
    val altAmountRef = "alt_amount"
    val amountLineRef = "amount_line"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ConstraintLayout(
            constraintSet = ConstraintSet {
                val amountInput = createRefFor(amountRef)
                val unitDropdown = createRefFor(unitRef)
                val altAmount = createRefFor(altAmountRef)

                createHorizontalChain(amountInput, unitDropdown, chainStyle = ChainStyle.Packed)
                constrain(amountInput) {
                    width = Dimension.preferredValue(150.dp)
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(unitDropdown.start)
                }
                constrain(unitDropdown) {
                    baseline.linkTo(amountInput.baseline)
                    start.linkTo(amountInput.end, margin = 2.dp)
                    end.linkTo(parent.end)
                }
                val amountLine = createRefFor(amountLineRef)
                constrain(amountLine) {
                    width = Dimension.fillToConstraints
                    top.linkTo(amountInput.bottom, margin = 2.dp)
                    start.linkTo(amountInput.start)
                    end.linkTo(unitDropdown.end)
                }
                constrain(altAmount) {
                    top.linkTo(amountInput.bottom, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
            },
            modifier = modifier.width(IntrinsicSize.Min)
        ) {
            BasicTextField(
                value = inputValue,
                onValueChange = { newValue ->
                    inputValue = newValue
                    AmountInputHelper.convertToComplexAmount(
                        context = context,
                        input = inputValue,
                        unit = unit,
                        prefBitcoinUnit = prefBitcoinUnit,
                        rate = rate,
                        onConverted = { convertedValue = it },
                        onError = { errorMessage = it }
                    ).let { onAmountChange(it) }
                },
                modifier = inputModifier
                    .layoutId(amountRef)
                    .defaultMinSize(minWidth = 32.dp)
                    .sizeIn(maxWidth = 180.dp),
                textStyle = MaterialTheme.typography.body1.copy(
                    fontSize = 32.sp,
                    color = MaterialTheme.colors.primary
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true,
            )

            UnitDropdown(
                selectedUnit = unit,
                units = units,
                onUnitChange = { newValue ->
                    unit = newValue
                    AmountInputHelper.convertToComplexAmount(
                        context = context,
                        input = inputValue,
                        unit = unit,
                        prefBitcoinUnit = prefBitcoinUnit,
                        rate = rate,
                        onConverted = { convertedValue = it },
                        onError = { errorMessage = it }
                    ).let { onAmountChange(it) }
                },
                onDismiss = { },
                modifier = dropdownModifier.layoutId(unitRef)
            )

            // -- dashed line
            AndroidView(modifier = Modifier
                .layoutId(amountLineRef), factory = {
                ImageView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setBackgroundResource(R.drawable.line_dots)
                }
            })
        }

        Text(
            text = errorMessage.ifBlank {
                convertedValue.takeIf { it.isNotBlank() }?.let { stringResource(id = R.string.utils_converted_amount, it) } ?: ""
            },
            maxLines = 1,
            style = if (errorMessage.isNotBlank()) MaterialTheme.typography.body1.copy(color = negativeColor(), fontSize = 14.sp) else MaterialTheme.typography.caption.copy(fontSize = 14.sp),
            modifier = Modifier
                .layoutId(altAmountRef)
                .align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun UnitDropdown(
    selectedUnit: CurrencyUnit,
    units: List<CurrencyUnit>,
    onUnitChange: (CurrencyUnit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    internalPadding: PaddingValues = PaddingValues(8.dp),
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(maxOf(units.lastIndexOf(selectedUnit), 0)) }
    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        Button(
            text = units[selectedIndex].toString(),
            icon = R.drawable.ic_chevron_down,
            onClick = { expanded = true },
            padding = internalPadding,
            space = 8.dp,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            units.forEachIndexed { index, s ->
                DropdownMenuItem(onClick = {
                    selectedIndex = index
                    expanded = false
                    onDismiss()
                    onUnitChange(units[index])
                }) {
                    Text(text = s.toString())
                }
            }
        }
    }
}
