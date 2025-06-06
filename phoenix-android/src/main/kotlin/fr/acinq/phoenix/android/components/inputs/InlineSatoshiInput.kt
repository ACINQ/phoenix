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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalFiatCurrencies
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.primaryFiatRate
import fr.acinq.phoenix.android.utils.converters.AmountConverter.toFiat
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.outlinedTextFieldColors

@Composable
fun InlineSatoshiInput(
    amount: Satoshi?,
    onAmountChange: (Satoshi?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    isError: Boolean,
) {
    val prefFiat = LocalFiatCurrencies.current.primary
    val rate = primaryFiatRate

    val focusManager = LocalFocusManager.current
    var internalText by remember { mutableStateOf(amount?.sat?.toString() ?: "") }
    var convertedValue: String by remember { mutableStateOf(amount?.toPrettyString(prefFiat, rate, withUnit = true) ?: "") }
    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colors.primary.copy(alpha = 0.7f),
        backgroundColor = Color.Transparent
    )

    Box(modifier = modifier.enableOrFade(enabled)) {
        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
            OutlinedTextField(
                value = internalText,
                onValueChange = {
                    internalText = it
                    val newAmount = it.toDoubleOrNull()?.toLong()?.sat
                    convertedValue = rate?.let { newAmount?.toMilliSatoshi()?.toFiat(rate.price)?.toPrettyString(prefFiat, withUnit = true) } ?: ""
                    onAmountChange(newAmount)
                },
                isError = isError,
                enabled = enabled,
                label = null,
                placeholder = placeholder,
                trailingIcon = trailingIcon,
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
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (convertedValue.isNotBlank()) {
                Text(
                    text = stringResource(id = R.string.utils_converted_amount, convertedValue),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = when {
                        isError -> MaterialTheme.typography.subtitle2.copy(color = negativeColor, fontSize = 13.sp)
                        else -> MaterialTheme.typography.subtitle2.copy(fontSize = 13.sp)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.surface)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}
