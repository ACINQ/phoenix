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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.SettingHeader
import fr.acinq.phoenix.android.components.SettingScreen
import fr.acinq.phoenix.android.utils.Converter.toFiat
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.managers.PaymentsManager
import fr.acinq.phoenix.utils.createdAt
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.NoSuchElementException

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentMoreDetailsView(
    direction:Long,
    id: String
) {
    val log = logger("PaymentMoreDetailsView")
    val nc = navController
    val context = LocalContext.current
    val rate = fiatRate
    val prefFiat = LocalFiatCurrency.current

    val vm: PaymentMoreDetailsViewModel = viewModel(factory = PaymentMoreDetailsViewModel.Factory(business.paymentsManager))
    val paymentState = produceState<PaymentDetailsState>(initialValue = PaymentDetailsState.Loading) {
        value = WalletPaymentId.create(direction, id)?.let {
            vm.getPayment(it)
        } ?: PaymentDetailsState.Failure(IllegalArgumentException("unhandled payment id: direction=$direction id=$id"))
    }

    SettingScreen {
        SettingHeader(
            onBackClick = { nc.popBackStack() },
            title = stringResource(id = R.string.paymentdetails_title),
            subtitle = stringResource(id = R.string.paymentdetails_subtitle)
        )

        when (val state = paymentState.value) {
            is PaymentDetailsState.Loading -> Card {
                Text(stringResource(id = R.string.paymentdetails_loading))
            }
            is PaymentDetailsState.Failure -> Card {
                Text(stringResource(id = R.string.paymentdetails_error, state.error.message ?: stringResource(id = R.string.utils_unknown)))
            }
            is PaymentDetailsState.Success -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val payment = state.payment.payment

                Card(internalPadding = PaddingValues(16.dp)) {

                    Row {
                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(id = R.string.paymentdetails_amount_sent),
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(
                            Modifier.weight(3f)
                        ) {
                            Text(text = payment.amount.toPrettyString(BitcoinUnit.Sat, withUnit = true))
                        }
                    }

                    rate?.let {
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_current_rate),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                val fiatAmount = payment.amount.toFiat(rate.price).toPrettyString(prefFiat, withUnit = true)
                                Text(text = stringResource(id = R.string.utils_converted_amount, fiatAmount))
                            }
                        }
                    }

                    payment.fees.takeUnless { it <= 0.msat }?.let {

                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_fees_paid),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                Text(text = it.truncateToSatoshi().toString() )
                            }
                        }

                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(id = R.string.paymentdetails_sent_at),
                                style = MaterialTheme.typography.subtitle2
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(
                            Modifier.weight(3f)
                        ) {
                            Text(text = Date(payment.createdAt).toString())
                        }
                    }
                }

                Card(internalPadding = PaddingValues(16.dp)) {

                    when (payment) {
                        is OutgoingPayment -> when (val details = payment.details) {
                            is OutgoingPayment.Details.Normal -> details.paymentRequest.amount
                            else -> null
                        }
                        is IncomingPayment -> when (val origin = payment.origin) {
                            is IncomingPayment.Origin.Invoice -> origin.paymentRequest.amount
                            else -> null
                        }
                        else -> null
                    }?.let {
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_amount_requested),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                //Text(text = it.truncateToSatoshi().toString())
                                Text(text = payment.amount.toPrettyString(BitcoinUnit.Sat, withUnit = true))
                            }
                        }
                    }

                    /*
                    when (payment) {
                        is OutgoingPayment -> payment.recipientAmount
                        else -> null
                    }?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_amount_received),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                Text(text = it.truncateToSatoshi().toString())
                            }
                        }
                    }
                    */
                    when (payment) {
                        is OutgoingPayment -> when (val details = payment.details) {
                            is OutgoingPayment.Details.Normal -> details.paymentRequest.timestampSeconds
                            else -> null
                        }
                        is IncomingPayment -> when (val origin = payment.origin) {
                            is IncomingPayment.Origin.Invoice -> origin.paymentRequest.timestampSeconds
                            else -> null
                        }
                        else -> null
                    }?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_invoice_created),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                val date = Date(it * 1000)
                                Text(text = date.toString())
                            }
                        }
                    }
                    when (payment) {
                        is OutgoingPayment -> payment.recipient
                        else -> null
                    }?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_recipient_pubkey),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                Text(text = it.toString())
                            }
                        }
                    }
                }

                Card(internalPadding = PaddingValues(16.dp)) {

                    when (payment) {
                        is OutgoingPayment -> when (val status = payment.status) {
                            is OutgoingPayment.Status.Completed.Succeeded.OffChain -> status.completedAt
                            is OutgoingPayment.Status.Completed.Succeeded.OnChain -> status.completedAt
                            else -> null
                        }
                        else -> null
                    }?.let {
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_elapsed),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                Text (stringResource(id = R.string.paymentdetails_elapsed_ms, it-payment.createdAt))
                                //Text(text = (it-payment.createdAt).toString())
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    when (payment) {
                        is OutgoingPayment -> when (val details = payment.details) {
                            is OutgoingPayment.Details.Normal -> details.paymentRequest.paymentHash.toHex()
                            else -> null
                        }
                        is IncomingPayment -> when (val origin = payment.origin) {
                            is IncomingPayment.Origin.Invoice -> origin.paymentRequest.paymentHash.toHex()
                            else -> null
                        }
                        else -> null
                    }?.let {
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_payment_hash),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                Text(text = it)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    when (payment) {
                        is OutgoingPayment -> when (val status = payment.status) {
                            is OutgoingPayment.Status.Completed.Succeeded.OffChain -> status.preimage
                            else -> null
                        }
                        else -> null
                    }?.let {
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_preimage_label),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                Text (it.toHex())
                                //Text(text = (it-payment.createdAt).toString())
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }


                    when (payment) {
                        is OutgoingPayment -> when (val details = payment.details) {
                            is OutgoingPayment.Details.Normal -> details.paymentRequest.write()
                            else -> null
                        }
                        is IncomingPayment -> when (val origin = payment.origin) {
                            is IncomingPayment.Origin.Invoice -> origin.paymentRequest.write()
                            else -> null
                        }
                        else -> null
                    }?.let {
                        Row {
                            Column(
                                Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.paymentdetails_payment_request_label),
                                    style = MaterialTheme.typography.subtitle2
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(
                                Modifier.weight(3f)
                            ) {
                                Text(text = it)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class PaymentMoreDetailsViewModel(
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
            return PaymentMoreDetailsViewModel(paymentsManager) as T
        }
    }
}

/*
@Preview(device = Devices.PIXEL_3A)
@Composable
private fun Preview() {
    PaymentMoreDetailsView()
}
*/