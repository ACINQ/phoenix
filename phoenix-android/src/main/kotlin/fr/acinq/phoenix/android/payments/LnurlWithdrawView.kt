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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.payments.receive.EvaluateLiquidityIssuesForPayment
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.utils.Converter.toPrettyStringWithFallback
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.lnurl.LnurlError

@Composable
fun LnurlWithdrawView(
    model: Scan.Model.LnurlWithdrawFlow,
    onWithdrawClick: (Scan.Intent.LnurlWithdrawFlow) -> Unit,
    onFeeManagementClick: () -> Unit,
    onWithdrawDone: () -> Unit,
) {
    val context = LocalContext.current
    val prefUnit = preferredAmountUnit
    val rate = fiatRate

    val maxWithdrawable = model.lnurlWithdraw.maxWithdrawable
    var amount by remember { mutableStateOf<MilliSatoshi?>(maxWithdrawable) }
    var amountErrorMessage by remember { mutableStateOf("") }

    SplashLayout(
        header = { DefaultScreenHeader(onBackClick = onWithdrawDone) },
        topContent = {
            Text(text = annotatedStringResource(R.string.lnurl_withdraw_header, model.lnurlWithdraw.initialUrl.host), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            AmountHeroInput(
                initialAmount = maxWithdrawable,
                onAmountChange = { newAmount ->
                    amountErrorMessage = ""
                    when {
                        newAmount == null -> {}
                        newAmount.amount < model.lnurlWithdraw.minWithdrawable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_withdraw_amount_below_min, model.lnurlWithdraw.minWithdrawable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                        newAmount.amount > model.lnurlWithdraw.maxWithdrawable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_withdraw_amount_above_max, model.lnurlWithdraw.maxWithdrawable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                    }
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
                enabled = model.lnurlWithdraw.minWithdrawable != model.lnurlWithdraw.maxWithdrawable
                        && model is Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest,
            )
        }
    ) {
        SplashLabelRow(label = stringResource(R.string.lnurl_pay_meta_description)) {
            Text(text = model.lnurlWithdraw.defaultDescription)
        }
        Spacer(Modifier.height(32.dp))
        when (model) {
            is Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest -> {
                val error = model.error
                if (error != null && error is Scan.LnurlWithdrawError.RemoteError) {
                    ErrorMessage(
                        header = stringResource(id = R.string.lnurl_withdraw_error_header),
                        details = getRemoteErrorMessage(error = error.err),
                        alignment = Alignment.CenterHorizontally,
                    )
                }

                val mayDoPayments by business.peerManager.mayDoPayments.collectAsState()
                FilledButton(
                    text = if (!mayDoPayments) stringResource(id = R.string.send_connecting_button) else stringResource(id = R.string.lnurl_withdraw_confirm_button),
                    icon = R.drawable.ic_receive,
                    enabled = mayDoPayments && amount != null && amountErrorMessage.isBlank(),
                ) {
                    amount?.let {
                        onWithdrawClick(
                            Scan.Intent.LnurlWithdrawFlow.SendLnurlWithdraw(
                                lnurlWithdraw = model.lnurlWithdraw,
                                amount = it,
                                description = model.lnurlWithdraw.defaultDescription
                            )
                        )
                    }
                }

                EvaluateLiquidityIssuesForPayment(
                    amount = amount,
                    onFeeManagementClick = onFeeManagementClick,
                    showDialogImmediately = true,
                    onDialogShown = {},
                )
            }
            is Scan.Model.LnurlWithdrawFlow.LnurlWithdrawFetch -> {
                ProgressView(text = stringResource(id = R.string.lnurl_withdraw_wait))
            }
            is Scan.Model.LnurlWithdrawFlow.Receiving -> {
                Text(
                    text = annotatedStringResource(id = R.string.lnurl_withdraw_success, model.lnurlWithdraw.callback.host),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                BorderButton(text = stringResource(id = R.string.btn_ok), icon = R.drawable.ic_check_circle, onClick = onWithdrawDone)
            }
        }
    }
}

@Composable
fun getRemoteErrorMessage(
    error: LnurlError.RemoteFailure
): AnnotatedString {
    return when (error) {
        is LnurlError.RemoteFailure.Code -> annotatedStringResource(id = R.string.lnurl_error_remote_code, error.origin, error.code.value.toString())
        is LnurlError.RemoteFailure.CouldNotConnect -> annotatedStringResource(id = R.string.lnurl_error_remote_connection, error.origin)
        is LnurlError.RemoteFailure.Detailed -> annotatedStringResource(id = R.string.lnurl_error_remote_details, error.origin, error.reason)
        is LnurlError.RemoteFailure.Unreadable -> annotatedStringResource(id = R.string.lnurl_error_remote_unreadable, error.origin)
        is LnurlError.RemoteFailure.IsWebsite -> TODO()
        is LnurlError.RemoteFailure.LightningAddressError -> TODO()
    }
}
