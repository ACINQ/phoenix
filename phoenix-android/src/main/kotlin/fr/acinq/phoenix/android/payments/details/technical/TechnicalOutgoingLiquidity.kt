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
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigateToPaymentDetails
import fr.acinq.phoenix.android.payments.details.ChannelIdRow
import fr.acinq.phoenix.android.payments.details.TechnicalCard
import fr.acinq.phoenix.android.payments.details.TechnicalRow
import fr.acinq.phoenix.android.payments.details.TechnicalRowAmount
import fr.acinq.phoenix.android.payments.details.TechnicalRowClickable
import fr.acinq.phoenix.android.payments.details.TransactionRow
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.utils.extensions.relatedPaymentIds

@Composable
fun TechnicalOutgoingLiquidity(
    payment: InboundLiquidityOutgoingPayment,
    originalFiatRate: ExchangeRate.BitcoinPriceRate?
) {



    TechnicalCard {
        TechnicalRowAmount(
            label = stringResource(id = R.string.paymentdetails_liquidity_amount_label),
            amount = payment.purchase.amount.toMilliSatoshi(),
            rateThen = originalFiatRate,
            mSatDisplayPolicy = MSatDisplayPolicy.SHOW
        )
        if (payment.feePaidFromChannelBalance.total > 0.sat) {
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_label),
                amount = payment.feePaidFromChannelBalance.miningFee.toMilliSatoshi(),
                rateThen = originalFiatRate,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
            TechnicalRowAmount(
                label = stringResource(id = R.string.paymentdetails_liquidity_service_fee_label),
                amount = payment.feePaidFromChannelBalance.serviceFee.toMilliSatoshi(),
                rateThen = originalFiatRate,
                mSatDisplayPolicy = MSatDisplayPolicy.SHOW
            )
        }

        TechnicalRow(label = stringResource(id = R.string.paymentdetails_liquidity_purchase_type)) {
            Text(text = "${
                when (payment.purchase) {
                    is LiquidityAds.Purchase.Standard -> "Standard"
                    is LiquidityAds.Purchase.WithFeeCredit -> "Fee credit"
                }
            } [${payment.purchase.paymentDetails.paymentType}]")
        }
        TransactionRow(payment.txId)
        ChannelIdRow(channelId = payment.channelId)
        val paymentIds = payment.relatedPaymentIds()
        val navController = navController
        paymentIds.forEach {
            TechnicalRowClickable(
                label = stringResource(id = R.string.paymentdetails_liquidity_caused_by_label),
                onClick = { navigateToPaymentDetails(navController, it, isFromEvent = false) },
            ) {
                Text(text = it.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}