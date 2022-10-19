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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.fiatRate
import fr.acinq.phoenix.android.preferredAmountUnit
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.LNUrl
import fr.acinq.phoenix.android.utils.Converter.toPrettyStringWithFallback
import fr.acinq.phoenix.android.utils.annotatedStringResource

@Composable
fun LnurlWithdrawView(
    model: Scan.Model.LnurlWithdrawFlow,
    onBackClick: () -> Unit,
    onWithdrawClick: (Scan.Intent.LnurlWithdrawFlow) -> Unit
) {
    val log = logger("LnurlWithdrawView")
    log.info { "init lnurl-withdraw view with url=${model.lnurlWithdraw}" }

    val context = LocalContext.current
    val prefUnit = preferredAmountUnit
    val rate = fiatRate

    var amount by remember { mutableStateOf<MilliSatoshi?>(model.lnurlWithdraw.minWithdrawable) }
    var amountErrorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(bottom = 50.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DefaultScreenHeader(onBackClick = onBackClick)
        Spacer(Modifier.height(16.dp))

        when (model) {
            is Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest, is Scan.Model.LnurlWithdrawFlow.LnurlWithdrawFetch -> {

                // withdraw details
                Card(
                    externalPadding = PaddingValues(horizontal = 16.dp),
                    internalPadding = PaddingValues(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = annotatedStringResource(R.string.lnurl_withdraw_header, model.lnurlWithdraw.lnurl.host), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    AmountHeroInput(
                        initialAmount = amount,
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
                    )
                    Spacer(Modifier.height(24.dp))
                    Label(text = stringResource(R.string.lnurl_pay_meta_description)) {
                        Text(text = model.lnurlWithdraw.defaultDescription)
                    }
                }
                Spacer(Modifier.height(32.dp))

                // withdraw button, progress feedback, error messages
                if (model is Scan.Model.LnurlWithdrawFlow.LnurlWithdrawRequest) {
                    val error = model.error
                    if (error != null && error is Scan.LnurlWithdrawError.RemoteError) {
                        RemoteErrorResponseView(error.err)
                    }
                    FilledButton(
                        text = R.string.lnurl_withdraw_confirm_button,
                        icon = R.drawable.ic_receive,
                        enabled = amount != null && amountErrorMessage.isBlank(),
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
                } else {
                    ProgressView(text = stringResource(id = R.string.lnurl_withdraw_wait))
                }

            }
            is Scan.Model.LnurlWithdrawFlow.Receiving -> {
                Card(externalPadding = PaddingValues(horizontal = 16.dp), internalPadding = PaddingValues(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = annotatedStringResource(id = R.string.lnurl_withdraw_success, model.lnurlWithdraw.callback.host))
                    Spacer(Modifier.height(12.dp))
                    BorderButton(text = stringResource(id = R.string.btn_ok), icon = R.drawable.ic_check_circle, onClick = onBackClick)
                }
            }
        }
    }
}

@Composable
private fun RemoteErrorResponseView(
    error: LNUrl.Error.RemoteFailure
) {
    Text(
        text = stringResource(R.string.lnurl_withdraw_error_header) + "\n" + getRemoteErrorMessage(error = error),
        style = MaterialTheme.typography.body1.copy(color = negativeColor(), textAlign = TextAlign.Center),
        modifier = Modifier.padding(horizontal = 48.dp)
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
fun getRemoteErrorMessage(
    error: LNUrl.Error.RemoteFailure
): String {
    return when (error) {
        is LNUrl.Error.RemoteFailure.Code -> stringResource(id = R.string.lnurl_error_remote_code, error.origin, error.code)
        is LNUrl.Error.RemoteFailure.CouldNotConnect -> stringResource(id = R.string.lnurl_error_remote_code, error.origin)
        is LNUrl.Error.RemoteFailure.Detailed -> stringResource(id = R.string.lnurl_error_remote_details, error.origin, error.reason)
        is LNUrl.Error.RemoteFailure.Unreadable -> stringResource(id = R.string.lnurl_error_remote_unreadable, error.origin)
    }
}

