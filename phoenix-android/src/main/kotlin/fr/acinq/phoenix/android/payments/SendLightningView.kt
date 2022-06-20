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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.mutedItalicTypo
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.controllers.payments.MaxFees
import fr.acinq.phoenix.controllers.payments.Scan


@Composable
fun SendLightningPaymentView(
    paymentRequest: PaymentRequest,
    trampolineMaxFees: MaxFees?,
    onBackClick: () -> Unit,
    onPayClick: (Scan.Intent.InvoiceFlow.SendInvoicePayment) -> Unit
) {
    val log = logger("SendLightningPaymentView")
    log.info { "init sendview amount=${paymentRequest.amount} desc=${paymentRequest.description}" }

    val context = LocalContext.current
    val balance = business.peerManager.balance.collectAsState(null).value

    val requestedAmount = paymentRequest.amount
    var amount by remember { mutableStateOf(requestedAmount) }
    var amountErrorMessage by remember { mutableStateOf("") }

    BackButtonWithBalance(onBackClick = onBackClick, balance = balance)
    Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(110.dp))
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
                        amountErrorMessage = context.getString(R.string.send_error_amount_below_requested)
                    }
                    requestedAmount != null && newAmount.amount > requestedAmount * 2 -> {
                        amountErrorMessage = context.getString(R.string.send_error_amount_overpaying)
                    }
                }
                amount = newAmount?.amount
            },
            inputTextSize = 48.sp,
        )
        Text(amountErrorMessage, style = MaterialTheme.typography.body1.copy(color = negativeColor(), fontSize = 14.sp), maxLines = 1)
        Spacer(modifier = Modifier.height(24.dp))

        SendBasicDetailsRow(label = stringResource(R.string.send_destination_label)) {
            Text(text = paymentRequest.nodeId.toHex(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(8.dp))
        SendBasicDetailsRow(label = stringResource(R.string.send_description_label)) {
            val description = (paymentRequest.description ?: paymentRequest.descriptionHash?.toHex())?.takeIf { it.isNotBlank() }
            Text(text = description ?: stringResource(id = R.string.paymentdetails_no_description), style = if (description != null) MaterialTheme.typography.body1 else mutedItalicTypo())
        }
        Spacer(modifier = Modifier.height(24.dp))
        FilledButton(
            text = R.string.send_pay_button,
            icon = R.drawable.ic_send,
            enabled = amount != null && amountErrorMessage.isBlank(),
        ) {
            amount?.let {
                onPayClick(Scan.Intent.InvoiceFlow.SendInvoicePayment(paymentRequest = paymentRequest, amount = it, maxFees = trampolineMaxFees))
            }
        }
    }
}

@Composable
fun SendBasicDetailsRow(
    label: String,
    labelWeight: Float = 1f,
    contentWeight: Float = 3f,
    content: @Composable () -> Unit
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
                .weight(labelWeight)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .alignBy(FirstBaseline)
                .weight(contentWeight)
        ) {
            content()
        }
    }
}

@Composable
fun BackButtonWithBalance(
    onBackClick: () -> Unit,
    balance: MilliSatoshi?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BackButton(onClick = onBackClick)
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(id = R.string.send_balance_prefix).uppercase(),
                style = MaterialTheme.typography.body1.copy(color = mutedTextColor(), fontSize = 12.sp),
                modifier = Modifier.alignBy(FirstBaseline)
            )
            Spacer(modifier = Modifier.width(4.dp))
            balance?.let {
                AmountView(amount = it, modifier = Modifier.alignBy(FirstBaseline))
            }
        }
    }
}