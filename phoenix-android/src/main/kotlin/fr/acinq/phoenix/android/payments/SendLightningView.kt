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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.utils.extensions.isAmountlessTrampoline

@Composable
fun SendLightningPaymentView(
    paymentRequest: PaymentRequest,
    trampolineFees: TrampolineFees?,
    onBackClick: () -> Unit,
    onPayClick: (Scan.Intent.InvoiceFlow.SendInvoicePayment) -> Unit
) {
    val log = logger("SendLightningPaymentView")
    log.debug { "init sendview amount=${paymentRequest.amount} desc=${paymentRequest.description}" }

    val context = LocalContext.current
    val balance = business.balanceManager.balance.collectAsState(null).value
    val prefBitcoinUnit = LocalBitcoinUnit.current

    val requestedAmount = paymentRequest.amount
    var amount by remember { mutableStateOf(requestedAmount) }
    var amountErrorMessage by remember { mutableStateOf("") }

    SplashLayout(
        header = { BackButtonWithBalance(onBackClick = onBackClick, balance = balance) },
        topContent = {
            AmountHeroInput(
                initialAmount = amount,
                onAmountChange = { newAmount ->
                    amountErrorMessage = ""
                    when {
                        newAmount == null -> {}
                        balance != null && newAmount.amount > balance -> {
                            amountErrorMessage = context.getString(R.string.send_error_amount_over_balance)
                        }
                        requestedAmount != null && newAmount.amount < requestedAmount -> {
                            amountErrorMessage = context.getString(
                                R.string.send_error_amount_below_requested,
                                (requestedAmount).toPrettyString(prefBitcoinUnit, withUnit = true)
                            )
                        }
                        requestedAmount != null && newAmount.amount > requestedAmount * 2 -> {
                            amountErrorMessage = context.getString(
                                R.string.send_error_amount_overpaying,
                                (requestedAmount * 2).toPrettyString(prefBitcoinUnit, withUnit = true)
                            )
                        }
                    }
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
            )
        }
    ) {
        paymentRequest.description?.takeIf { it.isNotBlank() }?.let {
            SplashLabelRow(label = stringResource(R.string.send_description_label)) {
                Text(text = it)
            }
        }
        SplashLabelRow(label = stringResource(R.string.send_destination_label), icon = R.drawable.ic_zap) {
            SelectionContainer {
                Text(text = paymentRequest.nodeId.toHex(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (paymentRequest.isAmountlessTrampoline()) {
            SplashLabelRow(label = "", helpMessage = stringResource(id = R.string.send_trampoline_amountless_warning_details)) {
                Text(text = stringResource(id = R.string.send_trampoline_amountless_warning_label))
            }
        }
        SplashLabelRow(label = stringResource(id = R.string.send_trampoline_fee_label)) {
            val amt = amount
            val fees = trampolineFees
            if (amt == null) {
                Text(stringResource(id = R.string.send_trampoline_fee_no_amount), style = MaterialTheme.typography.caption)
            } else if (fees == null) {
                Text(stringResource(id = R.string.send_trampoline_fee_loading))
            } else {
                AmountWithFiatRowView(amount = fees.calculateFees(amt))
            }
        }
        Spacer(modifier = Modifier.height(36.dp))
        val mayDoPayments by business.peerManager.mayDoPayments.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledButton(
                text = if (!mayDoPayments) stringResource(id = R.string.send_connecting_button) else stringResource(id = R.string.send_pay_button),
                icon = R.drawable.ic_send,
                enabled = mayDoPayments && amount != null && amountErrorMessage.isBlank() && trampolineFees != null,
            ) {
                safeLet(amount, trampolineFees) { amt, fees ->
                    onPayClick(Scan.Intent.InvoiceFlow.SendInvoicePayment(paymentRequest = paymentRequest, amount = amt, trampolineFees = fees))
                }
            }
        }
    }
}
