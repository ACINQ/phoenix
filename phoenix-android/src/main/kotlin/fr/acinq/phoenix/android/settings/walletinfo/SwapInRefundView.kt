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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.LocalFiatCurrency
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountWithFiatBelow
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.FeerateSlider
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.TextInput
import fr.acinq.phoenix.android.components.InlineTransactionLink
import fr.acinq.phoenix.android.components.buttons.SmartSpendButton
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.SuccessMessage
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.components.scanner.ScannerView
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.Parser

@Composable
fun SendSwapInRefundView(
    onBackClick: () -> Unit,
) {
    val peerManager = business.peerManager
    val swapInWallet by peerManager.swapInWallet.collectAsState()
    val availableForRefund = swapInWallet?.readyForRefund?.balance

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.swapinrefund_title))

        when (availableForRefund) {
            null -> {
                ProgressView(text = stringResource(id = R.string.utils_loading_data), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp))
            }

            else -> {
                AvailableForRefundView(peerManager = peerManager, availableForRefund = availableForRefund)
            }
        }
    }
}

@Composable
private fun AvailableForRefundView(
    peerManager: PeerManager,
    availableForRefund: Satoshi,
) {
    val electrumClient = business.electrumClient
    val walletManager = business.walletManager
    val vm = viewModel<SwapInRefundViewModel>(factory = SwapInRefundViewModel.Factory(peerManager, walletManager, electrumClient))
    val state = vm.state
    val keyboardManager = LocalSoftwareKeyboardController.current

    var address by remember { mutableStateOf("") }
    var addressErrorMessage by remember { mutableStateOf<String?>(null) }
    var showScannerView by remember { mutableStateOf(false) }

    val mempoolFeerate by business.appConfigurationManager.mempoolFeerate.collectAsState()
    var feerate by remember { mutableStateOf(mempoolFeerate?.halfHour?.feerate) }

    if (showScannerView) {
        SwapInRefundScanner(
            onScannerDismiss = { showScannerView = false },
            onAddressChange = {
                address = Parser.trimMatchingPrefix(Parser.removeExcessInput(it), Parser.bitcoinPrefixes)
                addressErrorMessage = ""
                showScannerView = false
            }
        )
    } else {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (availableForRefund == 0.sat) {
                Text(
                    text = stringResource(id = R.string.swapinrefund_none_available_label),
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = annotatedStringResource(
                            id = R.string.swapinrefund_available_label,
                            availableForRefund.toPrettyString(LocalBitcoinUnit.current, withUnit = true),
                            availableForRefund.toPrettyString(LocalFiatCurrency.current, fiatRate, withUnit = true)
                        )
                    )
                }

                Card(internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.fillMaxWidth()) {

                    Text(text = stringResource(id = R.string.swapinrefund_instructions_1))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(id = R.string.swapinrefund_instructions_2))
                    Spacer(modifier = Modifier.height(24.dp))

                    TextInput(
                        text = address,
                        onTextChange = {
                            addressErrorMessage = ""
                            vm.state = SwapInRefundState.Init
                            address = it
                        },
                        staticLabel = stringResource(id = R.string.mutualclose_input_label),
                        trailingIcon = {
                            Button(
                                onClick = { showScannerView = true },
                                icon = R.drawable.ic_scan_qr,
                                iconTint = MaterialTheme.colors.primary
                            )
                        },
                        errorMessage = addressErrorMessage,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !(vm.state is SwapInRefundState.Publishing || vm.state is SwapInRefundState.Publishing || vm.state is SwapInRefundState.Done.Success),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SplashLabelRow(label = stringResource(id = R.string.send_spliceout_feerate_label)) {
                        feerate?.let { currentFeerate ->
                            FeerateSlider(
                                feerate = currentFeerate,
                                onFeerateChange = { newFeerate ->
                                    if (vm.state != SwapInRefundState.Init && feerate != newFeerate) {
                                        vm.state = SwapInRefundState.Init
                                    }
                                    feerate = newFeerate
                                },
                                mempoolFeerate = mempoolFeerate,
                                enabled = !(vm.state is SwapInRefundState.Publishing || vm.state is SwapInRefundState.Publishing || vm.state is SwapInRefundState.Done.Success),
                            )
                        } ?: ProgressView(text = stringResource(id = R.string.send_spliceout_feerate_waiting_for_value), padding = PaddingValues(0.dp))
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when (val currentState = vm.state) {
                    is SwapInRefundState.Init, is SwapInRefundState.Done.Failed -> {
                        if (availableForRefund > 0.sat) {
                            var showLowFeerateDialog by remember(feerate, mempoolFeerate) { mutableStateOf(false) }

                            if (showLowFeerateDialog) {
                                ConfirmLowFeerate(
                                    onConfirm = {
                                        feerate?.let {
                                            vm.getFeeForRefund(address = address, feerate = FeeratePerByte(it))
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
                                            vm.getFeeForRefund(address = address, feerate = FeeratePerByte(finalFeerate))
                                        }
                                    }
                                },
                                padding = PaddingValues(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    is SwapInRefundState.GettingFee -> {
                        ProgressView(text = stringResource(id = R.string.swapinrefund_estimating))
                    }

                    is SwapInRefundState.ReviewFee -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        SplashLabelRow(
                            label = stringResource(id = R.string.send_spliceout_complete_recap_fee),
                            helpMessage = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_help)
                        ) {
                            AmountWithFiatBelow(amount = currentState.fees.toMilliSatoshi(), amountTextStyle = MaterialTheme.typography.body2)
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        SmartSpendButton(
                            text = stringResource(id = R.string.swapinrefund_send_button),
                            onSpend = { vm.executeRefund(currentState.transaction) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = true,
                        )
                    }

                    is SwapInRefundState.Publishing -> {
                        ProgressView(text = stringResource(id = R.string.swapinrefund_sending))
                    }

                    is SwapInRefundState.Done.Success -> {
                        SuccessMessage(header = stringResource(id = R.string.swapinrefund_success), alignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth())
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(text = stringResource(id = R.string.swapinrefund_success_details))
                            Spacer(modifier = Modifier.height(12.dp))
                            InlineTransactionLink(txId = currentState.tx.txid)
                        }
                    }
                }
            }

            if (state is SwapInRefundState.Done.Failed) {
                ErrorMessage(
                    header = stringResource(id = R.string.swapinrefund_failed),
                    details = when (state) {
                        is SwapInRefundState.Done.Failed.InvalidAddress -> stringResource(id = R.string.swapinrefund_failed_address)
                        is SwapInRefundState.Done.Failed.Error -> state.e.message
                        is SwapInRefundState.Done.Failed.CannotCreateTx -> stringResource(id = R.string.swapinrefund_failed_cannot_create)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    alignment = Alignment.CenterHorizontally,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
private fun SwapInRefundScanner(
    onScannerDismiss: () -> Unit,
    onAddressChange: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ScannerView(onScannedText = onAddressChange, isPaused = false, onDismiss = onScannerDismiss)
    }
}
