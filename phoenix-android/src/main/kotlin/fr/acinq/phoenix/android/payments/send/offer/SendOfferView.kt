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

package fr.acinq.phoenix.android.payments.send.offer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.inputs.AmountHeroInput
import fr.acinq.phoenix.android.components.AmountWithFiatRowView
import fr.acinq.phoenix.android.components.BackButtonWithBalance
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.SplashLayout
import fr.acinq.phoenix.android.components.inputs.TextInput
import fr.acinq.phoenix.android.components.buttons.SmartSpendButton
import fr.acinq.phoenix.android.components.contact.ContactOrOfferView
import fr.acinq.phoenix.android.components.dialogs.ModalBottomSheet
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.payments.details.splash.translatePaymentError
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString

@Composable
fun SendToOfferView(
    offer: OfferTypes.Offer,
    onBackClick: () -> Unit,
    onPaymentSent: () -> Unit,
) {
    val context = LocalContext.current
    val prefBitcoinUnit = LocalBitcoinUnits.current.primary
    val keyboardManager = LocalSoftwareKeyboardController.current

    val balance = business.balanceManager.balance.collectAsState(null).value
    val peer by business.peerManager.peerState.collectAsState()
    val trampolineFees = peer?.walletParams?.trampolineFees?.firstOrNull()

    val vm = viewModel<SendOfferViewModel>(factory = SendOfferViewModel.Factory(offer, business.peerManager, business.nodeParamsManager, business.databaseManager), key = offer.encode())
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
            else -> ""
        }
    }

    var message by remember { mutableStateOf("") }
    var showMessageDialog by remember { mutableStateOf(false) }

    SplashLayout(
        header = { BackButtonWithBalance(onBackClick = onBackClick, balance = balance) },
        topContent = {
            AmountHeroInput(
                initialAmount = requestedAmount,
                enabled = vm.state !is OfferState.FetchingInvoice,
                onAmountChange = { newAmount ->
                    if (newAmount?.amount != amount) vm.state = OfferState.Init
                    amount = newAmount?.amount
                },
                validationErrorMessage = amountErrorMessage,
                inputTextSize = 42.sp,
            )
        }
    ) {
        offer.description?.takeIf { it.isNotBlank() }?.let {
            SplashLabelRow(label = stringResource(R.string.send_description_label)) {
                Text(text = it)
            }
        }

        SplashLabelRow(label = stringResource(id = R.string.send_destination_label)) {
            ContactOrOfferView(offer = offer)
        }

        SplashLabelRow(label = stringResource(id = R.string.send_offer_payer_note_label)) {
            Clickable(
                onClick = { showMessageDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = (-8).dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    message.takeIf { it.isNotBlank() }?.let {
                        Text(text = it)
                    } ?: Text(text = stringResource(id = R.string.send_offer_payer_note_placeholder), style = MaterialTheme.typography.caption)
                }
            }
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
            onSendClick = { amount, offer -> vm.sendOffer(amount, message, offer) },
            onPaymentSent = onPaymentSent,
        )
    }

    if (showMessageDialog) {
        PayerNoteInput(initialMessage = message, onMessageChange = { message = it }, onDismiss = { showMessageDialog = false ; keyboardManager?.hide() })
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
                        is OfferState.Complete.Failed.PayerNoteTooLong -> "The message is too long (max. 64 chars)"
                    },
                    alignment = Alignment.CenterHorizontally,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            SmartSpendButton(
                enabled = amount != null && !isAmountInError,
                text = if (state is OfferState.Complete.Failed) {
                    stringResource(id = R.string.send_pay_retry_button)
                } else {
                    stringResource(id = R.string.send_pay_button)
                },
                onSpend = {
                    amount?.let { onSendClick(it, offer) }
                },
            )
        }

        is OfferState.FetchingInvoice -> {
            ProgressView(text = stringResource(id = R.string.send_offer_fetching))
        }

        is OfferState.Complete.SendingOffer -> {
            LaunchedEffect(key1 = Unit) { onPaymentSent() }
        }
    }
}

@Composable
private fun PayerNoteInput(
    initialMessage: String,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismiss = onDismiss,
        skipPartiallyExpanded = true,
        internalPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Text(text = stringResource(id = R.string.send_offer_payer_note_dialog_title), style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(id = R.string.send_offer_payer_note_dialog_subtitle), style = MaterialTheme.typography.caption, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        TextInput(
            text = initialMessage,
            staticLabel = null,
            onTextChange = onMessageChange,
            placeholder = { Text(text = stringResource(id = R.string.send_offer_payer_note_dialog_placeholder) )},
            maxChars = 64,
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilledButton(text = stringResource(id = R.string.btn_ok), icon = R.drawable.ic_check, onClick = onDismiss, modifier = Modifier.align(Alignment.End))
        Spacer(modifier = Modifier.height(80.dp))
    }
}
