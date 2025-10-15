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

package fr.acinq.phoenix.android.settings.channels

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.inputs.AmountInput
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.data.BitcoinUnit

@Composable
fun SpendFromChannelAddress(
    business: PhoenixBusiness,
    onBackClick: () -> Unit,
) {
    val vm = viewModel<SpendFromChannelAddressViewModel>(factory = SpendFromChannelAddressViewModel.Factory(business))
    val state = vm.state.value

    var amount by remember { mutableStateOf<MilliSatoshi?>(null) }
    var txIndex by remember { mutableStateOf("") }
    var channelData by remember { mutableStateOf("") }
    var remoteFundingPubkey by remember { mutableStateOf("") }
    var unsignedTx by remember { mutableStateOf("") }

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.spendchanneladdress_title))
        Card(internalPadding = PaddingValues(16.dp)) {
            Text(text = stringResource(id = R.string.spendchanneladdress_instructions))
            Spacer(modifier = Modifier.height(24.dp))

            // amount
            AmountInput(
                amount = amount,
                onAmountChange = {
                    if (it?.amount != amount) vm.resetState()
                    amount = it?.amount
                },
                staticLabel = stringResource(id = R.string.spendchanneladdress_amount),
                forceUnit = BitcoinUnit.Sat,
                enabled = state.canProcess,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // tx index
            TextInput(
                text = txIndex,
                onTextChange = {newValue ->
                    if (newValue != txIndex) { vm.resetState() }
                    newValue.toLongOrNull()?.let { txIndex = newValue }
                },
                staticLabel = stringResource(id = R.string.spendchanneladdress_tx_index),
                minLines = 1,
                maxLines = 1,
                enabled = state.canProcess,
                keyboardType = KeyboardType.Number
            )
            Spacer(modifier = Modifier.height(16.dp))

            // encrypted channel data
            TextInput(
                text = channelData,
                onTextChange = {
                    if (it != channelData) { vm.resetState() }
                    channelData = it
                },
                staticLabel = stringResource(id = R.string.spendchanneladdress_channel_data),
                minLines = 2,
                maxLines = 4,
                enabled = state.canProcess
            )
            Spacer(modifier = Modifier.height(16.dp))

            // remote funding pubkey
            TextInput(
                text = remoteFundingPubkey,
                onTextChange = {
                    if (it != remoteFundingPubkey) { vm.resetState() }
                    remoteFundingPubkey = it
                },
                staticLabel = stringResource(id = R.string.spendchanneladdress_remote_funding_pubkey),
                minLines = 1,
                maxLines = 2,
                enabled = state.canProcess
            )
            Spacer(modifier = Modifier.height(16.dp))

            // unsigned tx
            TextInput(
                text = unsignedTx,
                onTextChange = {
                    if (it != unsignedTx) { vm.resetState() }
                    unsignedTx = it
                },
                staticLabel = stringResource(id = R.string.spendchanneladdress_unsigned_tx),
                minLines = 1,
                maxLines = 3,
                enabled = state.canProcess
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is SpendFromChannelAddressViewState.Init, is SpendFromChannelAddressViewState.Error -> {
                    if (state is SpendFromChannelAddressViewState.Error) {
                        ErrorMessage(
                            header = stringResource(id = R.string.spendchanneladdress_error_generic),
                            details = when (state) {
                                is SpendFromChannelAddressViewState.Error.Generic -> {
                                    state.cause.message
                                }
                                is SpendFromChannelAddressViewState.Error.AmountMissing -> {
                                    stringResource(id = R.string.spendchanneladdress_error_amount)
                                }
                                is SpendFromChannelAddressViewState.Error.TxIndexMalformed -> {
                                    stringResource(id = R.string.spendchanneladdress_error_tx_index)
                                }
                                is SpendFromChannelAddressViewState.Error.InvalidChannelKeyPath -> {
                                    stringResource(id = R.string.spendchanneladdress_error_channel_keypath)
                                }
                                is SpendFromChannelAddressViewState.Error.PublicKeyMalformed -> {
                                    stringResource(id = R.string.spendchanneladdress_error_remote_funding_pubkey, state.details)
                                }
                                is SpendFromChannelAddressViewState.Error.TransactionMalformed -> {
                                    stringResource(id = R.string.spendchanneladdress_error_tx, state.details)
                                }
                                is SpendFromChannelAddressViewState.Error.InvalidSig -> {
                                    stringResource(R.string.spendchanneladdress_error_invalid_sig)
                                }
                            },
                            alignment = Alignment.CenterHorizontally
                        )
                        if (state is SpendFromChannelAddressViewState.Error.InvalidSig) {
                            val context = LocalContext.current
                            FilledButton(
                                text = "Copy error data",
                                onClick = {
                                    copyToClipboard(
                                        context = context,
                                        data = """
                                            tx_id=${state.txId}
                                            funding_script=${state.fundingScript.toHex()}
                                            public_key=${state.publicKey.toHex()}
                                            signature=${state.signature.toHex()}
                                        """.trimIndent(),
                                        dataLabel = "signature error data"
                                    )
                                }
                            )
                        }
                    }

                    Card {
                        FilledButton(
                            text = stringResource(id = R.string.spendchanneladdress_sign_button),
                            icon = R.drawable.ic_build,
                            onClick = { vm.spendFromChannelAddress(amount?.truncateToSatoshi(), txIndex.toLongOrNull(), channelData, remoteFundingPubkey, unsignedTx) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape
                        )
                    }
                }
                is SpendFromChannelAddressViewState.Processing -> {
                    ProgressView(text = stringResource(id = R.string.spendchanneladdress_signing))
                }
                is SpendFromChannelAddressViewState.SignedTransaction -> {
                    val context = LocalContext.current
                    Card {
                        Spacer(modifier = Modifier.height(8.dp))
                        SigLabelValue(label = stringResource(id = R.string.spendchanneladdress_success_pubkey), value = state.pubkey.toHex())
                        SigLabelValue(label = stringResource(id = R.string.spendchanneladdress_success_signature), value = state.signature.toHex())
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            text = stringResource(id = R.string.btn_copy),
                            icon = R.drawable.ic_copy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                copyToClipboard(
                                    context = context,
                                    data = """
                                        pubkey=${state.pubkey.toHex()}
                                        signature=${state.signature.toHex()}
                                    """.trimIndent(),
                                    dataLabel = "channel outpoint spending data"
                                )
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
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
