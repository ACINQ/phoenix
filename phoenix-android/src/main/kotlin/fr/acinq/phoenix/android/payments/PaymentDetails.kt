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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.BackButton
import fr.acinq.phoenix.android.components.PrimarySeparator
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.managers.PaymentsManager
import fr.acinq.phoenix.utils.WalletPaymentState
import fr.acinq.phoenix.utils.errorMessage
import fr.acinq.phoenix.utils.state
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
    val detailsState by produceState<PaymentDetailsState>(initialValue = PaymentDetailsState.Loading) {
        value = WalletPaymentId.create(direction, id)?.let {
            vm.getPayment(it)
        } ?: PaymentDetailsState.Failure(IllegalArgumentException("unhandled payment id: direction=$direction id=$id"))
    }

    when (val state = detailsState) {
        is PaymentDetailsState.Loading -> Text("Loading payment details...")
        is PaymentDetailsState.Success -> PaymentDetailsInfo(state.payment, onBackClick)
        is PaymentDetailsState.Failure -> Text("Failed to get payment details (${state.error.message})")
    }
}

@Composable
private fun PaymentDetailsInfo(
    data: WalletPaymentInfo,
    onBackClick: () -> Unit
) {
    val payment = data.payment
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        BackButton(onBackClick)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            when (payment.state()) {
                WalletPaymentState.Pending -> IconState(
                    message = "Payment is pending",
                    imageResId = R.drawable.ic_payment_success_large_static,
                    color = mutedTextColor()
                )
                WalletPaymentState.Failure -> IconState(
                    message = "Payment has failed",
                    imageResId = R.drawable.ic_payment_failed,
                    color = negativeColor()
                )
                WalletPaymentState.Success -> {
                    val message = when (data.payment) {
                        is OutgoingPayment -> "sent ${data.payment.completedAt().toRelativeDateString()}"
                        is IncomingPayment -> "received ${data.payment.completedAt().toRelativeDateString()}"
                    }
                    IconState(
                        message = message,
                        imageResId = R.drawable.ic_payment_success_large_static,
                        color = positiveColor()
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
            AmountView(
                amount = payment.amount,
                amountTextStyle = MaterialTheme.typography.body1.copy(fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            PrimarySeparator()
            Spacer(modifier = Modifier.height(48.dp))

            if (payment is OutgoingPayment) {
                DetailsRow(
                    label = "Destination", value = when (val details = payment.details) {
                        is OutgoingPayment.Details.Normal -> details.paymentRequest.nodeId.toString()
                        is OutgoingPayment.Details.ChannelClosing -> details.closingAddress
                        is OutgoingPayment.Details.KeySend -> null
                        is OutgoingPayment.Details.SwapOut -> details.address
                    }
                )
            }

            DetailsRow(
                label = "Description", value = when (payment) {
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
                }, fallbackValue = stringResource(id = R.string.paymentdetails_no_description)
            )

            val errorMessage = payment.errorMessage()
            if (errorMessage != null) {
                DetailsRow(label = "Error details", value = errorMessage)
            }
        }
    }
}

@Composable
private fun IconState(
    message: String,
    imageResId: Int,
    color: Color,
) {
    Image(
        painter = painterResource(id = imageResId),
        contentDescription = message,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(50.dp)
    )
    Spacer(Modifier.height(8.dp))
    Text(text = message)
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
