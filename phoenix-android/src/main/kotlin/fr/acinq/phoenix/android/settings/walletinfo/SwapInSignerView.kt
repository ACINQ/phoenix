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

package fr.acinq.phoenix.android.settings.walletinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.utils.swapin.LegacySwapInSignerResult
import fr.acinq.phoenix.utils.swapin.TaprootSwapInSignerResult


@Composable
fun SwapInSignerView(
    business: PhoenixBusiness,
    onBackClick: () -> Unit,
) {
    val vm = viewModel<SwapInSignerViewModel>(factory = SwapInSignerViewModel.Factory(business))

    DefaultScreenLayout(isScrollable = true) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.swapin_signer_title),
        )
        val state = vm.state.value
        Card {
            Text(
                text = stringResource(id = R.string.swapin_signer_instructions),
                modifier = Modifier.padding(16.dp),
            )
            val options = listOf(SwapInOptions.LEGACY, SwapInOptions.TAPROOT)
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(options[0]) }
            Column(Modifier.selectableGroup()) {
                options.forEach { opt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (opt == selectedOption),
                                onClick = {
                                    onOptionSelected(opt)
                                    when (opt) {
                                        SwapInOptions.LEGACY -> if (state !is LegacySwapInSignerState) vm.state.value = LegacySwapInSignerState.Init
                                        SwapInOptions.TAPROOT -> if (state !is TaprootSwapInSignerState) vm.state.value = TaprootSwapInSignerState.Init
                                    }
                                },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (opt == selectedOption),
                            onClick = null
                        )
                        Text(
                            text = when (opt) {
                                SwapInOptions.LEGACY -> "Legacy"
                                SwapInOptions.TAPROOT -> "Taproot"
                            },
                            style = MaterialTheme.typography.body1.merge(),
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }

        when (state) {
            is LegacySwapInSignerState -> {
                SignLegacySwapInView(
                    state = state,
                    onResetState = { vm.state.value = LegacySwapInSignerState.Init },
                    onSign = { vm.signLegacy(it) }
                )
            }
            is TaprootSwapInSignerState -> {
                SignTaprootSwapInView(
                    state = state,
                    onResetState = { vm.state.value = TaprootSwapInSignerState.Init },
                    onSign = { unsignedTx, serverNonce -> vm.signTaproot(unsignedTx, serverNonce) }
                )
            }
        }
    }
}

