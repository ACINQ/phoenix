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

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.CardHeader
import fr.acinq.phoenix.android.payments.details.Bolt11InvoiceSection
import fr.acinq.phoenix.android.payments.details.Bolt12InvoiceSection
import fr.acinq.phoenix.android.payments.details.OutgoingAmountSection
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TechnicalRowSelectable
import fr.acinq.phoenix.android.payments.details.TechnicalRowWithCopy
import fr.acinq.phoenix.android.payments.details.TimestampSection
import fr.acinq.phoenix.data.ExchangeRate

@Suppress("DEPRECATION")
@Composable
fun TechnicalOutgoingLightning(
    payment: LightningOutgoingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    Card {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
            Text(text = when (payment.details) {
                is LightningOutgoingPayment.Details.Normal -> stringResource(R.string.paymentdetails_type_outgoing_bolt11)
                is LightningOutgoingPayment.Details.SwapOut -> stringResource(R.string.paymentdetails_type_outgoing_swapout)
                is LightningOutgoingPayment.Details.Blinded -> stringResource(id = R.string.paymentdetails_type_outgoing_bolt12)
            })
        }
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
            Text(text = when (payment.status) {
                is LightningOutgoingPayment.Status.Pending -> stringResource(R.string.paymentdetails_status_pending)
                is LightningOutgoingPayment.Status.Succeeded -> stringResource(R.string.paymentdetails_status_success)
                is LightningOutgoingPayment.Status.Failed -> stringResource(R.string.paymentdetails_status_failed)
            })
        }
    }

    Card {
        TimestampSection(payment = payment)
    }

    Card {
        OutgoingAmountSection(payment = payment, originalFiatRate = originalFiatRate)

        when (val details = payment.details) {
            is LightningOutgoingPayment.Details.Normal -> {
                Bolt11InvoiceSection(invoice = details.paymentRequest, preimage = (payment.status as? LightningOutgoingPayment.Status.Succeeded)?.preimage, originalFiatRate = originalFiatRate)
                TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_pubkey_label), value = payment.recipient.toHex())
            }
            is LightningOutgoingPayment.Details.SwapOut -> {
                TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_bitcoin_address_label), value = details.address)
                TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = details.paymentHash.toHex())
                ((payment.status as? LightningOutgoingPayment.Status.Succeeded)?.preimage)?.let {
                    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_preimage_label), value = it.toHex())
                }
            }
            is LightningOutgoingPayment.Details.Blinded -> {
                Bolt12InvoiceSection(invoice = details.paymentRequest, payerKey = details.payerKey, preimage = (payment.status as? LightningOutgoingPayment.Status.Succeeded)?.preimage, originalFiatRate = originalFiatRate)
            }
        }

        val status = payment.status
        if (status is LightningOutgoingPayment.Status.Failed) {
            TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_error_label), value = status.reason.toString())
        }
    }

    if (payment.parts.isNotEmpty()) {
        CardHeader(text = stringResource(id = R.string.paymentdetails_parts_label, payment.parts.size))
        payment.parts.forEachIndexed() { index, part ->
            Card {
                TechnicalRow(label = stringResource(id = R.string.paymentdetails_part_label)) {
                    Text("#$index")
                }
                TechnicalRow(label = stringResource(id = R.string.paymentdetails_part_hops_label)) {
                    part.route.forEach {
                        Text("-> ${it.nextNodeId.toHex()}", maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                }
                TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_sent_label), amount = part.amount, rateThen = originalFiatRate)
            }
        }
    }
}
