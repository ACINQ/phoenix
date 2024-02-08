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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountInput
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.InlineSatoshiInput
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.settings.channels.ImportChannelsDataViewModel
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.data.BitcoinUnit


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SwapInSignerView(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val vm = viewModel<SwapInSignerViewModel>(factory = SwapInSignerViewModel.Factory(business.walletManager))

    var amountInput by remember { mutableStateOf<MilliSatoshi?>(null) }
    var txInput by remember { mutableStateOf("") }

    DefaultScreenLayout(isScrollable = true) {
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(id = R.string.swapin_signer_title),
        )

        Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            val state = vm.state.value
            Text(text = stringResource(id = R.string.swapin_signer_instructions))
            Spacer(modifier = Modifier.height(16.dp))
            AmountInput(
                amount = amountInput,
                onAmountChange = {
                    amountInput = it?.amount
                    if (state != SwapInSignerState.Init) vm.state.value = SwapInSignerState.Init
                },
                staticLabel = stringResource(id = R.string.swapin_signer_amount),
                enabled = state !is SwapInSignerState.Signing,
                modifier = Modifier.fillMaxWidth(),
                forceUnit = BitcoinUnit.Sat,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextInput(
                text = txInput,
                onTextChange = {
                    txInput = it
                    if (state != SwapInSignerState.Init) vm.state.value = SwapInSignerState.Init
                },
                staticLabel = stringResource(id = R.string.swapin_signer_tx),
                maxLines = 4,
                enabled = state !is SwapInSignerState.Signing,
                errorMessage = if (txInput.isBlank()) stringResource(id = R.string.validation_empty) else null
            )
        }

        val keyboardManager = LocalSoftwareKeyboardController.current
        Card(horizontalAlignment = Alignment.CenterHorizontally) {
            when (val state = vm.state.value) {
                is SwapInSignerState.Init -> {
                    Button(
                        text = stringResource(id = R.string.swapin_signer_sign),
                        icon = R.drawable.ic_check,
                        onClick = {
                            if (amountInput != null && txInput.isNotBlank()) {
                                vm.sign(unsignedTx = txInput, amount = amountInput!!.truncateToSatoshi())
                                keyboardManager?.hide()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is SwapInSignerState.Signing -> {
                    ProgressView(text = stringResource(id = R.string.swapin_signer_signing))
                }
                is SwapInSignerState.Signed -> {
                    Column(modifier = Modifier.padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp))) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = stringResource(id = R.string.swapin_signer_signed_sig), style = MaterialTheme.typography.body2, modifier = Modifier.width(100.dp))
                            Text(text = state.userSig)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        BorderButton(
                            text = stringResource(id = R.string.btn_copy),
                            icon = R.drawable.ic_copy,
                            onClick = {
                                copyToClipboard(
                                    context = context,
                                    data = """
                                        user_sig=${state.userSig}
                                    """.trimIndent(),
                                    dataLabel = "swap input signature"
                                )
                            }
                        )
                    }
                }
                is SwapInSignerState.Failed.Error -> {
                    ErrorMessage(
                        header = stringResource(id = R.string.swapin_signer_error_header),
                        details = state.cause.message ?: state.cause::class.java.simpleName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is SwapInSignerState.Failed.InvalidTxInput -> {
                    ErrorMessage(
                        header = stringResource(id = R.string.swapin_signer_invalid_tx_header),
                        details = stringResource(id = R.string.swapin_signer_invalid_tx_details),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}