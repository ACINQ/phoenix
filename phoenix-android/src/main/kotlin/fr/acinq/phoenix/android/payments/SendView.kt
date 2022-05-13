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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountInput
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.payments.MaxFees
import fr.acinq.phoenix.controllers.payments.Scan

@Composable
fun SendView(request: PaymentRequest?) {
    val log = logger("SendView")
    log.info { "init sendview amount=${request?.amount} desc=${request?.description}" }

    val trampolineMaxFees by UserPrefs.getTrampolineMaxFee(LocalContext.current).collectAsState(null)

    MVIView(CF::scan) { model, postIntent ->
        val nc = navController
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var amount by remember { mutableStateOf(request?.amount) }
            Spacer(modifier = Modifier.height(80.dp))
            AmountInput(
                initialAmount = amount,
                onAmountChange = { msat, fiat, fiatUnit ->
                    amount = msat
                },
                useBasicInput = true,
                inputTextSize = 48.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledButton(
                text = R.string.send_pay_button,
                icon = R.drawable.ic_send,
                enabled = amount != null,
            ) {
                val finalAmount = amount
                if (request != null && finalAmount != null) {
                    val maxFees = trampolineMaxFees?.takeUnless { it.feeBase < 0.sat || it.feeProportional < 0 } ?.let { MaxFees(it.feeBase, it.feeProportional) }

                    postIntent(Scan.Intent.InvoiceFlow.SendInvoicePayment(paymentRequest = request, amount = finalAmount, maxFees = maxFees))
                    nc.navigate(Screen.Home)
                }
            }
        }
    }
}