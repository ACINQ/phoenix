/*
 * Copyright 2020 ACINQ SAS
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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.Command
import fr.acinq.lightning.transactions.Transactions
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private sealed class SpliceOutState {
    object Init: SpliceOutState()
    data class Preparing(val userAmount: Satoshi, val feeratePerByte: Satoshi): SpliceOutState()
    data class ReadyToSend(val userAmount: Satoshi, val feeratePerByte: Satoshi, val feeExpected: Satoshi): SpliceOutState()
    data class Executing(val userAmount: Satoshi, val feeratePerByte: Satoshi): SpliceOutState()
    sealed class Complete: SpliceOutState() {
        abstract val userAmount: Satoshi
        abstract val feeratePerByte: Satoshi
        abstract val result: Command.Splice.Response
        data class Success(override val userAmount: Satoshi, override val feeratePerByte: Satoshi, override val result: Command.Splice.Response.Created): Complete()
        data class Failure(override val userAmount: Satoshi, override val feeratePerByte: Satoshi, override val result: Command.Splice.Response.Failure): Complete()
    }
    sealed class Error: SpliceOutState() {
        data class Thrown(val e: Throwable): Error()
        object NoChannels: Error()
    }
}

private class SpliceOutViewModel(private val peerManager: PeerManager, private val chain: NodeParams.Chain): ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)
    var state by mutableStateOf<SpliceOutState>(SpliceOutState.Init)

    /** Estimate the fee for the splice-out, given a feerate. */
    fun prepareSpliceOut(
        amount: Satoshi,
        feeratePerByte: Satoshi,
    ) {
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when preparing splice-out: ", e)
            state = SpliceOutState.Error.Thrown(e)
        }) {
            val feeratePerKw = FeeratePerKw(FeeratePerByte(feeratePerByte))
            val fee = Transactions.weight2fee(feeratePerKw, 722) // FIXME hardcoded weight!
            state = SpliceOutState.ReadyToSend(amount, feeratePerByte, fee)
        }
    }

    fun executeSpliceOut(
        amount: Satoshi,
        feeratePerByte: Satoshi,
        address: String,
    ) {
        if (state is SpliceOutState.ReadyToSend) {
            state = SpliceOutState.Executing(amount, feeratePerByte)
            viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
                log.error("error when executing splice-out: ", e)
                state = SpliceOutState.Error.Thrown(e)
            }) {
                val response = peerManager.getPeer().spliceOut(
                    amount = amount,
                    scriptPubKey = Parser.addressToPublicKeyScript(chain, address)!!.byteVector(),
                    feeratePerKw = FeeratePerKw(FeeratePerByte(feeratePerByte))
                )
                when (response) {
                    is Command.Splice.Response.Created -> {
                        state = SpliceOutState.Complete.Success(amount, feeratePerByte, response)
                    }
                    is Command.Splice.Response.Failure -> {
                        state = SpliceOutState.Complete.Failure(amount, feeratePerByte, response)
                    }
                    null -> {
                        state = SpliceOutState.Error.NoChannels
                    }
                }
            }
        }
    }

    class Factory(
        private val peerManager: PeerManager, private val chain: NodeParams.Chain
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SpliceOutViewModel(peerManager, chain) as T
        }
    }
}

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
    val vm = viewModel<SpliceOutViewModel>(factory = SpliceOutViewModel.Factory(peerManager, business.chain))
    var amount by remember { mutableStateOf(requestedAmount) }
    var amountErrorMessage by remember { mutableStateOf("") }
    val balance = business.balanceManager.balance.collectAsState(null).value
    val feerateState = remember { mutableStateOf<Satoshi?>(null) }
    scope.launch {
        peerManager.onChainFeeratesFlow.filterNotNull().first().let {
            if (feerateState.value == null) {
                feerateState.value = FeeratePerByte(it.fundingFeerate).feerate
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
        SplashLabelRow(label = stringResource(id = R.string.send_feerate_label)) {
            val feerate = feerateState.value
            if (feerate == null) {
                ProgressView(text = stringResource(id = R.string.send_feerate_waiting_for_value))
            } else {
                Text(text = "${feerate.toPrettyString(BitcoinUnit.Sat, withUnit = false)} sat/byte")
                // FIXME: editable feerate
//                        FeerateInput(
//                            initialFeerate = feerate,
//                            onFeerateChange = { feerateState.value = it },
//                            minFeerate = 1.sat,
//                            minErrorMessage = stringResource(id = R.string.send_feerate_below_min),
//                            maxFeerate = 300.sat,
//                            maxErrorMessage = stringResource(id = R.string.send_feerate_above_max),
//                        )
            }
        }
        SplashLabelRow(label = stringResource(R.string.send_destination_label), icon = R.drawable.ic_chain) {
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
                        val finalFeerate = feerateState.value
                        if (finalAmount == null) {
                            amountErrorMessage = context.getString(R.string.send_error_amount_invalid)
                        } else if (finalFeerate == null) {
                            amountErrorMessage = context.getString(R.string.send_error_missing_feerate)
                        } else {
                            keyboardManager?.hide()
                            vm.prepareSpliceOut(finalAmount, finalFeerate)
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
                    Spacer(modifier = Modifier.height(4.dp))
                    HSeparator(width = 50.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val total = state.userAmount + state.feeExpected
                SpliceOutFeeSummaryView(fee = state.feeExpected, total = total)
                Spacer(modifier = Modifier.height(24.dp))
                if (balance != null && total.toMilliSatoshi() > balance) {
                    ErrorMessage(errorHeader = stringResource(R.string.send_spliceout_error_cannot_afford_fees), alignment = Alignment.CenterHorizontally)
                } else {
                    FilledButton(
                        text = stringResource(id = R.string.send_pay_button),
                        icon = R.drawable.ic_send,
                        enabled = amountErrorMessage.isBlank()
                    ) {
                        vm.executeSpliceOut(state.userAmount, state.feeratePerByte, address)
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
    total: Satoshi,
) {
    FeeSummary(label = stringResource(id = R.string.send_spliceout_complete_recap_fee), amount = fee.toMilliSatoshi())
    FeeSummary(label = stringResource(id = R.string.send_spliceout_complete_recap_total), amount = total.toMilliSatoshi())
}

@Composable
private fun FeeSummary(
    label: String,
    amount: MilliSatoshi
) {
    SplashLabelRow(label = label) {
        AmountWithFiatColumnView(amount = amount, amountTextStyle = MaterialTheme.typography.body2)
    }
}
