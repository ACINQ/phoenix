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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.enableOrFade
import fr.acinq.phoenix.android.utils.errorOutlinedTextFieldColors
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.outlinedTextFieldColors

@Composable
fun TextInput(
    modifier: Modifier = Modifier,
    text: String,
    textStyle: TextStyle = LocalTextStyle.current,
    minLines: Int = 1,
    maxLines: Int = 1,
    singleLine: Boolean = false,
    maxChars: Int? = null,
    staticLabel: String?,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (RowScope.() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    enabledEffect: Boolean = true,
    onTextChange: (String) -> Unit,
    textFieldColors: TextFieldColors = outlinedTextFieldColors(),
    showResetButton: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val charsCount by remember(text) { mutableStateOf(text.length) }
    val focusManager = LocalFocusManager.current

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val displayError = isError || !errorMessage.isNullOrBlank()

    Box(modifier = modifier.enableOrFade(enabled || !enabledEffect)) {
        OutlinedTextField(
            value = text,
            onValueChange = { newValue -> onTextChange(newValue.take(maxChars ?: Int.MAX_VALUE)) },
            textStyle = textStyle,
            minLines = minLines,
            maxLines = maxLines,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done,
                keyboardType = keyboardType
            ),
            label = null,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = if (!showResetButton && trailingIcon == null) {
                null
            } else {
                {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight()) {
                        if (text.isNotBlank()) {
                            FilledButton(
                                onClick = { onTextChange("") },
                                icon = R.drawable.ic_cross,
                                enabled = enabled,
                                enabledEffect = false,
                                backgroundColor = Color.Transparent,
                                iconTint = MaterialTheme.colors.onSurface,
                                padding = PaddingValues(12.dp),
                            )
                        }
                        trailingIcon?.let { it() }
                    }
                }
            },
            enabled = enabled,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = if (displayError) errorOutlinedTextFieldColors() else textFieldColors,
            shape = shape,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = if (staticLabel != null) 14.dp else 0.dp, bottom = 8.dp)
                .clip(shape)
        )

        staticLabel?.let {
            Text(
                text = staticLabel,
                maxLines = 1,
                style = when {
                    !errorMessage.isNullOrBlank() -> MaterialTheme.typography.body2.copy(color = negativeColor, fontSize = 14.sp)
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

        val subMessage = errorMessage?.takeIf { it.isNotBlank() } ?: if (maxChars != null && charsCount > 0) {
            "$charsCount/$maxChars"
        } else ""
        if (subMessage.isNotBlank()) {
            Text(
                text = subMessage,
                maxLines = 1,
                style = when {
                    !errorMessage.isNullOrBlank() -> MaterialTheme.typography.subtitle2.copy(color = MaterialTheme.colors.onPrimary, fontSize = 13.sp)
                    else -> MaterialTheme.typography.subtitle2.copy(fontSize = 13.sp)
                },
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!errorMessage.isNullOrBlank()) negativeColor else MaterialTheme.colors.surface)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}