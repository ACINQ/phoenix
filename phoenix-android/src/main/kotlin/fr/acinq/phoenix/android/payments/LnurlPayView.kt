/*
 * Copyright 2022 ACINQ SAS
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountHeroInput
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.utils.BitmapHelper
import fr.acinq.phoenix.android.utils.Converter.toPrettyStringWithFallback
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.controllers.payments.MaxFees
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.lnurl.LnurlError
import fr.acinq.phoenix.data.lnurl.LnurlPay

@Composable
fun LnurlPayView(
    model: Scan.Model.LnurlPayFlow,
    trampolineMaxFees: MaxFees?,
    onBackClick: () -> Unit,
    onSendLnurlPayClick: (Scan.Intent.LnurlPayFlow) -> Unit
) {
    val log = logger("SendLightningPaymentView")
    log.info { "init lnurl-pay view with url=${model.paymentIntent}" }

    val context = LocalContext.current
    val balance = business.balanceManager.balance.collectAsState(null).value
    val prefUnit = preferredAmountUnit
    val rate = fiatRate

    var amount by remember { mutableStateOf<MilliSatoshi?>(model.paymentIntent.minSendable) }
    var amountErrorMessage by remember { mutableStateOf("") }

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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            AmountHeroInput(
                initialAmount = amount,
                onAmountChange = { newAmount ->
                    amountErrorMessage = ""
                    when {
                        newAmount == null -> {}
                        balance != null && newAmount.amount > balance -> {
                            amountErrorMessage = context.getString(R.string.send_error_amount_over_balance)
                        }
                        newAmount.amount < model.paymentIntent.minSendable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_pay_amount_below_min, model.paymentIntent.minSendable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                        newAmount.amount > model.paymentIntent.maxSendable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_pay_amount_above_max, model.paymentIntent.maxSendable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                    }
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
                enabled = model.paymentIntent.minSendable != model.paymentIntent.maxSendable
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val image = remember(model.paymentIntent.metadata.imagePng + model.paymentIntent.metadata.imageJpg) {
                    listOfNotNull(model.paymentIntent.metadata.imagePng, model.paymentIntent.metadata.imageJpg).firstOrNull()?.let {
                        BitmapHelper.decodeBase64Image(it)?.asImageBitmap()
                    }
                }
                image?.let {
                    Image(bitmap = it, contentDescription = model.paymentIntent.metadata.plainText, modifier = Modifier.size(90.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Label(text = stringResource(R.string.lnurl_pay_meta_description)) {
                    Text(text = model.paymentIntent.metadata.longDesc ?: model.paymentIntent.metadata.plainText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(24.dp))
                Label(text = stringResource(R.string.lnurl_pay_domain)) {
                    Text(text = model.paymentIntent.callback.host, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        when (model) {
            is Scan.Model.LnurlPayFlow.LnurlPayRequest -> {
                val error = model.error
                if (error != null) {
                    RemoteErrorResponseView(model.paymentIntent, error)
                }
                FilledButton(
                    text = stringResource(id = R.string.lnurl_pay_pay_button),
                    icon = R.drawable.ic_send,
                    enabled = amount != null && amountErrorMessage.isBlank(),
                ) {
                    amount?.let {
                        onSendLnurlPayClick(
                            Scan.Intent.LnurlPayFlow.RequestInvoice(
                                paymentIntent = model.paymentIntent,
                                amount = it,
                                maxFees = trampolineMaxFees,
                                comment = null
                            )
                        )
                    }
                }
            }
            is Scan.Model.LnurlPayFlow.LnurlPayFetch -> {
                Card(
                    externalPadding = PaddingValues(0.dp),
                    internalPadding = PaddingValues(16.dp)
                ) {
                    Text(text = stringResource(id = R.string.lnurl_pay_requesting_invoice))
                }
            }
            is Scan.Model.LnurlPayFlow.Sending -> LaunchedEffect(Unit) { onBackClick() }
        }
    }
}

@Composable
private fun RemoteErrorResponseView(
    lnurlPay: LnurlPay.Intent,
    error: Scan.LnurlPayError
) {
    Text(
        text = stringResource(R.string.lnurl_pay_error_header) + "\n" + when (error) {
            is Scan.LnurlPayError.AlreadyPaidInvoice -> annotatedStringResource(R.string.lnurl_pay_error_already_paid, lnurlPay.callback.host)
            is Scan.LnurlPayError.ChainMismatch -> annotatedStringResource(R.string.lnurl_pay_error_invalid_chain, lnurlPay.callback.host)
            is Scan.LnurlPayError.BadResponseError -> when (val errorDetail = error.err) {
                is LnurlError.Pay.Invoice.InvalidAmount -> annotatedStringResource(R.string.lnurl_pay_error_invalid_amount, errorDetail.origin)
                is LnurlError.Pay.Invoice.InvalidHash -> annotatedStringResource(R.string.lnurl_pay_error_invalid_hash, errorDetail.origin)
                is LnurlError.Pay.Invoice.Malformed -> annotatedStringResource(R.string.lnurl_pay_error_invalid_malformed, errorDetail.origin)
            }
            is Scan.LnurlPayError.RemoteError -> getRemoteErrorMessage(error = error.err)
        },
        style = MaterialTheme.typography.body1.copy(color = negativeColor, textAlign = TextAlign.Center),
        modifier = Modifier.padding(horizontal = 48.dp)
    )
    Spacer(Modifier.height(24.dp))
}

