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

package fr.acinq.phoenix.android.send

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountInput
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.Scan

@Composable
fun SendView(request: PaymentRequest?) {
    requireWalletPresent(inScreen = Screen.Send) {
        val log = logger()
        log.info { "init sendview amount=${request?.amount} desc=${request?.description}" }
        MVIView(CF::scan) { model, postIntent ->
            val nc = navController
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var amount by remember { mutableStateOf(request?.amount ?: MilliSatoshi(0)) }

                Spacer(modifier = Modifier.height(80.dp))
                AmountInput(
                    initialAmount = amount,
                    onAmountChange = { a, fiat, fiatUnit -> amount = a ?: MilliSatoshi(0) },
                    useBasicInput = true,
                    inputTextSize = 48.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                FilledButton(
                    text = R.string.send_pay_button,
                    icon = R.drawable.ic_send,
                    onClick = {
                        if (request != null) {
                            postIntent(Scan.Intent.Send(request.write(), amount))
                            nc.navigate(Screen.Home)
                        }
                    })
            }
        }
    }
}