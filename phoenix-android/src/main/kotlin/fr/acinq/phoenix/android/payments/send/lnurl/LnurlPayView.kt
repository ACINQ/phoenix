/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.send.lnurl

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.inputs.AmountHeroInput
import fr.acinq.phoenix.android.components.buttons.BackButtonWithActiveWallet
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.dialogs.Dialog
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.layouts.SplashClickableContent
import fr.acinq.phoenix.android.components.layouts.SplashLabelRow
import fr.acinq.phoenix.android.components.layouts.SplashLayout
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.buttons.SmartSpendButton
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.primaryFiatRate
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyStringWithFallback
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.extensions.safeLet
import fr.acinq.phoenix.android.utils.extensions.toLocalisedMessage
import fr.acinq.phoenix.android.utils.images.ImageDecoder
import fr.acinq.phoenix.data.lnurl.LnurlError
import fr.acinq.phoenix.managers.SendManager

@Composable
fun LnurlPayView(
    walletId: WalletId,
    business: PhoenixBusiness,
    pay: SendManager.ParseResult.Lnurl.Pay,
    onBackClick: () -> Unit,
    onPaymentSent: () -> Unit,
) {
    val context = LocalContext.current
    val balance = business.balanceManager.balance.collectAsState(null).value
    val prefUnit = preferredAmountUnit
    val rate = primaryFiatRate

    val peer by business.peerManager.peerState.collectAsState()
    val trampolineFees = peer?.walletParams?.trampolineFees?.firstOrNull()

    val payIntent = pay.paymentIntent
    val minRequestedAmount = payIntent.minSendable
    var amount by remember { mutableStateOf<MilliSatoshi?>(minRequestedAmount) }
    var amountErrorMessage by remember { mutableStateOf("") }

    val vm = viewModel<LnurlPayViewModel>(factory = LnurlPayViewModel.Factory(business.sendManager))

    SplashLayout(
        header = { BackButtonWithActiveWallet(onBackClick = onBackClick, walletId = walletId) },
        topContent = {
            AmountHeroInput(
                initialAmount = minRequestedAmount,
                onAmountChange = { newAmount ->
                    amountErrorMessage = ""
                    when {
                        newAmount == null -> {}
                        balance != null && newAmount.amount > balance -> {
                            amountErrorMessage = context.getString(R.string.send_error_amount_over_balance)
                        }
                        newAmount.amount < payIntent.minSendable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_pay_amount_below_min, payIntent.minSendable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                        newAmount.amount > payIntent.maxSendable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_pay_amount_above_max, payIntent.maxSendable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                    }
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
                enabled = payIntent.minSendable != payIntent.maxSendable
            )
        }
    ) {
        val image = remember(payIntent.metadata.imagePng + payIntent.metadata.imageJpg) {
            listOfNotNull(payIntent.metadata.imagePng, payIntent.metadata.imageJpg).firstOrNull()?.let {
                ImageDecoder.decodeBase64Image(it)?.asImageBitmap()
            }
        }
        image?.let {
            Image(bitmap = it, contentDescription = payIntent.metadata.plainText, modifier = Modifier.size(90.dp))
            Spacer(modifier = Modifier.height(16.dp))
        }
        SplashLabelRow(label = stringResource(R.string.lnurl_pay_domain)) {
            Text(text = payIntent.callback.host, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(label = stringResource(R.string.lnurl_pay_meta_description)) {
            Text(
                text = payIntent.metadata.longDesc ?: payIntent.metadata.plainText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        var comment by remember { mutableStateOf<String?>(null) }
        val commentLength = payIntent.maxCommentLength?.toInt()
        if (commentLength != null && commentLength > 0) {
            var showCommentDialog by remember { mutableStateOf(false) }
            if (showCommentDialog) {
                EditCommentDialog(
                    comment = comment,
                    maxLength = commentLength,
                    onDismiss = { showCommentDialog = false },
                    onCommentSubmit = {
                        comment = it
                        showCommentDialog = false
                    },
                )
            }
            SplashLabelRow(label = stringResource(id = R.string.paymentdetails_lnurlpay_action_message_label)) {
                SplashClickableContent(onClick = { showCommentDialog = true }) {
                    Text(
                        text = comment ?: stringResource(id = R.string.lnurl_pay_comment_add_button),
                        style = if (comment == null) MaterialTheme.typography.caption else MaterialTheme.typography.body1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        when (val state = vm.state.value) {
            is LnurlPayViewState.Init, is LnurlPayViewState.Error -> {
                if (state is LnurlPayViewState.Error) {
                    ErrorMessage(
                        header = stringResource(id = R.string.lnurl_pay_error_header),
                        details = when (state) {
                            is LnurlPayViewState.Error.Generic -> state.cause.localizedMessage
                            is LnurlPayViewState.Error.PayError -> when (val error = state.error) {
                                is SendManager.LnurlPayError.PaymentPending -> annotatedStringResource(R.string.lnurl_pay_error_payment_pending, payIntent.callback.host)
                                is SendManager.LnurlPayError.AlreadyPaidInvoice -> annotatedStringResource(R.string.lnurl_pay_error_already_paid, payIntent.callback.host)
                                is SendManager.LnurlPayError.ChainMismatch -> annotatedStringResource(R.string.lnurl_pay_error_invalid_chain, payIntent.callback.host)
                                is SendManager.LnurlPayError.BadResponseError -> when (val errorDetail = error.err) {
                                    is LnurlError.Pay.Invoice.InvalidAmount -> annotatedStringResource(R.string.lnurl_pay_error_invalid_amount, errorDetail.origin)
                                    is LnurlError.Pay.Invoice.Malformed -> annotatedStringResource(R.string.lnurl_pay_error_invalid_malformed, errorDetail.origin)
                                }
                                is SendManager.LnurlPayError.RemoteError -> error.err.toLocalisedMessage()
                            }
                        },
                        alignment = Alignment.CenterHorizontally
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                SmartSpendButton(
                    walletId = walletId,
                    enabled = amount != null && amountErrorMessage.isBlank() && trampolineFees != null,
                    onSpend = {
                        safeLet(trampolineFees, amount) { fees, amt ->
                            vm.requestAndPayInvoice(pay, amt, fees, comment?.takeIf { it.isNotBlank() }, onPaymentSent)
                        }
                    }
                )
            }
            is LnurlPayViewState.RequestingInvoice -> {
                ProgressView(text = stringResource(id = R.string.lnurl_pay_requesting_invoice))
            }
            is LnurlPayViewState.PayingInvoice -> {
                ProgressView(text = stringResource(id = R.string.lnurl_pay_paying_invoice))
            }
        }
    }
}

@Composable
private fun EditCommentDialog(
    comment: String?,
    maxLength: Int,
    onDismiss: () -> Unit,
    onCommentSubmit: (String?) -> Unit,
) {
    var input by remember { mutableStateOf(comment ?: "") }
    Dialog(onDismiss = onDismiss, buttons = {
        Button(
            onClick = { onCommentSubmit(input.takeIf { it.isNotBlank() }) },
            text = stringResource(id = R.string.btn_ok)
        )
    }) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = stringResource(id = R.string.lnurl_pay_comment_instructions))
            Spacer(modifier = Modifier.height(16.dp))
            TextInput(
                text = input,
                onTextChange = { input = it },
                maxChars = maxLength,
                staticLabel = stringResource(id = R.string.lnurl_pay_comment_label),
            )
        }
    }
}
