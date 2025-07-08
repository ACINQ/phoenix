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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.Transaction
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountWithFiatBelow
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.inputs.FeerateSlider
import fr.acinq.phoenix.android.components.InlineTransactionLink
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.buttons.SmartSpendButton
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.scanner.ScannerView
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.data.MempoolFeerate
import fr.acinq.phoenix.utils.Parser
import fr.acinq.phoenix.utils.extensions.confirmed

@Composable
fun FinalWalletRefundView(
    onBackClick: () -> Unit,
) {
    val vm = viewModel<FinalWalletRefundViewModel>(factory = FinalWalletRefundViewModel.Factory(business.peerManager, business.electrumClient))
    val state = vm.state.value

    val finalWallet by business.peerManager.finalWallet.collectAsState()
    val available = finalWallet?.confirmed?.filter { it.blockHeight > 0 }?.balance

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.swapinrefund_title))
        when (available) {
            null -> {
                ProgressView(text = stringResource(id = R.string.utils_loading_data))
            }
            else -> {
                AvailableForRefund(
                    available = available,
                    state = state,
                    onResetState = { vm.state.value = FinalWalletRefundState.Init },
                    onEstimateRefundFee = vm::estimateRefundFee,
                    onExecuteRefund = vm::executeRefund,
                )
                if (state is FinalWalletRefundState.Success) {
                    SuccessTx(state = state)
                }
            }
        }
    }
}

@Composable
private fun AddressInputAndFeeSlider(
    address: String,
    onAddressChange: (String) -> Unit,
    feerate: Satoshi?,
    onFeerateChange: (Satoshi) -> Unit,
    onShowScanner: () -> Unit,
    mempoolFeerate: MempoolFeerate?,
    isEnabled: Boolean
) {
    TextInput(
        text = address,
        onTextChange = onAddressChange,
        staticLabel = stringResource(id = R.string.mutualclose_input_label),
        trailingIcon = {
            Button(
                onClick = onShowScanner,
                icon = R.drawable.ic_scan_qr,
                iconTint = MaterialTheme.colors.primary
            )
        },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
        enabled = isEnabled
    )

    Spacer(modifier = Modifier.height(16.dp))

    SplashLabelRow(label = stringResource(id = R.string.send_spliceout_feerate_label)) {
        feerate?.let { currentFeerate ->
            FeerateSlider(
                feerate = currentFeerate,
                onFeerateChange = onFeerateChange,
                mempoolFeerate = mempoolFeerate,
                enabled = isEnabled
            )
        } ?: ProgressView(text = stringResource(id = R.string.send_spliceout_feerate_waiting_for_value), padding = PaddingValues(0.dp))
    }
}


