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

package fr.acinq.phoenix.android.components


import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.View.TEXT_ALIGNMENT_VIEW_END
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import androidx.core.widget.doOnTextChanged
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.Converter.toFiat
import fr.acinq.phoenix.android.utils.Converter.toPlainString
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.*

@Composable
fun InputText(
    modifier: Modifier = Modifier,
    text: String,
    maxLines: Int = 1,
    label: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onTextChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = text,
        onValueChange = onTextChange,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Text
        ),
        label = label,
        enabled = enabled,
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = textFieldColors(),
        modifier = modifier
    )
}

@Composable
fun AmountInput(
    initialAmount: MilliSatoshi?,
    onAmountChange: (MilliSatoshi?, Double?, FiatCurrency) -> Unit,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier,
    dropdownModifier: Modifier = Modifier,
    useBasicInput: Boolean = false,
    inputTextSize: TextUnit = 16.sp,
    unitTextSize: TextUnit = 16.sp,
) {
    val log = logger()

    // get unit ambients
    val prefBitcoinUnit = LocalBitcoinUnit.current
    val prefFiat = LocalFiatCurrency.current
    val rate = localRate
    val units = if (rate != null) {
        listOf<CurrencyUnit>(
            BitcoinUnit.Sat,
            BitcoinUnit.Bit,
            BitcoinUnit.MBtc,
            BitcoinUnit.Btc,
            rate.fiatCurrency
        )
    } else {
        listOf<CurrencyUnit>(BitcoinUnit.Sat, BitcoinUnit.Bit, BitcoinUnit.MBtc, BitcoinUnit.Btc)
    }
    val focusManager = LocalFocusManager.current

    // references for constraint layout
    val amountRef = "amount_input"
    val unitRef = "unit_dropdown"
    val altAmountRef = "alt_amount"
    val amountLineRef = "amount_line"

    // unit selected in the dropdown menu
    var unit: CurrencyUnit by remember { mutableStateOf(prefBitcoinUnit) }
    // stores the raw String input entered by the user.
    var rawAmount: String by remember {
        mutableStateOf(
            initialAmount?.toUnit(prefBitcoinUnit).toPlainString()
        )
    }
    var convertedAmount: String by remember {
        mutableStateOf(
            initialAmount?.toFiat(
                rate?.price ?: -1.0
            ).toPlainString()
        )
    }
    // stores the numeric value of rawAmount, as a Double. Null if rawAmount is invalid or empty.
    var amount: Double? by remember { mutableStateOf(initialAmount?.toUnit(prefBitcoinUnit)) }

    log.info { "amount input initial [ amount=${amount.toPlainString()} unit=$unit with rate=$rate ]" }
    fun convertInputToAmount(): Pair<MilliSatoshi?, Double?> {
        log.info { "amount input update [ amount=$amount unit=$unit with rate=$rate ]" }
        return amount?.let { d ->
            when (val u = unit) {
                is FiatCurrency -> {
                    if (rate == null) {
                        log.warning { "cannot convert fiat amount to bitcoin with a null rate" }
                        convertedAmount = "No price available"
                        Pair(null, null)
                    } else {
                        val msat = d.toMilliSatoshi(rate.price)
                        convertedAmount = msat.toPrettyString(prefBitcoinUnit, withUnit = true)
                        Pair(msat, amount)
                    }
                }
                is BitcoinUnit -> d.toMilliSatoshi(u).run {
                    if (rate == null) {
                        convertedAmount = "No price available"
                        Pair(this, null) // conversion is not possible but that does not matter.
                    } else {
                        val fiat = toFiat(rate.price)
                        convertedAmount = fiat.toPrettyString(prefFiat, withUnit = true)
                        Pair(this, fiat)
                    }
                }
                else -> {
                    convertedAmount = ""
                    Pair(null, null)
                }
            }
        } ?: run {
            convertedAmount = ""
            Pair(null, null)
        }
    }

    ConstraintLayout(constraintSet = ConstraintSet {
        val amountInput = createRefFor(amountRef)
        val unitDropdown = createRefFor(unitRef)
        val altAmount = createRefFor(altAmountRef)
        constrain(amountInput) {
            if (!useBasicInput) {
                width = Dimension.fillToConstraints
                end.linkTo(unitDropdown.start)
            }
            top.linkTo(parent.top)
            start.linkTo(parent.start)
        }
        constrain(unitDropdown) {
            if (useBasicInput) {
                bottom.linkTo(amountInput.bottom)
                top.linkTo(amountInput.top)
            } else {
                baseline.linkTo(amountInput.baseline)
            }
            start.linkTo(amountInput.end)
            end.linkTo(parent.end)
        }
        if (useBasicInput) {
            val amountLine = createRefFor(amountLineRef)
            constrain(amountLine) {
                width = Dimension.fillToConstraints
                bottom.linkTo(amountInput.bottom)
                start.linkTo(amountInput.start)
                end.linkTo(unitDropdown.end)
            }
        }
        constrain(altAmount) {
            top.linkTo(amountInput.bottom)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }
    }, modifier = modifier) {
        val onValueChange: (String) -> Unit = {
            val d = it.toDoubleOrNull()
            if (d == null) {
                rawAmount = ""
                amount = null
            } else {
                rawAmount = it
                amount = d
            }
            convertInputToAmount().let { conv ->
                onAmountChange(conv.first, conv.second, prefFiat)
            }
        }
        if (useBasicInput) {
            AndroidView(factory = { ctx ->
                EditText(ctx).apply {
                    setText(rawAmount)
                    doOnTextChanged { text, start, before, count ->
                        onValueChange(text.toString())
                    }
                    background = null
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    textAlignment = TEXT_ALIGNMENT_VIEW_END
                    textSize = inputTextSize.value
                    setTextColor(getColor(ctx, R.attr.colorPrimary))
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    minWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80.0f, ctx.resources.displayMetrics).toInt()
                    maxLines = 1
                }
            }, modifier = Modifier.layoutId(amountRef))
        } else {
            TextField(
                value = rawAmount,
                onValueChange = onValueChange,
                modifier = inputModifier.layoutId(amountRef),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = textFieldColors(),
                singleLine = true,
                maxLines = 1,
            )
        }

        UnitDropdown(
            selectedUnit = unit,
            units = units,
            onUnitChange = {
                unit = it
                convertInputToAmount().let { p -> onAmountChange(p.first, p.second, prefFiat) }
            },
            onDismiss = { },
            modifier = dropdownModifier.layoutId(unitRef)
        )

        Text(
            text = convertedAmount.takeIf { it.isNotBlank() }
                ?.let { stringResource(id = R.string.utils_converted_amount, it) } ?: "",
            style = MaterialTheme.typography.caption,
            modifier = Modifier
                .layoutId(altAmountRef)
                .padding(top = 4.dp)
                .then(
                    if (useBasicInput) {
                        Modifier
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
        )

        if (useBasicInput) {
            AndroidView(modifier = Modifier
                .height(2.dp)
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
    }
}

@Composable
private fun UnitDropdown(
    selectedUnit: CurrencyUnit,
    units: List<CurrencyUnit>,
    onUnitChange: (CurrencyUnit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(maxOf(units.lastIndexOf(selectedUnit), 0)) }
    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        Button(
            text = units[selectedIndex].toString(),
            icon = R.drawable.ic_chevron_down,
            onClick = { expanded = true },
            padding = PaddingValues(8.dp),
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