@Composable
private fun SignLegacySwapInView(
    state: LegacySwapInSignerState,
    onResetState: () -> Unit,
    onSign: (String) -> Unit,
) {
    val context = LocalContext.current
    var txInput by remember { mutableStateOf("") }
    var txInputError by remember { mutableStateOf("") }

    Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        TextInput(
            text = txInput,
            onTextChange = {
                txInput = it
                txInputError = if (it.isBlank()) context.getString(R.string.validation_empty) else ""
                if (state != LegacySwapInSignerState.Init) onResetState()
            },
            staticLabel = stringResource(id = R.string.swapin_signer_tx),
            maxLines = 4,
            enabled = state !is LegacySwapInSignerState.Signing,
            errorMessage = txInputError
        )
    }

    val keyboardManager = LocalSoftwareKeyboardController.current
    Card(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when (state) {
            is LegacySwapInSignerState.Init -> {
                Button(
                    text = stringResource(id = R.string.swapin_signer_sign),
                    icon = R.drawable.ic_check,
                    onClick = {
                        if (txInput.isBlank()) {
                            txInputError = context.getString(R.string.validation_empty)
                            return@Button
                        }
                        onSign(txInput)
                        keyboardManager?.hide()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is LegacySwapInSignerState.Signing -> {
                ProgressView(text = stringResource(id = R.string.swapin_signer_signing))
            }
            is LegacySwapInSignerState.Signed -> {
                Spacer(modifier = Modifier.height(8.dp))
                SigLabelValue(label = stringResource(id = R.string.walletinfo_swapin_user_pubkey), value = state.result.userPubkey)
                SigLabelValue(label = stringResource(id = R.string.swapin_signer_signed_sig), value = state.result.userSignature)
                Spacer(modifier = Modifier.height(16.dp))
                BorderButton(
                    text = stringResource(id = R.string.btn_copy),
                    icon = R.drawable.ic_copy,
                    onClick = {
                        copyToClipboard(
                            context = context,
                            data = """
                                tx_id=${state.result.txId}
                                user_key=${state.result.userPubkey}
                                user_sig=${state.result.userSignature}
                            """.trimIndent(),
                            dataLabel = "legacy swap sig data"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            is LegacySwapInSignerState.Failed -> {
                ErrorMessage(
                    header = stringResource(id = R.string.swapin_signer_error_header),
                    details = when (val failure = state.result) {
                        is LegacySwapInSignerResult.Failure.ParentTxNotFound -> stringResource(id = R.string.swapin_signer_error_details_parent_not_found)
                        is LegacySwapInSignerResult.Failure.SigningError -> stringResource(id = R.string.swapin_signer_error_details_signing_error)
                        is LegacySwapInSignerResult.Failure.TooManyInputs -> stringResource(id = R.string.swapin_signer_error_details_too_many_inputs, failure.inputSize)
                        is LegacySwapInSignerResult.Failure.TransactionMalformed -> stringResource(id = R.string.swapin_signer_error_details_tx_malformed)
                    },
                    alignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(40.dp))
}

@Composable
private fun SignTaprootSwapInView(
    state: TaprootSwapInSignerState,
    onResetState: () -> Unit,
    onSign: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var txInput by remember { mutableStateOf("") }
    var txInputError by remember { mutableStateOf("") }
    var serverNonce by remember { mutableStateOf("") }
    var serverNonceError by remember { mutableStateOf("") }

    Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        TextInput(
            text = txInput,
            onTextChange = {
                txInput = it
                txInputError = if (it.isBlank()) context.getString(R.string.validation_empty) else ""
                if (state != TaprootSwapInSignerState.Init) onResetState()
            },
            staticLabel = stringResource(id = R.string.swapin_signer_tx),
            maxLines = 4,
            enabled = state !is TaprootSwapInSignerState.Signing,
            errorMessage = txInputError
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextInput(
            text = serverNonce,
            onTextChange = {
                serverNonce = it
                serverNonceError = if (it.isBlank()) context.getString(R.string.validation_empty) else ""
                if (state != TaprootSwapInSignerState.Init) onResetState()
            },
            staticLabel = stringResource(id = R.string.swapin_signer_server_nonce),
            maxLines = 4,
            enabled = state !is TaprootSwapInSignerState.Signing,
            errorMessage = serverNonceError
        )
    }

    val keyboardManager = LocalSoftwareKeyboardController.current
    Card(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when (state) {
            is TaprootSwapInSignerState.Init -> {
                Button(
                    text = stringResource(id = R.string.swapin_signer_sign),
                    icon = R.drawable.ic_check,
                    onClick = {
                        if (txInput.isBlank()) {
                            txInputError = context.getString(R.string.validation_empty)
                            return@Button
                        }
                        if (serverNonce.isBlank()) {
                            serverNonceError = context.getString(R.string.validation_empty)
                            return@Button
                        }
                        onSign(txInput, serverNonce)
                        keyboardManager?.hide()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is TaprootSwapInSignerState.Signing -> {
                ProgressView(text = stringResource(id = R.string.swapin_signer_signing))
            }
            is TaprootSwapInSignerState.Signed -> {
                SigLabelValue(label = stringResource(id = R.string.walletinfo_swapin_user_pubkey), value = state.result.userPubkey)
                SigLabelValue(label = "User refund key", value = state.result.userRefundKey)
                SigLabelValue(label = "User nonce", value = state.result.userNonce)
                SigLabelValue(label = stringResource(id = R.string.swapin_signer_signed_sig), value = state.result.userSignature)
                Spacer(modifier = Modifier.height(16.dp))
                BorderButton(
                    text = stringResource(id = R.string.btn_copy),
                    icon = R.drawable.ic_copy,
                    onClick = {
                        copyToClipboard(
                            context = context,
                            data = """
                                tx_id=${state.result.txId}
                                user_key=${state.result.userPubkey}
                                user_sig=${state.result.userSignature}
                                user_refund_key=${state.result.userRefundKey}
                                user_nonce=${state.result.userNonce}
                            """.trimIndent(),
                            dataLabel = "taproot swap sig data"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            is TaprootSwapInSignerState.Failed -> {
                ErrorMessage(
                    header = stringResource(id = R.string.swapin_signer_error_header),
                    details = when (val failure = state.result) {
                        is TaprootSwapInSignerResult.Failure.ParentTxNotFound -> stringResource(id = R.string.swapin_signer_error_details_parent_not_found)
                        is TaprootSwapInSignerResult.Failure.SigningError -> stringResource(id = R.string.swapin_signer_error_details_signing_error)
                        is TaprootSwapInSignerResult.Failure.TooManyInputs -> stringResource(id = R.string.swapin_signer_error_details_too_many_inputs, failure.inputSize)
                        is TaprootSwapInSignerResult.Failure.TransactionMalformed -> stringResource(id = R.string.swapin_signer_error_details_tx_malformed)
                        is TaprootSwapInSignerResult.Failure.SwapProtocolError -> stringResource(id = R.string.swapin_signer_error_details_swap_protocol)
                        is TaprootSwapInSignerResult.Failure.NonceGenerationFailure -> stringResource(id = R.string.swapin_signer_error_details_nonce_generation_failure)
                        is TaprootSwapInSignerResult.Failure.AddressIndexNotFound -> stringResource(id = R.string.swapin_signer_error_details_address_index)
                    },
                    alignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(40.dp))
}

@Composable
private fun SigLabelValue(label: String, value: String) {
    Row(
        modifier = Modifier.padding(PaddingValues(horizontal = 16.dp, vertical = 6.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.body2, modifier = Modifier.width(100.dp))
        Text(text = value)
    }
}
