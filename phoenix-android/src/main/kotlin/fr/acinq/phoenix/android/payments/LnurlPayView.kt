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
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.controllers.payments.MaxFees
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.LNUrl

@Composable
fun LnurlPayView(
    model: Scan.Model.LnurlPayFlow,
    trampolineMaxFees: MaxFees?,
    onBackClick: () -> Unit,
    onSendLnurlPayClick: (Scan.Intent.LnurlPayFlow) -> Unit
) {
    val log = logger("SendLightningPaymentView")
    log.info { "init lnurl-pay view with url=${model.lnurlPay}" }

    val context = LocalContext.current
    val balance = business.peerManager.balance.collectAsState(null).value
    val prefUnit = preferredAmountUnit
    val rate = fiatRate

    var amount by remember { mutableStateOf<MilliSatoshi?>(model.lnurlPay.minSendable) }
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
                        newAmount.amount < model.lnurlPay.minSendable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_pay_amount_below_min, model.lnurlPay.minSendable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                        newAmount.amount > model.lnurlPay.maxSendable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_pay_amount_above_max, model.lnurlPay.maxSendable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                    }
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
                enabled = model.lnurlPay.minSendable != model.lnurlPay.maxSendable
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val image = remember(model.lnurlPay.metadata.imagePng + model.lnurlPay.metadata.imageJpg) {
                    listOfNotNull(model.lnurlPay.metadata.imagePng, model.lnurlPay.metadata.imageJpg).firstOrNull()?.let {
                        BitmapHelper.decodeBase64Image(it)?.asImageBitmap()
                    }
                }
                image?.let {
                    Image(bitmap = it, contentDescription = model.lnurlPay.metadata.plainText, modifier = Modifier.size(90.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Label(label = stringResource(R.string.lnurl_pay_meta_description)) {
                    Text(text = model.lnurlPay.metadata.longDesc ?: model.lnurlPay.metadata.plainText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(24.dp))
                Label(label = stringResource(R.string.lnurl_pay_domain)) {
                    Text(text = model.lnurlPay.callback.host, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        when (model) {
            is Scan.Model.LnurlPayFlow.LnurlPayRequest -> {
                val error = model.error
                if (error != null) {
                    RemoteErrorResponseView(model.lnurlPay, error)
                }
                FilledButton(
                    text = R.string.lnurl_pay_pay_button,
                    icon = R.drawable.ic_send,
                    enabled = amount != null && amountErrorMessage.isBlank(),
                ) {
                    amount?.let {
                        onSendLnurlPayClick(
                            Scan.Intent.LnurlPayFlow.SendLnurlPayment(
                                lnurlPay = model.lnurlPay,
                                amount = it,
                                maxFees = trampolineMaxFees,
                                comment = "lorem ipsum"
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
    lnUrlPay: LNUrl.Pay,
    error: Scan.LnurlPayError
) {
    Text(
        text = stringResource(R.string.lnurl_pay_error_header) + "\n" + when (error) {
            is Scan.LnurlPayError.AlreadyPaidInvoice -> stringResource(R.string.lnurl_pay_error_already_paid, lnUrlPay.callback.host)
            is Scan.LnurlPayError.ChainMismatch -> stringResource(R.string.lnurl_pay_error_invalid_chain, lnUrlPay.callback.host)
            is Scan.LnurlPayError.BadResponseError -> when (val errorDetail = error.err) {
                is LNUrl.Error.PayInvoice.InvalidAmount -> stringResource(R.string.lnurl_pay_error_invalid_amount, errorDetail.origin)
                is LNUrl.Error.PayInvoice.InvalidHash -> stringResource(R.string.lnurl_pay_error_invalid_hash, errorDetail.origin)
                is LNUrl.Error.PayInvoice.Malformed -> stringResource(R.string.lnurl_pay_error_invalid_malformed, errorDetail.origin)
            }
            is Scan.LnurlPayError.RemoteError -> when (val errorDetail = error.err) {
                is LNUrl.Error.RemoteFailure.CouldNotConnect -> stringResource(R.string.lnurl_pay_error_remote_connection, errorDetail.origin)
                is LNUrl.Error.RemoteFailure.Code -> stringResource(R.string.lnurl_pay_error_remote_code, errorDetail.origin, errorDetail.code)
                is LNUrl.Error.RemoteFailure.Detailed -> stringResource(R.string.lnurl_pay_error_remote_details, errorDetail.origin, errorDetail.reason)
                is LNUrl.Error.RemoteFailure.Unreadable -> stringResource(R.string.lnurl_pay_error_remote_unreadable, errorDetail.origin)
            }
        },
        style = MaterialTheme.typography.body1.copy(color = negativeColor(), textAlign = TextAlign.Center),
        modifier = Modifier.padding(horizontal = 48.dp)
    )
    Spacer(Modifier.height(24.dp))
}

