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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.TrampolineFees
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.utils.BitmapHelper
import fr.acinq.phoenix.android.utils.Converter.toPrettyStringWithFallback
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.lnurl.LnurlError

@Composable
fun LnurlPayView(
    model: Scan.Model.LnurlPayFlow,
    trampolineFees: TrampolineFees?,
    onBackClick: () -> Unit,
    onSendLnurlPayClick: (Scan.Intent.LnurlPayFlow) -> Unit
) {
    val context = LocalContext.current
    val balance = business.balanceManager.balance.collectAsState(null).value
    val prefUnit = preferredAmountUnit
    val rate = fiatRate

    var amount by remember { mutableStateOf<MilliSatoshi?>(model.paymentIntent.minSendable) }
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
        }
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
        SplashLabelRow(label = stringResource(R.string.lnurl_pay_domain)) {
            Text(text = model.paymentIntent.callback.host, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SplashLabelRow(label = stringResource(R.string.lnurl_pay_meta_description)) {
            Text(
                text = model.paymentIntent.metadata.longDesc ?: model.paymentIntent.metadata.plainText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        var comment by remember { mutableStateOf<String?>(null) }
        val commentLength = model.paymentIntent.maxCommentLength?.toInt()
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
            comment?.let {
                SplashLabelRow(label = "", icon = R.drawable.ic_message_circle, iconTint = MaterialTheme.colors.primary) {
                    Clickable(onClick = { showCommentDialog = true }) {
                        Text(text = it)
                    }
                }
            } ?: run {
                SplashLabelRow(label = "", icon = R.drawable.ic_message_circle, iconTint = MaterialTheme.colors.primary) {
                    Button(
                        text = stringResource(id = R.string.lnurl_pay_comment_add_button),
                        onClick = { showCommentDialog = true },
                        padding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        when (model) {
            is Scan.Model.LnurlPayFlow.LnurlPayRequest -> {
                val error = model.error
                if (error != null) {
                    ErrorMessage(
                        header = stringResource(id = R.string.lnurl_pay_error_header),
                        details = when (error) {
                            is Scan.LnurlPayError.AlreadyPaidInvoice -> annotatedStringResource(R.string.lnurl_pay_error_already_paid, model.paymentIntent.callback.host)
                            is Scan.LnurlPayError.ChainMismatch -> annotatedStringResource(R.string.lnurl_pay_error_invalid_chain, model.paymentIntent.callback.host)
                            is Scan.LnurlPayError.BadResponseError -> when (val errorDetail = error.err) {
                                is LnurlError.Pay.Invoice.InvalidAmount -> annotatedStringResource(R.string.lnurl_pay_error_invalid_amount, errorDetail.origin)
                                is LnurlError.Pay.Invoice.Malformed -> annotatedStringResource(R.string.lnurl_pay_error_invalid_malformed, errorDetail.origin)
                            }
                            is Scan.LnurlPayError.RemoteError -> getRemoteErrorMessage(error = error.err)
                        },
                        alignment = Alignment.CenterHorizontally
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val mayDoPayments by business.peerManager.mayDoPayments.collectAsState()
                FilledButton(
                    text = if (!mayDoPayments) stringResource(id = R.string.send_connecting_button) else stringResource(id = R.string.lnurl_pay_pay_button),
                    icon = R.drawable.ic_send,
                    enabled = mayDoPayments && amount != null && amountErrorMessage.isBlank() && trampolineFees != null,
                ) {
                    safeLet(trampolineFees, amount) { fees, amt ->
                        onSendLnurlPayClick(
                            Scan.Intent.LnurlPayFlow.RequestInvoice(
                                paymentIntent = model.paymentIntent,
                                amount = amt,
                                trampolineFees = fees,
                                comment = comment?.takeIf { it.isNotBlank() },
                            )
                        )
                    }
                }
            }
            is Scan.Model.LnurlPayFlow.LnurlPayFetch -> {
                ProgressView(text = stringResource(id = R.string.lnurl_pay_requesting_invoice))
            }
            is Scan.Model.LnurlPayFlow.Sending -> LaunchedEffect(Unit) { onBackClick() }
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
