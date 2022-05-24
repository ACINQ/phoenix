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
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountInput
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.payments.Scan


@Composable
fun SendSwapOutView(
    model: Scan.Model.SwapOutFlow,
    onInvalidate: (Scan.Intent.SwapOutFlow.Invalidate) -> Unit,
    onPrepareSwapOutClick: (Scan.Intent.SwapOutFlow.PrepareSwapOut) -> Unit,
    onSendSwapOutClick: (Scan.Intent.SwapOutFlow.SendSwapOut) -> Unit,
) {
    val log = logger("SendSwapOutView")
    log.info { "init swapout amount=${model.address.amount?.toMilliSatoshi()} to=${model.address.address}" }

    val address = model.address.address
    var amount by remember { mutableStateOf(model.address.amount) }

    Column(
        modifier = Modifier
            .padding(32.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        SwapOutInputView(
            initialAmount = amount,
            onAmountChange = {
                if (model !is Scan.Model.SwapOutFlow.Init && amount != it) {
                    onInvalidate(Scan.Intent.SwapOutFlow.Invalidate(model.address)) // we must start over again
                }
                amount = it
            },
        )

        when (model) {
            is Scan.Model.SwapOutFlow.Init -> {
                FilledButton(
                    text = R.string.send_swap_required_button,
                    icon = R.drawable.ic_build,
                    enabled = amount != null,
                ) {
                    amount?.let {
                        onPrepareSwapOutClick(
                            Scan.Intent.SwapOutFlow.PrepareSwapOut(
                                address = model.address,
                                amount = it
                            )
                        )
                    }
                }
            }
            is Scan.Model.SwapOutFlow.RequestingSwapout -> {
                Text(stringResource(id = R.string.send_swap_in_progress))
            }
            is Scan.Model.SwapOutFlow.SwapOutReady -> {
                val total = model.paymentRequest.amount?.truncateToSatoshi()
                if (total == null || total == 0.sat || total != model.initialUserAmount + model.fee) {
                    onInvalidate(Scan.Intent.SwapOutFlow.Invalidate(model.address))
                } else {
                    SwapOutFeeSummaryView(userAmount = model.initialUserAmount, fee = model.fee, total = total)
                    FilledButton(
                        text = R.string.send_pay_button,
                        icon = R.drawable.ic_send,
                    ) {
                        onSendSwapOutClick(
                            Scan.Intent.SwapOutFlow.SendSwapOut(
                                amount = total,
                                paymentRequest = model.paymentRequest,
                                maxFees = null,
                                address = model.address
                            )
                        )
                    }
                }
            }
            is Scan.Model.SwapOutFlow.SendingSwapOut -> {
                Text("sending swap-out!")
            }
        }
    }
}

@Composable
private fun SwapOutInputView(
    initialAmount: Satoshi?,
    onAmountChange: (Satoshi) -> Unit
) {

    Spacer(modifier = Modifier.height(80.dp))
    AmountInput(
        initialAmount = initialAmount?.toMilliSatoshi(),
        onAmountChange = { newAmount, _, _ ->
            newAmount?.let { onAmountChange(it.truncateToSatoshi()) }
        },
        useBasicInput = true,
        inputTextSize = 48.sp,
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun SwapOutFeeSummaryView(
    userAmount: Satoshi,
    fee: Satoshi,
    total: Satoshi,
) {
    Text(text = stringResource(id = R.string.send_swap_complete_recap_amount))
    Spacer(modifier = Modifier.height(8.dp))
    AmountView(amount = userAmount.toMilliSatoshi())

    Spacer(modifier = Modifier.height(16.dp))

    Text(text = stringResource(id = R.string.send_swap_complete_recap_fee))
    Spacer(modifier = Modifier.height(8.dp))
    AmountView(amount = fee.toMilliSatoshi())

    Spacer(modifier = Modifier.height(16.dp))

    Text(text = stringResource(id = R.string.send_swap_complete_recap_total))
    Spacer(modifier = Modifier.height(8.dp))
    AmountView(amount = total.toMilliSatoshi())
}