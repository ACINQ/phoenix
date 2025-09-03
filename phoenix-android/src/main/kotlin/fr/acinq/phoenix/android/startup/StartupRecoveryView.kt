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

package fr.acinq.phoenix.android.startup

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.application
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.SuccessMessage
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.utils.errorOutlinedTextFieldColors
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.outlinedTextFieldColors


@Composable
fun StartupRecoveryView(
    onBackClick: () -> Unit,
    onRecoveryDone: () -> Unit,
) {
    val vm = viewModel<StartupRecoveryViewModel>(factory = StartupRecoveryViewModel.Factory(application = application))
    val state = vm.state.value

    var inputValue by remember { mutableStateOf("") }
    val inputWords: List<String> = remember(inputValue) { inputValue.split("\\s+".toRegex()) }
    val isSeedValid: Boolean? = remember(inputValue) {
        if (inputWords.size < 12) {
            null
        } else {
            try {
                MnemonicCode.validate(inputWords.joinToString(" "))
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    DefaultScreenLayout {
        DefaultScreenHeader(title = "Wallet recovery", onBackClick = onBackClick)

        Card(
            internalPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(id = R.string.startup_recovery_instructions))
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (inputValue.isNotBlank()) {
                            FilledButton(
                                onClick = { inputValue = "" },
                                icon = R.drawable.ic_cross,
                                backgroundColor = Color.Transparent,
                                iconTint = MaterialTheme.colors.onSurface,
                                padding = PaddingValues(12.dp),
                            )
                        }
                    }
                },
                label = {
                    if (inputWords.size <= 12) {
                        Text(text = stringResource(id = R.string.startup_recovery_input_label, inputWords.size), style = MaterialTheme.typography.body2)
                    } else {
                        Text(text = stringResource(id = R.string.startup_recovery_input_label_error), style = MaterialTheme.typography.body2.copy(color = negativeColor))
                    }
                },
                isError = isSeedValid == false,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.None
                ),
                colors = if (isSeedValid == false) errorOutlinedTextFieldColors() else outlinedTextFieldColors(),
                visualTransformation = VisualTransformation.None,
                maxLines = 3,
                singleLine = false,
                enabled = state is StartupRecoveryState.Init,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is StartupRecoveryState.Init -> {
                    if (isSeedValid == false) {
                        ErrorMessage(
                            header = stringResource(id = R.string.startup_recovery_invalid),
                            alignment = Alignment.CenterHorizontally,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    FilledButton(
                        text = stringResource(id = R.string.startup_recovery_import_button),
                        icon = R.drawable.ic_check,
                        enabled = isSeedValid == true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { vm.recoverSeed(inputWords, onRecoveryDone = onRecoveryDone) }
                    )
                }
                is StartupRecoveryState.CheckingSeed -> {
                    Text(text = stringResource(id = R.string.startup_recovery_checking))
                }
                is StartupRecoveryState.Error -> {
                    ErrorMessage(
                        header = when (state) {
                            is StartupRecoveryState.Error.Other -> stringResource(id = R.string.startup_recovery_error_default)
                            is StartupRecoveryState.Error.SeedDoesNotMatch -> stringResource(id = R.string.startup_recovery_error_incorrect_seed)
                            is StartupRecoveryState.Error.KeyStoreFailure -> stringResource(id = R.string.startup_recovery_error_keystore_error)
                        },
                        alignment = Alignment.CenterHorizontally,
                    )
                    Spacer(Modifier.height(16.dp))
                    BorderButton(
                        text = stringResource(id = R.string.startup_recovery_reset_button),
                        icon = R.drawable.ic_revert,
                        onClick = { vm.state.value = StartupRecoveryState.Init }
                    )
                }
                is StartupRecoveryState.Success.MatchingData -> {
                    SuccessMessage(header = stringResource(id = R.string.startup_recovery_success_match))
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
