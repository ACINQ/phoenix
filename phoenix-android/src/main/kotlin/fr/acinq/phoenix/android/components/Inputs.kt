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


import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import fr.acinq.phoenix.android.utils.Converter.toPlainString
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.PhoenixAndroidTheme
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.textFieldColors
import fr.acinq.phoenix.data.*

@Composable
fun TextInput(
    modifier: Modifier = Modifier,
    text: String,
    maxLines: Int = 1,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
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
        placeholder = placeholder,
        enabled = enabled,
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = textFieldColors(),
        shape = RectangleShape,
        modifier = modifier
    )
}

@Composable
fun NumberInput(
    modifier: Modifier = Modifier,
    text: String,
    maxLines: Int = 1,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
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
            keyboardType = KeyboardType.Number
        ),
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = textFieldColors(),
        shape = RectangleShape,
        modifier = modifier
    )
}

@Preview
@Composable
fun ComposablePreview() {
    PhoenixAndroidTheme() {
        Column {
            AmountInput(initialAmount = 123456.msat, onAmountChange = { _, _, _ -> Unit })
            Spacer(modifier = Modifier.height(24.dp))
            AmountInput(initialAmount = -123456.msat, onAmountChange = { _, _, _ -> Unit }, useBasicInput = true)
        }
    }
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
    val log = logger("AmountInput")

    // get unit ambients
    val context = LocalContext.current
    val prefBitcoinUnit = LocalBitcoinUnit.current
    val prefFiat = LocalFiatCurrency.current
    val rate = fiatRate
    val units = listOf<CurrencyUnit>(BitcoinUnit.Sat, BitcoinUnit.Bit, BitcoinUnit.MBtc, BitcoinUnit.Btc, prefFiat)
    val focusManager = LocalFocusManager.current

    // unit selected in the dropdown menu
    var unit: CurrencyUnit by remember { mutableStateOf(prefBitcoinUnit) }
    // stores the raw String input entered by the user.
    var rawAmount: String by remember {
        mutableStateOf(initialAmount?.toUnit(prefBitcoinUnit).toPlainString())
    }
    var convertedAmount: String by remember {
        mutableStateOf(initialAmount?.toFiat(rate?.price ?: -1.0).toPlainString())
    }
    // stores the numeric value of rawAmount as a Double. Null if rawAmount is invalid or empty.
    var amount: Double? by remember {
        mutableStateOf(initialAmount?.toUnit(prefBitcoinUnit))
    }

    log.debug { "amount input initial [ amount=${amount.toPlainString()} unit=$unit with rate=$rate ]" }

    /** Convert the input [Double] to a (msat -> fiat) pair, if possible. */
    fun convertInputToAmount(): Pair<MilliSatoshi?, Double?> {
        log.debug { "amount input update [ amount=$amount unit=$unit with rate=$rate ]" }
        return amount?.let { d ->
            when (val u = unit) {
                is FiatCurrency -> {
                    if (rate == null) {
                        log.warning { "cannot convert fiat amount to bitcoin with a null rate" }
                        convertedAmount = context.getString(R.string.utils_no_conversion)
                        null to null
                    } else {
                        val msat = d.toMilliSatoshi(rate.price)

                        if (msat.toUnit(BitcoinUnit.Btc) > 21e6) {
                            convertedAmount = context.getString(R.string.send_amount_error_too_large)
                            null to null
                        } else if (msat < 0.msat) {
                            convertedAmount = context.getString(R.string.send_amount_error_negative)
                            null to null
                        } else {
                            convertedAmount = msat.toPrettyString(prefBitcoinUnit, withUnit = true)
                            msat to amount
                        }
                    }
                }
                is BitcoinUnit -> d.toMilliSatoshi(u).run {
                    if (this.toUnit(BitcoinUnit.Btc) > 21e6) {
                        convertedAmount = context.getString(R.string.send_amount_error_too_large)
                        null to null
                    } else if (this < 0.msat) {
                        convertedAmount = context.getString(R.string.send_amount_error_negative)
                        null to null
                    } else if (rate == null) {
                        convertedAmount = context.getString(R.string.utils_no_conversion)
                        this to null // conversion is not possible but that does not stop the payment
                    } else {
                        val fiat = toFiat(rate.price)
                        convertedAmount = fiat.toPrettyString(prefFiat, withUnit = true)
                        this to fiat
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

    /**
     * When input changes, refresh the mutable amount field and convert it to a actionable (msat -> fiat) pair.
     */
    val onValueChange: (String) -> Unit = {
        val d = it.toDoubleOrNull()
        if (d == null) {
            rawAmount = ""
            amount = null
        } else {
            rawAmount = it
            amount = d
        }
        convertInputToAmount().let { (msat, fiat) -> onAmountChange(msat, fiat, prefFiat) }
    }

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
                if (useBasicInput) {
                    val amountLine = createRefFor(amountLineRef)
                    constrain(amountLine) {
                        width = Dimension.fillToConstraints
                        top.linkTo(amountInput.bottom, margin = 2.dp)
                        start.linkTo(amountInput.start)
                        end.linkTo(unitDropdown.end)
                    }
                }
                constrain(altAmount) {
                    if (useBasicInput) {
                        top.linkTo(amountInput.bottom, margin = 8.dp)
                    } else {
                        top.linkTo(amountInput.bottom, margin = 2.dp)
                    }
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
            },
            modifier = modifier
                .width(IntrinsicSize.Min)
                .then(if (!useBasicInput) Modifier.fillMaxWidth() else Modifier)
        ) {
            if (useBasicInput) {
                BasicTextField(
                    value = rawAmount,
                    onValueChange = onValueChange,
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
            } else {
                TextField(
                    value = rawAmount,
                    onValueChange = onValueChange,
                    modifier = inputModifier
                        .layoutId(amountRef)
                        .fillMaxWidth(),
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
                    shape = RectangleShape,
                )
            }

            UnitDropdown(
                selectedUnit = unit,
                units = units,
                onUnitChange = {
                    unit = it
                    convertInputToAmount().let { (msat, fiat) -> onAmountChange(msat, fiat, prefFiat) }
                },
                onDismiss = { },
                modifier = dropdownModifier.layoutId(unitRef)
            )

            // -- dashed line
            if (useBasicInput) {
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
        }

        Text(
            text = convertedAmount.takeIf { it.isNotBlank() }?.let { stringResource(id = R.string.utils_converted_amount, it) } ?: "",
            style = if (useBasicInput) MaterialTheme.typography.caption.copy(textAlign = TextAlign.Center) else MaterialTheme.typography.caption,
            modifier = Modifier
                .layoutId(altAmountRef)
                .fillMaxWidth()
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