@Composable
private fun ColumnScope.AvailableForRefund(
    available: Satoshi,
    state: FinalWalletRefundState,
    onResetState: () -> Unit,
    onEstimateRefundFee: (String, FeeratePerByte) -> Unit,
    onExecuteRefund: (Transaction) -> Unit,
) {
    val keyboardManager = LocalSoftwareKeyboardController.current
    val mempoolFeerate by business.appConfigurationManager.mempoolFeerate.collectAsState()

    var address by remember { mutableStateOf("") }
    var feerate by remember { mutableStateOf(mempoolFeerate?.halfHour?.feerate) }

    var showScannerView by remember { mutableStateOf(false) }

    if (showScannerView) {
        RefundScanner(
            onScannerDismiss = { showScannerView = false },
            onAddressChange = {
                address = Parser.trimMatchingPrefix(Parser.removeExcessInput(it), Parser.bitcoinPrefixes)
                showScannerView = false
            }
        )
        return
    }

    Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.fillMaxWidth()) {
        if (available == 0.sat) {
            Text(text = stringResource(id = R.string.finalwallet_refund_available_none), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption)
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Row {
                Text(text = stringResource(id = R.string.finalwallet_refund_available))
                Spacer(modifier = Modifier.width(8.dp))
                Column { AmountWithFiatBelow(amount = available.toMilliSatoshi(), amountTextStyle = MaterialTheme.typography.body2) }
            }
        }
    }

    Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(id = R.string.finalwallet_refund_instructions))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(id = R.string.swapinrefund_instructions_2))
        Spacer(modifier = Modifier.height(24.dp))
        AddressInputAndFeeSlider(
            address = address,
            onAddressChange = { address = it ; onResetState() },
            feerate = feerate,
            onFeerateChange = { feerate = it ; onResetState() },
            onShowScanner = { showScannerView = true },
            mempoolFeerate = mempoolFeerate,
            isEnabled = state !is FinalWalletRefundState.GettingFee && state !is FinalWalletRefundState.Publishing,
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            is FinalWalletRefundState.Init, is FinalWalletRefundState.Failed -> {
                var showLowFeerateDialog by remember(feerate, mempoolFeerate) { mutableStateOf(false) }

                if (showLowFeerateDialog) {
                    ConfirmLowFeerate(
                        onConfirm = {
                            feerate?.let {
                                onEstimateRefundFee(address, FeeratePerByte(it))
                                showLowFeerateDialog = false
                            }
                        },
                        onCancel = { showLowFeerateDialog = false }
                    )
                }

                Button(
                    text = stringResource(id = R.string.swapinrefund_estimate_button),
                    icon = R.drawable.ic_inspect,
                    enabled = feerate != null && address.isNotBlank(),
                    onClick = {
                        keyboardManager?.hide()
                        val finalFeerate = feerate
                        if (finalFeerate != null) {
                            val recommendedFeerate = mempoolFeerate?.hour
                            if (recommendedFeerate != null && finalFeerate < recommendedFeerate.feerate) {
                                showLowFeerateDialog = true
                            } else {
                                onEstimateRefundFee(address, FeeratePerByte(finalFeerate))
                            }
                        }
                    },
                    backgroundColor = MaterialTheme.colors.primary,
                    textStyle = MaterialTheme.typography.button.copy(color = MaterialTheme.colors.onPrimary),
                    iconTint = MaterialTheme.colors.onPrimary,
                    padding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is FinalWalletRefundState.GettingFee -> {
                ProgressView(text = stringResource(id = R.string.swapinrefund_estimating))
            }

            is FinalWalletRefundState.ReviewFee -> {
                Spacer(modifier = Modifier.height(16.dp))
                SplashLabelRow(
                    label = stringResource(id = R.string.send_spliceout_complete_recap_fee),
                    helpMessage = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_help)
                ) {
                    AmountWithFiatBelow(amount = state.fees.toMilliSatoshi(), amountTextStyle = MaterialTheme.typography.body2)
                }
                Spacer(modifier = Modifier.height(16.dp))

                SmartSpendButton(
                    text = stringResource(id = R.string.swapinrefund_send_button),
                    onSpend = { onExecuteRefund(state.transaction) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                )
            }
            is FinalWalletRefundState.Publishing -> {
                ProgressView(text = stringResource(id = R.string.swapinrefund_sending))
            }
            is FinalWalletRefundState.Success ->  Unit
        }
    }

    if (state is FinalWalletRefundState.Failed) {
        ErrorMessage(
            header = stringResource(id = R.string.swapinrefund_failed),
            details = when (state) {
                is FinalWalletRefundState.Failed.InvalidAddress -> stringResource(id = R.string.swapinrefund_failed_address)
                is FinalWalletRefundState.Failed.Error -> state.e.message
                is FinalWalletRefundState.Failed.CannotCreateTx -> stringResource(id = R.string.swapinrefund_failed_cannot_create)
            },
            modifier = Modifier.fillMaxWidth(),
            alignment = Alignment.CenterHorizontally,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    Spacer(modifier = Modifier.height(60.dp))
}

@Composable
private fun SuccessTx(state: FinalWalletRefundState.Success) {
    Card(modifier = Modifier.fillMaxWidth(), internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text =  stringResource(id = R.string.swapinrefund_success), style = MaterialTheme.typography.body2)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(id = R.string.swapinrefund_success_details))
        Spacer(modifier = Modifier.height(12.dp))
        InlineTransactionLink(txId = state.tx.txid)
    }
}

@Composable
private fun ConfirmLowFeerate(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismiss = onCancel, title = stringResource(id = R.string.spliceout_low_feerate_dialog_title), buttons = null) {
        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = annotatedStringResource(id = R.string.spliceout_low_feerate_dialog_body1))
            Text(text = annotatedStringResource(id = R.string.spliceout_low_feerate_dialog_body3))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                text = stringResource(id = R.string.btn_cancel),
                onClick = onCancel,
            )
            Button(
                text = stringResource(id = R.string.btn_confirm),
                onClick = onConfirm,
                icon = R.drawable.ic_check_circle,
                space = 8.dp,
            )
        }
    }
}

@Composable
private fun ColumnScope.RefundScanner(
    onScannerDismiss: () -> Unit,
    onAddressChange: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth().weight(1f)) {
        ScannerView(onScannedText = onAddressChange, isPaused = false, onDismiss = onScannerDismiss)
    }
}