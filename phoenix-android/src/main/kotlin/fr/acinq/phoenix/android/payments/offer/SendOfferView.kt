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

package fr.acinq.phoenix.android.payments.offer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountHeroInput
import fr.acinq.phoenix.android.components.AmountWithFiatRowView
import fr.acinq.phoenix.android.components.BackButtonWithBalance
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.SplashLayout
import fr.acinq.phoenix.android.components.contact.ContactOrOfferView
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.payments.details.translatePaymentError
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.Converter.toPrettyString

@Composable
fun SendOfferView(
    offer: OfferTypes.Offer,
    trampolineFees: TrampolineFees?,
    onBackClick: () -> Unit,
    onPaymentSent: () -> Unit,
) {
    val context = LocalContext.current
    val balance = business.balanceManager.balance.collectAsState(null).value
    val prefBitcoinUnit = LocalBitcoinUnit.current

    val vm = viewModel<SendOfferViewModel>(factory = SendOfferViewModel.Factory(business.peerManager))
    val requestedAmount = offer.amount
    var amount by remember { mutableStateOf(requestedAmount) }
    val amountErrorMessage: String = remember(amount) {
        val currentAmount = amount
        when {
            currentAmount == null -> ""
            balance != null && currentAmount > balance -> context.getString(R.string.send_error_amount_over_balance)
            requestedAmount != null && currentAmount < requestedAmount -> context.getString(
                R.string.send_error_amount_below_requested,
                (requestedAmount).toPrettyString(prefBitcoinUnit, withUnit = true)
            )

            requestedAmount != null && currentAmount > requestedAmount * 2 -> context.getString(
                R.string.send_error_amount_overpaying,
                (requestedAmount * 2).toPrettyString(prefBitcoinUnit, withUnit = true)
            )

            else -> ""
        }
    }
    val isOverpaymentEnabled by userPrefs.getIsOverpaymentEnabled.collectAsState(initial = false)

    SplashLayout(
        header = { BackButtonWithBalance(onBackClick = onBackClick, balance = balance) },
        topContent = {
            AmountHeroInput(
                initialAmount = requestedAmount,
                enabled = (requestedAmount == null || isOverpaymentEnabled) && vm.state !is OfferState.FetchingInvoice,
                onAmountChange = { newAmount ->
                    if (newAmount?.amount != amount) vm.state = OfferState.Init
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
            )
        }
    ) {
        offer.description?.let {
            SplashLabelRow(label = stringResource(R.string.send_description_label)) {
                Text(text = it)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        SplashLabelRow(label = stringResource(id = R.string.send_destination_label)) {
            ContactOrOfferView(offer = offer)
        }

        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(label = stringResource(id = R.string.send_trampoline_fee_label)) {
            val amt = amount
            if (amt == null) {
                Text(stringResource(id = R.string.send_trampoline_fee_no_amount), style = MaterialTheme.typography.caption)
            } else if (trampolineFees == null) {
                Text(stringResource(id = R.string.send_trampoline_fee_loading))
            } else {
                AmountWithFiatRowView(amount = trampolineFees.calculateFees(amt))
            }
        }
        Spacer(modifier = Modifier.height(36.dp))

        SendOfferStateButton(
            state = vm.state,
            offer = offer,
            amount = amount,
            isAmountInError = amountErrorMessage.isNotBlank(),
            onSendClick = { amount, offer -> vm.sendOffer(amount, offer) },
            onPaymentSent = onPaymentSent,
        )
    }
}

@Composable
private fun SendOfferStateButton(
    state: OfferState,
    offer: OfferTypes.Offer,
    amount: MilliSatoshi?,
    isAmountInError: Boolean,
    onSendClick: (MilliSatoshi, OfferTypes.Offer) -> Unit,
    onPaymentSent: () -> Unit,
) {
    when (state) {
        is OfferState.Init, is OfferState.Complete.Failed -> {
            if (state is OfferState.Complete.Failed) {
                ErrorMessage(
                    header = stringResource(id = R.string.send_offer_failure_title),
                    details = when (state) {
                        is OfferState.Complete.Failed.CouldNotGetInvoice -> stringResource(id = R.string.send_offer_failure_timeout)
                        is OfferState.Complete.Failed.PaymentNotSent -> translatePaymentError(paymentFailure = state.reason)
                        is OfferState.Complete.Failed.Error -> state.throwable.message
                    },
                    alignment = Alignment.CenterHorizontally,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            val mayDoPayments by business.peerManager.mayDoPayments.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledButton(
                    text = if (!mayDoPayments) {
                        stringResource(id = R.string.send_connecting_button)
                    } else if (state is OfferState.Complete.Failed) {
                        stringResource(id = R.string.send_pay_retry_button)
                    } else {
                        stringResource(id = R.string.send_pay_button)
                    },
                    icon = R.drawable.ic_send,
                    enabled = mayDoPayments && amount != null && !isAmountInError,
                ) {
                    amount?.let { onSendClick(it, offer) }
                }
            }
        }

        is OfferState.FetchingInvoice -> {
            ProgressView(text = stringResource(id = R.string.send_offer_fetching))
        }

        is OfferState.Complete.SendingOffer -> {
            LaunchedEffect(key1 = Unit) { onPaymentSent() }
        }
    }
}
