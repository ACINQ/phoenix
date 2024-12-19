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

package fr.acinq.phoenix.android.payments.details.technical

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.payments.details.OutgoingAmountSection
import fr.acinq.phoenix.android.payments.details.TechnicalCard
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TechnicalRowSelectable
import fr.acinq.phoenix.android.payments.details.TechnicalRowWithCopy
import fr.acinq.phoenix.data.ExchangeRate

@Composable
fun TechnicalOutgoingLightning(
    payment: LightningOutgoingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    TechnicalCard {
        OutgoingAmountSection(payment = payment, originalFiatRate = originalFiatRate)

        val details = payment.details
        val status = payment.status

        // -- details of the payment
        when (details) {
            is LightningOutgoingPayment.Details.Normal -> {
                TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_pubkey_label), value = payment.recipient.toHex())
                Bolt11InvoiceSection(invoice = details.paymentRequest)
            }
            is LightningOutgoingPayment.Details.SwapOut -> {
                TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_bitcoin_address_label), value = details.address)
                TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = details.paymentHash.toHex())
            }
            is LightningOutgoingPayment.Details.Blinded -> {
                Bolt12InvoiceSection(invoice = details.paymentRequest, payerKey = details.payerKey)
            }
        }

        // -- status details
        when (status) {
            is LightningOutgoingPayment.Status.Succeeded -> {
                TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_preimage_label), value = status.preimage.toHex())
            }
            is LightningOutgoingPayment.Status.Failed -> {
                TechnicalRow(label = stringResource(id = R.string.paymentdetails_error_label)) {
                    Text(text = status.reason.toString())
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun Bolt11InvoiceSection(
    invoice: Bolt11Invoice
) {
    val requestedAmount = invoice.amount
    if (requestedAmount != null) {
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_invoice_requested_label),
            amount = requestedAmount,
            rateThen = null
        )
    }

    val description = (invoice.description ?: invoice.descriptionHash?.toHex())?.takeIf { it.isNotBlank() }
    if (description != null) {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_request_description_label)) {
            Text(text = description)
        }
    }
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = invoice.paymentHash.toHex())
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_request_label), value = invoice.write())
}

@Composable
private fun Bolt12InvoiceSection(
    invoice: Bolt12Invoice,
    payerKey: PrivateKey,
) {
    val requestedAmount = invoice.amount
    if (requestedAmount != null) {
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_invoice_requested_label),
            amount = requestedAmount,
            rateThen = null
        )
    }

    val description = invoice.description?.takeIf { it.isNotBlank() }
    if (description != null) {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_request_description_label)) {
            Text(text = description)
        }
    }

    TechnicalRow(label = stringResource(id = R.string.paymentdetails_payerkey_label)) {
        Text(text = payerKey.toHex())
        val nodeParamsManager = business.nodeParamsManager
        val offerPayerKey by produceState<PrivateKey?>(initialValue = null) {
            value = nodeParamsManager.defaultOffer().payerKey
        }
        if (offerPayerKey != null && payerKey == offerPayerKey) {
            Spacer(modifier = Modifier.heightIn(4.dp))
            TextWithIcon(
                text = stringResource(id = R.string.paymentdetails_payerkey_is_mine),
                textStyle = MaterialTheme.typography.subtitle2,
                icon = R.drawable.ic_info,
                iconTint = MaterialTheme.typography.subtitle2.color,
            )
        }
    }
    TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = invoice.paymentHash.toHex())
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_offer_label), value = invoice.invoiceRequest.offer.encode())
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_bolt12_label), value = invoice.write())
}