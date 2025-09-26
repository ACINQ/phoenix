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

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.payments.details.ChannelIdRow
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TimestampSection
import fr.acinq.phoenix.android.payments.details.TransactionRow
import fr.acinq.phoenix.android.utils.converters.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate

@Composable
fun TechnicalOutgoingManualLiquidity(
    payment: ManualLiquidityPurchasePayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?
) {
    Card {
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_payment_type_label)) {
            Text(text = stringResource(id = R.string.paymentdetails_type_outgoing_liquidity_manual))
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
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_liquidity_amount_label),
            amount = payment.liquidityPurchase.amount.toMilliSatoshi(),
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_liquidity_service_fee_label),
            amount = payment.serviceFee,
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_label),
            amount = payment.miningFee.toMilliSatoshi(),
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        TechnicalRow(label = stringResource(id = R.string.paymentdetails_liquidity_purchase_type)) {
            Text(text = when (payment.liquidityPurchase) {
                is LiquidityAds.Purchase.Standard -> "Standard"
                is LiquidityAds.Purchase.WithFeeCredit -> "Fee credit"
            })
            Text(text = "[${payment.liquidityPurchase.paymentDetails.paymentType}]", style = MaterialTheme.typography.subtitle2)
        }
        ChannelIdRow(channelId = payment.channelId)
        TransactionRow(txId = payment.txId)
    }
}