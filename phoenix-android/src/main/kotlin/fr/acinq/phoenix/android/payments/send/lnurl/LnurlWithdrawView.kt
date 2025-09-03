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

package fr.acinq.phoenix.android.payments.send.lnurl

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.buttons.BorderButton
import fr.acinq.phoenix.android.components.buttons.FilledButton
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.inputs.AmountHeroInput
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.SplashLabelRow
import fr.acinq.phoenix.android.components.layouts.SplashLayout
import fr.acinq.phoenix.android.primaryFiatRate
import fr.acinq.phoenix.android.payments.receive.EvaluateLiquidityIssuesForPayment
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyStringWithFallback
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.extensions.toLocalisedMessage
import fr.acinq.phoenix.data.lnurl.LnurlWithdraw
import fr.acinq.phoenix.managers.SendManager

@Composable
fun LnurlWithdrawView(
    withdraw: LnurlWithdraw,
    onBackClick: () -> Unit,
    onFeeManagementClick: () -> Unit,
    onWithdrawDone: () -> Unit,
) {
    val context = LocalContext.current
    val prefUnit = preferredAmountUnit
    val rate = primaryFiatRate

    val maxWithdrawable = withdraw.maxWithdrawable
    var amount by remember { mutableStateOf<MilliSatoshi?>(maxWithdrawable) }
    var amountErrorMessage by remember { mutableStateOf("") }

    val vm = viewModel<LnurlWithdrawViewModel>(factory = LnurlWithdrawViewModel.Factory(business.sendManager))
    val withdrawState = vm.state.value

    val isAmountDisabled = remember(withdraw, withdrawState) { withdraw.minWithdrawable == withdraw.maxWithdrawable || withdrawState is LnurlWithdrawViewState.SendingInvoice }

    SplashLayout(
        header = { DefaultScreenHeader(onBackClick = onBackClick) },
        topContent = {
            Text(text = annotatedStringResource(R.string.lnurl_withdraw_header, withdraw.initialUrl.host), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            AmountHeroInput(
                initialAmount = maxWithdrawable,
                onAmountChange = { newAmount ->
                    amountErrorMessage = ""
                    when {
                        newAmount == null -> {}
                        newAmount.amount < withdraw.minWithdrawable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_withdraw_amount_below_min, withdraw.minWithdrawable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                        newAmount.amount > withdraw.maxWithdrawable -> {
                            amountErrorMessage = context.getString(R.string.lnurl_withdraw_amount_above_max, withdraw.maxWithdrawable.toPrettyStringWithFallback(prefUnit, rate, withUnit = true))
                        }
                    }
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
                enabled = !isAmountDisabled,
            )
        }
    ) {
        SplashLabelRow(label = stringResource(R.string.lnurl_pay_meta_description)) {
            Text(text = withdraw.defaultDescription)
        }
        Spacer(Modifier.height(32.dp))
        when (withdrawState) {
            is LnurlWithdrawViewState.Init, is LnurlWithdrawViewState.Error -> {
                if (withdrawState is LnurlWithdrawViewState.Error) {
                    ErrorMessage(
                        header = stringResource(id = R.string.lnurl_withdraw_error_header),
                        details = when (withdrawState) {
                            is LnurlWithdrawViewState.Error.WithdrawError -> when (val error = withdrawState.error) {
                                is SendManager.LnurlWithdrawError.RemoteError -> error.err.toLocalisedMessage()
                            }
                            is LnurlWithdrawViewState.Error.Generic -> withdrawState.cause.localizedMessage ?: withdrawState.cause::class.java.simpleName
                        },
                        alignment = Alignment.CenterHorizontally,
                    )
                }

                val mayDoPayments by business.peerManager.mayDoPayments.collectAsState()
                FilledButton(
                    text = if (!mayDoPayments) stringResource(id = R.string.send_connecting_button) else stringResource(id = R.string.lnurl_withdraw_confirm_button),
                    icon = R.drawable.ic_receive,
                    enabled = mayDoPayments && amount != null && amountErrorMessage.isBlank(),
                    onClick = { amount?.let { vm.sendInvoice(withdraw, amount = it) } }
                )

                EvaluateLiquidityIssuesForPayment(
                    amount = amount,
                    onFeeManagementClick = onFeeManagementClick,
                    showDialogImmediately = true,
                    onDialogShown = {},
                )
            }
            is LnurlWithdrawViewState.SendingInvoice -> {
                ProgressView(text = stringResource(id = R.string.lnurl_withdraw_wait))
            }
            is LnurlWithdrawViewState.InvoiceSent -> {
                Text(
                    text = annotatedStringResource(id = R.string.lnurl_withdraw_success, withdraw.callback.host),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                BorderButton(text = stringResource(id = R.string.btn_ok), icon = R.drawable.ic_check_circle, onClick = onWithdrawDone)
            }
        }
    }
}
