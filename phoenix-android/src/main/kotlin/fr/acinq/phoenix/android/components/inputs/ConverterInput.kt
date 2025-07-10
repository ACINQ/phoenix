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

package fr.acinq.phoenix.android.components.inputs


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.outlinedTextFieldColors


@Composable
fun ConverterInput(
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    internalPaddingHorizontal: Dp = 10.dp,
    internalPaddingVertical: Dp = 10.dp,
    enabled: Boolean = true,
    readonly: Boolean = false,
    value: String?,
    onValueChange: (String?) -> Unit,
    backgroundColor: Color
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var internalStringValue by remember(value) { mutableStateOf(value ?: "") }
    var errorMessage by remember { mutableStateOf("") }

    val primaryColor = MaterialTheme.colors.primary
    var borderColor by remember { mutableStateOf(Color.Transparent) }

    Box(modifier = modifier) {
        BasicTextField(
            value = internalStringValue,
            onValueChange = {
                if (it.length > 12) return@BasicTextField
                errorMessage = ""
                if (it.isNotBlank() && it.toDoubleOrNull() == null) {
                    errorMessage = context.getString(R.string.validation_invalid_number)
                }
                internalStringValue = it
                onValueChange(it)
            },
            enabled = enabled,
            readOnly = readonly,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged {
                    if (it.isFocused) {
                        borderColor = primaryColor
                        onValueChange(internalStringValue)
                    } else {
                        borderColor = Color.Transparent
                    }
                },
            textStyle = MaterialTheme.typography.body1,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Decimal,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            singleLine = true,
            maxLines = 1,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        border = BorderStroke(2.dp, borderColor),
                        shape = RoundedCornerShape(16.dp),
                        color = backgroundColor,
                        modifier = Modifier.matchParentSize()
                    ) { }
                        Row(
                            modifier = Modifier.padding(horizontal = internalPaddingHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(internalPaddingHorizontal),
                        ) {
                            Box(modifier = Modifier.padding(vertical = internalPaddingVertical)) {
                                leadingContent?.invoke()
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                Box(modifier = Modifier.padding(vertical = internalPaddingVertical)) {
                                    innerTextField()
                                }
                                if (internalStringValue.isBlank() && placeholder != null) {
                                    Box(modifier = Modifier.padding(vertical = internalPaddingVertical)) {
                                        placeholder()
                                    }
                                }
                                if (enabled && errorMessage.isNotBlank()) {
                                    Text(
                                        text = errorMessage,
                                        style = MaterialTheme.typography.body1.copy(color = negativeColor, fontSize = 12.sp),
                                        modifier = Modifier.align(Alignment.BottomStart).offset(x = (-3).dp, y = 7.dp).background(backgroundColor).padding(horizontal = 3.dp)
                                    )
                                }
                            }
                            Box(modifier = Modifier.padding(vertical = internalPaddingVertical)) {
                                trailingContent?.invoke()
                            }
                        }
                    }

            },
        )
    }
}


@Composable
fun RowScope.InlineNumberInput(
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    value: Double?,
    onValueChange: (Double?) -> Unit,
    isError: Boolean,
    acceptDecimal: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    var internalText by remember { mutableStateOf(value?.let { if (acceptDecimal) it.toString() else it.toLong().toString() } ?: "") }

    OutlinedTextField(
        value = internalText,
        onValueChange = {
            internalText = it
            onValueChange(it.toDoubleOrNull())
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
        modifier = modifier
            .alignByBaseline()
            .enableOrFade(enabled)
    )
}