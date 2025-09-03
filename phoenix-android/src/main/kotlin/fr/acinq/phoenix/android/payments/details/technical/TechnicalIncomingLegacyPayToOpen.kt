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

@file:Suppress("DEPRECATION")

package fr.acinq.phoenix.android.payments.details.technical

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.CardHeader
import fr.acinq.phoenix.android.payments.details.Bolt11InvoiceSection
import fr.acinq.phoenix.android.payments.details.ChannelIdRow
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TimestampSection
import fr.acinq.phoenix.android.payments.details.TransactionRow
import fr.acinq.phoenix.android.utils.converters.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate


@Composable
fun TechnicalIncomingLegacyPayToOpen(
    payment: LegacyPayToOpenIncomingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    Card {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
            Text(text = stringResource(R.string.paymentdetails_type_incoming_legacy_paytopen))
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
        TimestampSection(payment = payment)
    }

    Card {
        TechnicalRowAmount(
            label = stringResource(R.string.paymentdetails_amount_received_label),
            amount = payment.amountReceived,
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        val (serviceFee, miningFee) = payment.parts.filterIsInstance<LegacyPayToOpenIncomingPayment.Part.OnChain>().fold(0.msat to 0.sat) { a, b ->
            a.first + b.serviceFee to (a.second + b.miningFee)
        }
        if (serviceFee > 0.msat) {
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_service_fees_label),
                amount = serviceFee,
                rateThen = originalFiatRate,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
        }
        if (miningFee > 0.sat) {
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_funding_fees_label),
                amount = miningFee.toMilliSatoshi(),
                rateThen = originalFiatRate,
                mSatDisplayPolicy = MSatDisplayPolicy.HIDE
            )
        }
        when (val origin = payment.origin) {
            is LegacyPayToOpenIncomingPayment.Origin.Invoice -> Bolt11InvoiceSection(invoice = origin.paymentRequest, preimage = payment.paymentPreimage, originalFiatRate = originalFiatRate)
            is LegacyPayToOpenIncomingPayment.Origin.Offer -> IncomingBolt12Details(metadata = origin.metadata, originalFiatRate = originalFiatRate)
        }
    }

    if (payment.parts.isNotEmpty()) {
        CardHeader(text = stringResource(id = R.string.paymentdetails_parts_label, payment.parts.size))
        Card {
            payment.parts.forEach { part ->
                when (part) {
                    is LegacyPayToOpenIncomingPayment.Part.OnChain -> {
                        TechnicalRow(label = stringResource(id = R.string.paymentdetails_received_with_label)) {
                            Text(text = stringResource(id = R.string.paymentdetails_received_with_legacy_onchain))
                        }
                        if (part.channelId != ByteVector32.Zeroes) ChannelIdRow(channelId = part.channelId)
                        if (part.txId.value != ByteVector32.Zeroes) TransactionRow(txId = part.txId)
                        TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = part.amountReceived, rateThen = originalFiatRate)
                    }
                    is LegacyPayToOpenIncomingPayment.Part.Lightning -> {
                        TechnicalRow(label = stringResource(id = R.string.paymentdetails_received_with_label)) {
                            Text(text = stringResource(id = R.string.paymentdetails_received_with_lightning))
                        }
                        if (part.channelId != ByteVector32.Zeroes) ChannelIdRow(channelId = part.channelId)
                        TechnicalRowAmount(label = stringResource(id = R.string.paymentdetails_amount_received_label), amount = part.amountReceived, rateThen = originalFiatRate)
                    }
                }
            }
        }
    }
}