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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.payments.details.ChannelIdRow
import fr.acinq.phoenix.android.payments.details.OutgoingAmountSection
import fr.acinq.phoenix.android.payments.details.TechnicalCard
import fr.acinq.phoenix.android.payments.details.TechnicalRowSelectable
import fr.acinq.phoenix.android.payments.details.TransactionRow
import fr.acinq.phoenix.data.ExchangeRate

@Composable
fun TechnicalOutgoingChannelClose(
    payment: ChannelCloseOutgoingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {

    TechnicalCard {
        OutgoingAmountSection(payment = payment, originalFiatRate = originalFiatRate)
        ChannelIdRow(payment.channelId)
        TechnicalRowSelectable(
            label = stringResource(id = R.string.paymentdetails_bitcoin_address_label),
            value = payment.address
        )
        TransactionRow(payment.txId)
        TechnicalRowSelectable(
            label = stringResource(id = R.string.paymentdetails_closing_type_label),
            value = when (payment.closingType) {
                ChannelClosingType.Mutual -> stringResource(id = R.string.paymentdetails_closing_type_mutual)
                ChannelClosingType.Local -> stringResource(id = R.string.paymentdetails_closing_type_local)
                ChannelClosingType.Remote -> stringResource(id = R.string.paymentdetails_closing_type_remote)
                ChannelClosingType.Revoked -> stringResource(id = R.string.paymentdetails_closing_type_revoked)
                ChannelClosingType.Other -> stringResource(id = R.string.paymentdetails_closing_type_other)
            }
        )
    }
}