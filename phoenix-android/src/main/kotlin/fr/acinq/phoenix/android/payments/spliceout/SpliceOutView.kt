/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.payments.spliceout.SpliceOutState
import fr.acinq.phoenix.android.payments.spliceout.SpliceOutViewModel
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SendSpliceOutView(
    requestedAmount: Satoshi?,
    address: String,
    onBackClick: () -> Unit,
    onSpliceOutSuccess: () -> Unit,
) {
    val log = logger("SendSpliceOut")
    log.info { "init splice-out with amount=$requestedAmount address=$address" }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefBtcUnit = LocalBitcoinUnit.current
    val keyboardManager = LocalSoftwareKeyboardController.current

    val peerManager = business.peerManager
    val balance = business.balanceManager.balance.collectAsState(null).value
    val vm = viewModel<SpliceOutViewModel>(factory = SpliceOutViewModel.Factory(peerManager, business.chain))

    var feerate by remember { mutableStateOf<Satoshi?>(null) }
    var amount by remember { mutableStateOf(requestedAmount) }
    var amountErrorMessage by remember { mutableStateOf("") }

    scope.launch {
        peerManager.onChainFeeratesFlow.filterNotNull().first().let {
            if (feerate == null) {
                feerate = FeeratePerByte(it.fundingFeerate).feerate
            }
        }
    }

    SplashLayout(
        header = { BackButtonWithBalance(onBackClick = onBackClick, balance = balance) },
        topContent = {
            AmountHeroInput(
                initialAmount = amount?.toMilliSatoshi(),
                onAmountChange = {
                    amountErrorMessage = ""
                    val newAmount = it?.amount?.truncateToSatoshi()
                    if (vm.state != SpliceOutState.Init && amount != newAmount) {
                        vm.state = SpliceOutState.Init
                    }
                    when {
                        newAmount == null -> {}
                        balance != null && newAmount > balance.truncateToSatoshi() -> {
                            amountErrorMessage = context.getString(R.string.send_error_amount_over_balance)
                        }
                        requestedAmount != null && newAmount < requestedAmount -> {
                            amountErrorMessage = context.getString(R.string.send_error_amount_below_requested,
                                (requestedAmount).toMilliSatoshi().toPrettyString(prefBtcUnit, withUnit = true))
                        }
                    }
                    amount = newAmount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp
            )
        }
    ) {
        SplashLabelRow(label = stringResource(id = R.string.send_spliceout_feerate_label)) {
            feerate?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("") // trick to force baseline alignment with label in the container's row
                    SatoshiSlider(
                        modifier = Modifier.widthIn(max = 130.dp),
                        amount = it,
                        onAmountChange = { newFeerate ->
                            if (vm.state != SpliceOutState.Init && feerate != newFeerate) {
                                vm.state = SpliceOutState.Init
                            }
                            feerate = newFeerate
                        },
                    )
                }
                Text(text = stringResource(id = R.string.utils_fee_rate,it.toPrettyString(BitcoinUnit.Sat, withUnit = false)), modifier = Modifier.offset(y = (-12).dp))
                // TODO if fee too big, warning
            } ?: ProgressView(text = stringResource(id = R.string.send_spliceout_feerate_waiting_for_value), padding = PaddingValues(0.dp))
        }
        SplashLabelRow(label = stringResource(R.string.send_spliceout_address_label), icon = R.drawable.ic_chain) {
            SelectionContainer {
                Text(text = address)
            }
        }

        when (val state = vm.state) {
            is SpliceOutState.Init, is SpliceOutState.Error -> {
                Spacer(modifier = Modifier.height(24.dp))
                if (state is SpliceOutState.Error.Thrown) {
                    ErrorMessage(errorHeader = "Placeholder error message!", errorDetails = state.e.localizedMessage, alignment = Alignment.CenterHorizontally)
                } else if (state is SpliceOutState.Error.NoChannels) {
                    ErrorMessage(errorHeader = "No channels available", errorDetails = "placeholder message", alignment = Alignment.CenterHorizontally)
                }
                BorderButton(
                    text = stringResource(id = R.string.send_spliceout_prepare_button),
                    icon = R.drawable.ic_build,
                    enabled = amountErrorMessage.isBlank(),
                    onClick = {
                        val finalAmount = amount
                        val finalFeerate = feerate
                        if (finalAmount == null) {
                            amountErrorMessage = context.getString(R.string.send_error_amount_invalid)
                        } else if (finalFeerate == null) {
                            amountErrorMessage = context.getString(R.string.send_spliceout_error_invalid_feerate)
                        } else {
                            keyboardManager?.hide()
                            vm.prepareSpliceOut(finalAmount, finalFeerate, address)
                        }
                    }
                )
            }
            is SpliceOutState.Preparing -> {
                Spacer(modifier = Modifier.height(24.dp))
                ProgressView(text = stringResource(id = R.string.send_spliceout_prepare_in_progress))
            }
            is SpliceOutState.ReadyToSend -> {
                SplashLabelRow(label = "") {
                    Spacer(modifier = Modifier.height(16.dp))
                    HSeparator(width = 60.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                val total = state.userAmount + state.estimatedFee
                SpliceOutFeeSummaryView(fee = state.estimatedFee, total = total, userFeerate = state.userFeerate, actualFeerate = state.actualFeerate)
                Spacer(modifier = Modifier.height(24.dp))

                if (balance != null && total.toMilliSatoshi() > balance) {
                    ErrorMessage(errorHeader = stringResource(R.string.send_spliceout_error_cannot_afford_fees), alignment = Alignment.CenterHorizontally)
                } else {
                    FilledButton(
                        text = stringResource(id = R.string.send_pay_button),
                        icon = R.drawable.ic_send,
                        enabled = amountErrorMessage.isBlank()
                    ) {
                        vm.executeSpliceOut(state.userAmount, state.actualFeerate, address)
                    }
                }
            }
            is SpliceOutState.Executing -> {
                ProgressView(text = stringResource(id = R.string.send_spliceout_prepare_in_progress))
            }
            is SpliceOutState.Complete.Success -> {
                LaunchedEffect(key1 = Unit) { onSpliceOutSuccess() }
            }
            is SpliceOutState.Complete.Failure -> {
                ErrorMessage(
                    errorHeader = stringResource(id = R.string.send_spliceout_error_failure),
                    errorDetails = state.result::class.java.simpleName // TODO handle error
                )
            }
        }
    }
}

@Composable
private fun SpliceOutFeeSummaryView(
    fee: Satoshi,
    userFeerate: FeeratePerKw,
    actualFeerate: FeeratePerKw,
    total: Satoshi,
) {
    SplashLabelRow(label = stringResource(id = R.string.send_spliceout_complete_recap_fee), helpMessage = "Uses an actual feerate of ${actualFeerate}. Takes into account the chain of unconfirmed txs.") {
        AmountWithFiatColumnView(amount = fee.toMilliSatoshi(), amountTextStyle = MaterialTheme.typography.body2)
    }
    SplashLabelRow(label = stringResource(id = R.string.send_spliceout_complete_recap_total)) {
        AmountWithFiatColumnView(amount = total.toMilliSatoshi(), amountTextStyle = MaterialTheme.typography.body2)
    }
}
