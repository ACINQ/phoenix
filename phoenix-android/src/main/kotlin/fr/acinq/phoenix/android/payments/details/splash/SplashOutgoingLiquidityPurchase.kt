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

package fr.acinq.phoenix.android.payments.details.splash

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigateToPaymentDetails
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.utils.extensions.isManualPurchase
import fr.acinq.phoenix.utils.extensions.relatedPaymentIds
import fr.acinq.phoenix.utils.extensions.state

@Composable
fun SplashLiquidityPurchase(
    payment: InboundLiquidityOutgoingPayment,
) {
    // if this purchase is paid from the balance and was not triggered by a on-chain new-channel, show the amount
    if (payment.purchase.amount != 1.sat && payment.feePaidFromChannelBalance.total > 0.sat) {
        SplashAmount(amount = payment.amount, state = payment.state(), isOutgoing = true)
    } else {
        Spacer(modifier = Modifier.height(36.dp))
    }
    SplashPurchase(payment = payment)
    SplashFee(payment = payment)
    SplashRelatedPayments(payment)
}

@Composable
private fun SplashPurchase(
    payment: InboundLiquidityOutgoingPayment,
) {
    if (payment.purchase.amount > 1.sat) {
        val btcUnit = LocalBitcoinUnit.current
        SplashLabelRow(label = stringResource(id = R.string.paymentdetails_liquidity_purchase_label)) {
            Text(text = payment.purchase.amount.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
        }
    }
}

@Composable
fun SplashStatusLiquidityPurchase(payment: InboundLiquidityOutgoingPayment, fromEvent: Boolean) {
    when (val lockedAt = payment.lockedAt) {
        null -> {
            PaymentStatusIcon(
                message = null,
                imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                isAnimated = false,
                color = mutedTextColor,
            )
        }
        else -> {
            PaymentStatusIcon(
                message = {
                    when {
                        payment.isManualPurchase() -> Text(text = annotatedStringResource(id = R.string.paymentdetails_status_inbound_liquidity_success, lockedAt.toRelativeDateString()))
                        else -> Text(text = annotatedStringResource(id = R.string.paymentdetails_status_inbound_liquidity_auto_success, lockedAt.toRelativeDateString()))
                    }
                },
                imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                isAnimated = fromEvent,
                color = positiveColor,
            )
        }
    }
}

@Composable
private fun SplashFee(
    payment: InboundLiquidityOutgoingPayment
) {
    val btcUnit = LocalBitcoinUnit.current
    if (payment.purchase.amount != 1.sat && payment.feePaidFromChannelBalance.total > 0.sat) {
        val miningFee = payment.feePaidFromChannelBalance.miningFee
        val serviceFee = payment.feePaidFromChannelBalance.serviceFee
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(
            label = stringResource(id = R.string.paymentdetails_liquidity_service_fee_label),
            helpMessage = stringResource(id = R.string.paymentdetails_liquidity_service_fee_help)
        ) {
            Text(text = serviceFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
        }
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(
            label = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_label),
            helpMessage = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_help)
        ) {
            Text(text = miningFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
        }
    }
}

@Composable
private fun SplashRelatedPayments(payment: InboundLiquidityOutgoingPayment) {
    val paymentsManager = business.paymentsManager
    val relatedPaymentIds by produceState(initialValue = emptyList(), key1 = payment) {
        value = if (payment.purchase.paymentDetails is LiquidityAds.PaymentDetails.FromChannelBalance) {
            paymentsManager.listIncomingPaymentsForTxId(payment.txId).map { it.id }
        } else payment.relatedPaymentIds()
    }
    if (relatedPaymentIds.isNotEmpty()) {
        val navController = navController
        val paymentId = relatedPaymentIds.first()
        Spacer(modifier = Modifier.height(4.dp))
        SplashLabelRow(
            label = stringResource(id = R.string.paymentdetails_liquidity_caused_by_label),
            helpMessage = if (payment.isManualPurchase()) null else stringResource(id = R.string.paymentdetails_liquidity_caused_by_help),
            helpLink = stringResource(id = R.string.paymentdetails_liquidity_caused_by_help_link) to "https://acinq.co/faq",
        ) {
            Button(
                text = paymentId.toString(),
                onClick = { navigateToPaymentDetails(navController, paymentId, isFromEvent = false) },
                maxLines = 1,
                padding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                space = 4.dp,
                shape = RoundedCornerShape(12.dp),
                backgroundColor = mutedBgColor,
                modifier = Modifier.widthIn(max = 130.dp)
            )
        }
    }
}
