/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.send

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.utils.getValue
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.utils.negativeColor

val domains = listOf(
    "testnet.phoenixwallet.me",
    "bitrefill.me",
    "strike.me",
    "coincorner.io",
    "sparkwallet.me",
    "ln.tips",
    "getalby.com",
    "walletofsatoshi.com",
    "stacker.news",
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SendSmartInput(
    onValueChange: (String) -> Unit,
    onValueSubmit: () -> Unit,
    isProcessing: Boolean,
    isError: Boolean,
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange.Zero)) }

    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var showCompletionBox by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = showCompletionBox,
            onExpandedChange = { showCompletionBox = it },
            modifier = Modifier.weight(1f)
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val shape = RoundedCornerShape(24.dp)
            val colors = if (isError) {
                TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    focusedLabelColor = negativeColor,
                    unfocusedLabelColor = negativeColor.copy(alpha = .5f),
                    focusedBorderColor = negativeColor,
                    unfocusedBorderColor = negativeColor.copy(alpha = .5f),
                )
            } else {
                TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    focusedLabelColor = MaterialTheme.colors.primary,
                    unfocusedLabelColor = MaterialTheme.typography.body1.color,
                    focusedBorderColor = MaterialTheme.colors.primary,
                    unfocusedBorderColor = MaterialTheme.colors.primary,
                )
            }

            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onValueChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.backgroundColor(true).value, shape),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send,
                ),
                textStyle = MaterialTheme.typography.body1.copy(fontSize = 18.sp),
                minLines = 1,
                maxLines = 3,
                singleLine = false,
                enabled = true,
                visualTransformation = VisualTransformation.None,
                decorationBox = @Composable { innerTextField ->
                    TextFieldDefaults.OutlinedTextFieldDecorationBox(
                        value = textFieldValue.text,
                        visualTransformation = VisualTransformation.None,
                        innerTextField = innerTextField,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        placeholder = {
                            Text(
                                text = stringResource(id = R.string.preparesend_manual_input_hint),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        },
                        trailingIcon = {
                            if (textFieldValue.text.isNotEmpty()) {
                                FilledButton(
                                    onClick = {
                                        textFieldValue = TextFieldValue("")
                                        onValueChange("")
                                    },
                                    icon = R.drawable.ic_cross,
                                    enabled = true,
                                    enabledEffect = false,
                                    backgroundColor = Color.Transparent,
                                    iconTint = MaterialTheme.colors.onSurface,
                                    padding = PaddingValues(12.dp),
                                )
                            }
                        },
                        singleLine = false,
                        enabled = true,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        border = {
                            TextFieldDefaults.BorderBox(enabled = true, isError = isError, interactionSource = interactionSource, colors = colors, shape = shape)
                        }
                    )
                }
            )

            data class DomainFilterResult(val affix: String, val domainFilter: String, val domainsMatching: List<String>) {
                val isPerfectMatch: Boolean by lazy { domainsMatching.size == 1 && domainsMatching.first() == domainFilter }
            }

            val filterResult = remember(textFieldValue.text) {
                textFieldValue.text.takeIf { it.length >= 3 }?.let { input ->
                    val index = input.lastIndexOf("@")
                    if (index == -1) null else input.substring(0, index) to input.substring(index + 1, input.length)
                }?.let { (affix, domain) ->
                    DomainFilterResult(affix, domain, domains.filter { it.contains(domain, ignoreCase = true) } )
                }
            }

            if (filterResult != null && filterResult.domainsMatching.isNotEmpty()) {
                if (filterResult.isPerfectMatch) {
                    showCompletionBox = false
                } else {
                    showCompletionBox = true
                    Box(modifier = Modifier.padding(top = 48.dp, start = 16.dp, end = 0.dp)) {
                        ExposedDropdownMenu(
                            expanded = showCompletionBox,
                            onDismissRequest = { showCompletionBox = false },
                            modifier = Modifier.exposedDropdownSize(false)
                        ) {
                            filterResult.domainsMatching.forEach { option ->
                                Clickable(
                                    onClick = {
                                        val newInput = "${filterResult.affix}@$option"
                                        textFieldValue = TextFieldValue(text = newInput, selection = TextRange(newInput.length))
                                        onValueChange(newInput)
                                        showCompletionBox = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Text(text = "@", style = MaterialTheme.typography.caption, modifier = Modifier.alignByBaseline())
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = option, modifier = Modifier.alignByBaseline())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        if (isProcessing) {
            CircularProgressIndicator(Modifier.size(42.dp), strokeWidth = 2.dp)
        } else {
            Button(
                icon = R.drawable.ic_send,
                iconTint = MaterialTheme.colors.onPrimary,
                onClick = onValueSubmit,
                padding = PaddingValues(14.dp),
                shape = CircleShape,
                backgroundColor = if (isError) negativeColor else MaterialTheme.colors.primary,
            )
        }
    }
}
