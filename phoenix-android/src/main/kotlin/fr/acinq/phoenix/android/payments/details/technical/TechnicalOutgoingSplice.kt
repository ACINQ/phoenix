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
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.payments.details.ChannelIdRow
import fr.acinq.phoenix.android.payments.details.OutgoingAmountSection
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowSelectable
import fr.acinq.phoenix.android.payments.details.TimestampSection
import fr.acinq.phoenix.android.payments.details.TransactionRow
import fr.acinq.phoenix.data.ExchangeRate

@Composable
fun TechnicalOutgoingSplice(
    payment: SpliceOutgoingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    Card {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
            Text(text = stringResource(id = R.string.paymentdetails_type_outgoing_splice))
        }
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
            Text(text = when (payment.confirmedAt) {
                null -> stringResource(R.string.paymentdetails_status_pending)
                else -> stringResource(R.string.paymentdetails_status_success)
            })
        }
    }

    Card {
        TimestampSection(payment = payment)
    }

    Card {
        OutgoingAmountSection(payment = payment, originalFiatRate = originalFiatRate)
        ChannelIdRow(channelId = payment.channelId, label = stringResource(id = R.string.paymentdetails_splice_out_channel_label))
        TechnicalRowSelectable(
            label = stringResource(id = R.string.paymentdetails_bitcoin_address_label),
            value = payment.address
        )
        TransactionRow(payment.txId)
    }
}