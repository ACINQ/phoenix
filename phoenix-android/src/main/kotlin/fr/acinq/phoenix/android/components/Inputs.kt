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


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.outlinedTextFieldColors

@Composable
fun TextInput(
    modifier: Modifier = Modifier,
    text: String,
    maxLines: Int = 1,
    maxChars: Int? = null,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onTextChange: (String) -> Unit,
) {
    val charsCount by remember(text) { mutableStateOf(text.length) }
    val focusManager = LocalFocusManager.current
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                if (maxChars == null || newValue.length <= maxChars) {
                    onTextChange(newValue)
                }
            },
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Text
            ),
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = outlinedTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier
        )
        if (maxChars != null) {
            Spacer(Modifier.height(4.dp))
            Text("$charsCount/$maxChars", style = MaterialTheme.typography.caption.copy(fontSize = 12.sp), modifier = Modifier.align(Alignment.End))
        }
    }
}


/**
 * @param onValueChange the value in this callback will be null if the input is not valid and an error is
 *      displayed in the component. If the value is not null, then the value can be assumed to be valid.
 * @param acceptDecimal if false, an error will be raised when the user enters a value with a decimal part
 *      and the input will be invalid.
 */
@Composable
fun NumberInput(
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    initialValue: Double?,
    onValueChange: (Double?) -> Unit,
    minValue: Double = 0.0,
    minErrorMessage: String? = null,
    maxValue: Double = Double.MAX_VALUE,
    maxErrorMessage: String? = null,
    acceptDecimal: Boolean = true,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var internalText by remember { mutableStateOf(initialValue?.let { if (acceptDecimal) it.toString() else it.toLong().toString() } ?: "") }
    var errorMessage by remember { mutableStateOf("") }

    fun processValueChange(newValue: String) {
        errorMessage = ""
        internalText = newValue
        if (newValue.isBlank()) {
            errorMessage = context.getString(R.string.validation_empty)
            onValueChange(null)
        } else {
            val doubleValue = newValue.toDoubleOrNull()
            when {
                doubleValue == null -> {
                    errorMessage = context.getString(R.string.validation_invalid_number)
                    onValueChange(null)
                }
                !acceptDecimal && doubleValue.rem(1) != 0.0 -> {
                    errorMessage = context.getString(R.string.validation_no_decimal)
                    onValueChange(null)
                }
                doubleValue < minValue -> {
                    errorMessage = minErrorMessage ?: context.getString(R.string.validation_below_min, minValue.toString())
                    onValueChange(null)
                }
                doubleValue > maxValue -> {
                    errorMessage = maxErrorMessage ?: context.getString(R.string.validation_above_max, maxValue.toString())
                    onValueChange(null)
                }
                else -> {
                    onValueChange(doubleValue)
                }
            }
        }
    }

    Column {
        OutlinedTextField(
            value = internalText,
            onValueChange = { processValueChange(it) },
            isError = errorMessage.isNotEmpty(),
            enabled = enabled,
            label = label,
            placeholder = placeholder,
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
            text = if (enabled) errorMessage else "",
            style = MaterialTheme.typography.body1.copy(color = negativeColor(), fontSize = 13.sp),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
