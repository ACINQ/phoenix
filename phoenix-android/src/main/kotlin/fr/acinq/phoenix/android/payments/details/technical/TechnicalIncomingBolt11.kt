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
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.CardHeader
import fr.acinq.phoenix.android.payments.details.Bolt11InvoiceSection
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TechnicalRowSelectable
import fr.acinq.phoenix.android.payments.details.TimestampSection
import fr.acinq.phoenix.android.utils.Converter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate

@Composable
fun TechnicalIncomingBolt11(
    payment: Bolt11IncomingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    Card {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
            Text(text = stringResource(R.string.paymentdetails_type_incoming_bolt11))
        }
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
            Text(
                text = when (payment.completedAt) {
                    null -> stringResource(id = R.string.paymentdetails_status_pending)
                    else -> stringResource(R.string.paymentdetails_status_success)
                }
            )
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
        Bolt11InvoiceSection(invoice = payment.paymentRequest, preimage = payment.paymentPreimage, originalFiatRate = originalFiatRate)
    }

    LightningPartsSection(payment = payment, originalFiatRate = originalFiatRate)
}

@Composable
fun LightningPartsSection(
    payment: LightningIncomingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?
) {
    if (payment.parts.isEmpty()) return

    CardHeader(text = stringResource(id = R.string.paymentdetails_parts_label, payment.parts.size))
    payment.parts.forEach { part ->
        Card {
            when (part) {
                is LightningIncomingPayment.Part.Htlc -> {
                    TechnicalRow(label = stringResource(id = R.string.paymentdetails_received_with_label)) {
                        Text(text = stringResource(id = R.string.paymentdetails_received_with_lightning))
                    }
                    TechnicalRowAmount(
                        label = stringResource(id = R.string.paymentdetails_amount_received_label),
                        amount = part.amountReceived,
                        rateThen = originalFiatRate
                    )
                    TechnicalRowAmount(
                        label = stringResource(id = R.string.paymentdetails_fees_label),
                        amount = part.fees,
                        rateThen = originalFiatRate
                    )
                }
                is LightningIncomingPayment.Part.FeeCredit -> {
                    TechnicalRow(label = stringResource(id = R.string.paymentdetails_received_with_label)) {
                        Text(text = stringResource(id = R.string.paymentdetails_received_with_fee_credit))
                    }
                    TechnicalRowAmount(
                        label = stringResource(id = R.string.paymentdetails_amount_added_to_fee_credit_label),
                        amount = part.amountReceived,
                        rateThen = originalFiatRate
                    )
                }
            }
            TechnicalRow(label = stringResource(id = R.string.paymentdetails_completed_at_label)) {
                Text(text = part.receivedAt.toAbsoluteDateTimeString())
            }
        }
    }
}