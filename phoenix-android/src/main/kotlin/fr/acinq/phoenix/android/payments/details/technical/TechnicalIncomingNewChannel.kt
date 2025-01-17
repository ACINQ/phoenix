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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.channel.ChannelManagementFees
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.InlineTransactionLink
import fr.acinq.phoenix.android.payments.details.ChannelIdRow
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TimestampSection
import fr.acinq.phoenix.android.payments.details.TransactionRow
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate

@Composable
fun TechnicalIncomingNewChannel(
    payment: NewChannelIncomingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?,
) {
    Card {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
            Text(text = stringResource(id = R.string.paymentdetails_type_incoming_onchain_newchannel))
        }
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_status_label)) {
            Text(text = when {
                payment.lockedAt == null -> stringResource(id = R.string.paymentdetails_status_pending)
                payment.lockedAt != null && payment.confirmedAt == null -> stringResource(R.string.paymentdetails_status_success)
                else -> stringResource(R.string.paymentdetails_status_confirmed)
            })
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
        val paymentsManager = business.paymentsManager
        val channelManagementFees by produceState(initialValue = ChannelManagementFees(0.sat, 0.sat)) {
            val liquidityPayments = paymentsManager.listPaymentsForTxId(payment.txId).filterIsInstance<InboundLiquidityOutgoingPayment>()
            value = liquidityPayments.map { it.feePaidFromChannelBalance }.fold(ChannelManagementFees(0.sat, 0.sat)) { a, b ->
                ChannelManagementFees(miningFee = a.miningFee + b.miningFee, serviceFee = a.serviceFee + b.serviceFee)
            }
        }
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_service_fees_label),
            amount = payment.serviceFee + channelManagementFees.serviceFee.toMilliSatoshi(),
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_funding_fees_label),
            amount = payment.miningFee.toMilliSatoshi() + channelManagementFees.miningFee.toMilliSatoshi(),
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.HIDE
        )
        ChannelIdRow(channelId = payment.channelId)
        TransactionRow(txId = payment.txId)
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_inputs_label)) {
            payment.localInputs.mapIndexed { index, outpoint ->
                Row {
                    Text(text = stringResource(id = R.string.paymentdetails_inputs_value, index + 1))
                    Spacer(modifier = Modifier.width(4.dp))
                    InlineTransactionLink(txId = outpoint.txid)
                }
            }
        }
    }
}
