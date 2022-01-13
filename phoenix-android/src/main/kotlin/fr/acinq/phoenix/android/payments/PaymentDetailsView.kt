/*
 * Copyright 2021 ACINQ SAS
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.PrimarySeparator
import fr.acinq.phoenix.android.components.SettingHeader
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.managers.PaymentsManager
import fr.acinq.phoenix.utils.errorMessage
import org.slf4j.LoggerFactory


sealed class PaymentDetailsState {
    object Loading : PaymentDetailsState()
    data class Success(val payment: WalletPaymentInfo) : PaymentDetailsState()
    data class Failure(val error: Throwable) : PaymentDetailsState()
}

@Composable
fun PaymentDetailsView(
    direction: Long, id: String,
    onBackClick: () -> Unit
) {
    val vm: PaymentDetailsViewModel = viewModel(factory = PaymentDetailsViewModel.Factory(business.paymentsManager))
    val paymentState = produceState<PaymentDetailsState>(initialValue = PaymentDetailsState.Loading) {
        value = WalletPaymentId.create(direction, id)?.let {
            vm.getPayment(it)
        } ?: PaymentDetailsState.Failure(IllegalArgumentException("unhandled payment id: direction=$direction id=$id"))
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        SettingHeader(onBackClick = onBackClick, backgroundColor = Color.Unspecified)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = paymentState.value) {
                is PaymentDetailsState.Loading -> {
                    Card {
                        Text(stringResource(id = R.string.paymentdetails_loading))
                    }
                }
                is PaymentDetailsState.Success -> PaymentDetailsInfo(state.payment)
                is PaymentDetailsState.Failure -> {
                    Card {
                        Text(stringResource(id = R.string.paymentdetails_error, state.error.message ?: stringResource(id = R.string.utils_unknown)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentDetailsInfo(
    data: WalletPaymentInfo
) {
    val payment = data.payment

    // status
    Spacer(modifier = Modifier.height(48.dp))
    DetailsInfoHeader(payment)
    Spacer(modifier = Modifier.height(48.dp))

    // details
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colors.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AmountView(
            amount = payment.amount,
            amountTextStyle = MaterialTheme.typography.body1.copy(fontSize = 24.sp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimarySeparator(height = 6.dp)
        Spacer(modifier = Modifier.height(48.dp))

        if (payment is OutgoingPayment) {
            DetailsRow(
                label = stringResource(id = R.string.paymentdetails_destination_label),
                value = when (val details = payment.details) {
                    is OutgoingPayment.Details.Normal -> details.paymentRequest.nodeId.toString()
                    is OutgoingPayment.Details.ChannelClosing -> details.closingAddress
                    is OutgoingPayment.Details.KeySend -> null
                    is OutgoingPayment.Details.SwapOut -> details.address
                }
            )
        }

        DetailsRow(
            label = stringResource(id = R.string.paymentdetails_desc_label),
            value = when (payment) {
                is OutgoingPayment -> when (val details = payment.details) {
                    is OutgoingPayment.Details.Normal -> details.paymentRequest.description ?: details.paymentRequest.descriptionHash?.toHex()
                    is OutgoingPayment.Details.ChannelClosing -> "Closing channel ${details.channelId}"
                    is OutgoingPayment.Details.KeySend -> "Donation"
                    is OutgoingPayment.Details.SwapOut -> "Swap to a Bitcoin address"
                }
                is IncomingPayment -> when (val origin = payment.origin) {
                    is IncomingPayment.Origin.Invoice -> origin.paymentRequest.description ?: origin.paymentRequest.descriptionHash?.toHex()
                    is IncomingPayment.Origin.KeySend -> "Spontaneous payment"
                    is IncomingPayment.Origin.SwapIn -> "On-chain swap deposit"
                }
                else -> null
            },
            fallbackValue = stringResource(id = R.string.paymentdetails_no_description)
        )

        payment.errorMessage()?.let { errorMessage ->
            DetailsRow(
                label = stringResource(id = R.string.paymentdetails_error_label),
                value = errorMessage
            )
        }
    }
}

@Composable
private fun DetailsInfoHeader(
    payment: WalletPayment
) {
    when (payment) {
        is OutgoingPayment -> when (payment.status) {
            is OutgoingPayment.Status.Pending -> PaymentStateIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_pending),
                imageResId = R.drawable.ic_payment_details_pending_static,
                color = mutedTextColor()
            )
            is OutgoingPayment.Status.Completed.Failed -> PaymentStateIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_failed),
                imageResId = R.drawable.ic_payment_details_failure_static,
                color = negativeColor()
            )
            is OutgoingPayment.Status.Completed.Succeeded -> PaymentStateIcon(
                message = stringResource(id = R.string.paymentdetails_status_sent_successful),
                imageResId = R.drawable.ic_payment_details_success_static,
                color = positiveColor(),
                timestamp = payment.completedAt()
            )
        }
        is IncomingPayment -> when (payment.received) {
            null -> PaymentStateIcon(
                message = stringResource(id = R.string.paymentdetails_status_received_pending),
                imageResId = R.drawable.ic_payment_details_pending_static,
                color = mutedTextColor()
            )
            else -> PaymentStateIcon(
                message = stringResource(id = R.string.paymentdetails_status_received_successful),
                imageResId = R.drawable.ic_payment_details_success_static,
                color = positiveColor(),
                timestamp = payment.received?.receivedAt
            )
        }
    }
}

@Composable
private fun PaymentStateIcon(
    message: String,
    timestamp: Long? = null,
    imageResId: Int,
    color: Color,
) {
    Image(
        painter = painterResource(id = imageResId),
        contentDescription = message,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(80.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(text = message.uppercase(), style = MaterialTheme.typography.body2)
    timestamp?.let {
        Text(text = timestamp.toRelativeDateString(), style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun DetailsRow(label: String, value: String?, fallbackValue: String = stringResource(id = R.string.utils_unknown)) {
    Row {
        Text(text = label, style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.End), modifier = Modifier.width(100.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = value ?: fallbackValue,
            style = MaterialTheme.typography.body1.copy(fontStyle = if (value == null) FontStyle.Italic else FontStyle.Normal),
            modifier = Modifier.width(300.dp)
        )
    }
}

private class PaymentDetailsViewModel(
    val paymentsManager: PaymentsManager
) : ViewModel() {

    val log = LoggerFactory.getLogger(this::class.java)

    suspend fun getPayment(id: WalletPaymentId): PaymentDetailsState {
        log.info("getting payment details for id=$id")
        return paymentsManager.getPayment(id, WalletPaymentFetchOptions.All)?.let {
            PaymentDetailsState.Success(it)
        } ?: PaymentDetailsState.Failure(NoSuchElementException("no payment found for id=$id"))
    }

    class Factory(
        private val paymentsManager: PaymentsManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PaymentDetailsViewModel(paymentsManager) as T
        }
    }
}
