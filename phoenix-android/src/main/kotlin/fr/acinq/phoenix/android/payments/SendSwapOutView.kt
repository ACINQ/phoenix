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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.Command
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private sealed class SpliceOutState {
    object Init: SpliceOutState()
    object Preparing: SpliceOutState()
    sealed class ReadyToSend(val userAmount: Satoshi, val feeExpected: Satoshi): SpliceOutState()
    object Executing: SpliceOutState()
    sealed class Complete: SpliceOutState() {
        data class Success(val result: Command.Splice.Response.Success): Complete()
        data class Failure(val result: Command.Splice.Response.Failure): Complete()
    }
    sealed class Error(val e: Throwable): SpliceOutState()
}

private class SpliceOutViewModel(private val peerManager: PeerManager, private val chain: Chain): ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)
    var state by mutableStateOf<SpliceOutState>(SpliceOutState.Init)

    /** Simulate splice to get the fee. */
    fun prepareSpliceOut(
        amount: Satoshi,
        feeratePerByte: Satoshi,
        address: String,
    ) {
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when preparing splice-out: ", e)
        }) {
            // TODO
            // state = SpliceOutState.ReadyToSend(123_000.sat, 456.sat)
        }
    }

    fun executeSpliceOut(
        amount: Satoshi,
        feeratePerByte: Satoshi,
        address: String,
    ) {
        if (state is SpliceOutState.ReadyToSend) {
            state = SpliceOutState.Executing
            viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
                log.error("error when executing splice-out: ", e)
            }) {
                val response = peerManager.getPeer().spliceOut(
                    amount = amount,
                    scriptPubKey = Parser.addressToPublicKeyScript(chain, address)!!.byteVector(),
                    feeratePerKw = FeeratePerKw(FeeratePerByte(feeratePerByte))
                )
                when (response) {
                    is Command.Splice.Response.Success -> {
                        state = SpliceOutState.Complete.Success(response)
                    }
                    is Command.Splice.Response.Failure -> {
                        state = SpliceOutState.Complete.Failure(response)
                    }
                    null -> {}
                }
            }
        }
    }

    class Factory(
        private val peerManager: PeerManager, private val chain: Chain
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
    val prefBtcUnit = LocalBitcoinUnit.current
    val keyboardManager = LocalSoftwareKeyboardController.current

    val vm = viewModel<SpliceOutViewModel>(factory = SpliceOutViewModel.Factory(business.peerManager, business.chain))
    var amount by remember { mutableStateOf(requestedAmount) }
    var amountErrorMessage by remember { mutableStateOf("") }
    val balance = business.balanceManager.balance.collectAsState(null).value

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(bottom = 50.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BackButtonWithBalance(onBackClick = onBackClick, balance = balance)
        Spacer(Modifier.height(16.dp))
        Card(
            externalPadding = PaddingValues(horizontal = 16.dp),
            internalPadding = PaddingValues(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            AmountHeroInput(
                initialAmount = amount?.toMilliSatoshi(),
                onAmountChange = {
                    amountErrorMessage = ""
                    val newAmount = it?.amount?.truncateToSatoshi()
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
            Column(
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 32.dp, start = 16.dp, end = 16.dp)
                    .sizeIn(maxWidth = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label(text = stringResource(R.string.send_destination_label)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhoenixIcon(resourceId = R.drawable.ic_chain, modifier = Modifier.size(18.dp), tint = MaterialTheme.colors.primary)
                        Spacer(Modifier.width(4.dp))
                        SelectionContainer {
                            Text(text = address, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = vm.state) {
            is SpliceOutState.Init, is SpliceOutState.Error -> {

                if (state is SpliceOutState.Error) {
                    ErrorMessage(errorHeader = "!! error !!", errorDetails = state.e.localizedMessage)
                }

                BorderButton(
                    text = stringResource(id = R.string.send_spliceout_prepare_button),
                    icon = R.drawable.ic_build,
                    enabled = amountErrorMessage.isBlank(),
                ) {
                    amount?.let {
                        keyboardManager?.hide()
                        vm.prepareSpliceOut(it, 20.sat, address)
                    } ?: run {
                        amountErrorMessage = context.getString(R.string.send_error_amount_invalid)
                    }
                }
            }
            is SpliceOutState.Preparing -> {
                ProgressView(text = stringResource(id = R.string.send_spliceout_prepare_in_progress))
            }
            is SpliceOutState.ReadyToSend -> {
                val total = state.userAmount + state.feeExpected
                val chain = business.chain
                val peerManager = business.peerManager

                SpliceOutFeeSummaryView(userAmount = state.userAmount, fee = state.feeExpected, total = total)
                Spacer(modifier = Modifier.height(24.dp))
                if (balance != null && total.toMilliSatoshi() > balance) {
                    ErrorMessage(errorHeader = stringResource(R.string.send_spliceout_error_cannot_afford_fees))
                } else {
                    FilledButton(
                        text = stringResource(id = R.string.send_pay_button),
                        icon = R.drawable.ic_send,
                        enabled = amountErrorMessage.isBlank()
                    ) {
                        vm.executeSpliceOut(state.userAmount, 20.sat, address)
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
    userAmount: Satoshi,
    fee: Satoshi,
    total: Satoshi,
) {
    Card(
        internalPadding = PaddingValues(16.dp),
        modifier = Modifier.widthIn(max = 300.dp),
        withBorder = true,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FeeSummary(label = stringResource(id = R.string.send_spliceout_complete_recap_amount), amount = userAmount.toMilliSatoshi())
        FeeSummary(label = stringResource(id = R.string.send_spliceout_complete_recap_fee), amount = fee.toMilliSatoshi())
        FeeSummary(label = stringResource(id = R.string.send_spliceout_complete_recap_total), amount = total.toMilliSatoshi())
    }
}

@Composable
private fun FeeSummary(
    label: String,
    amount: MilliSatoshi
) {
    Row(
        modifier = Modifier.widthIn(max = 500.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.body1.copy(color = mutedTextColor(), fontSize = 12.sp),
            textAlign = TextAlign.End,
            modifier = Modifier
                .alignBy(FirstBaseline)
                .weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .alignBy(FirstBaseline)
                .weight(2f)
        ) {
            AmountWithFiatView(amount = amount, amountTextStyle = MaterialTheme.typography.body2)
        }
    }
}
