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
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TechnicalRowSelectable
import fr.acinq.phoenix.android.payments.details.TechnicalRowWithCopy
import fr.acinq.phoenix.android.payments.details.TimestampSection
import fr.acinq.phoenix.android.utils.converters.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.utils.extensions.description

@Composable
fun TechnicalIncomingBolt12(
    payment: Bolt12IncomingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    Card {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
            Text(text = stringResource(R.string.paymentdetails_type_incoming_bolt12))
        }
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
            Text(text = when (payment.completedAt) {
                null -> stringResource(id = R.string.paymentdetails_status_pending)
                else -> stringResource(R.string.paymentdetails_status_success)
            })
        }
    }

    Card {
        TimestampSection(payment)
    }

    Card {
        TechnicalRowAmount(
            label = stringResource(R.string.paymentdetails_amount_received_label),
            amount = payment.amountReceived,
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        IncomingBolt12Details(metadata = payment.metadata, originalFiatRate = originalFiatRate)
    }

    LightningPartsSection(payment = payment, originalFiatRate = originalFiatRate)
}

@Composable
fun IncomingBolt12Details(
    metadata: OfferPaymentMetadata,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    TechnicalRowAmount(
        label = stringResource(id = R.string.paymentdetails_invoice_requested_label),
        amount = metadata.amount,
        rateThen = originalFiatRate
    )
    metadata.description?.let { description ->
        TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_bolt12_description_label), value = description)
    }
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_payment_hash_label), value = metadata.paymentHash.toHex())
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_preimage_label), value = metadata.preimage.toHex())
    TechnicalRowWithCopy(label = stringResource(id = R.string.paymentdetails_offer_metadata_label), value = metadata.encode().toHex())
    metadata.payerKey?.let { payerKey ->
        TechnicalRowSelectable(label = stringResource(id = R.string.paymentdetails_payerkey_label), value = payerKey.toHex())
    }
}