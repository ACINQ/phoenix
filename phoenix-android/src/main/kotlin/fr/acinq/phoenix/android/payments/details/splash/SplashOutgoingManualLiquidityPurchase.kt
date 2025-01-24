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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.utils.extensions.state

@Composable
fun SplashManualLiquidityPurchase(
    payment: ManualLiquidityPurchasePayment,
) {
    SplashAmount(amount = payment.amount, state = payment.state(), isOutgoing = true)
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_liquidity_purchase_label)) {
        Text(text = payment.liquidityPurchase.amount.toPrettyString(LocalBitcoinUnit.current, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
    }
    SplashFee(payment = payment)
}

@Composable
fun SplashStatusManualLiquidityPurchase(payment: ManualLiquidityPurchasePayment, fromEvent: Boolean) {
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
                    Text(text = annotatedStringResource(id = R.string.paymentdetails_status_inbound_liquidity_success, lockedAt.toRelativeDateString()))
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
    payment: ManualLiquidityPurchasePayment
) {
    val btcUnit = LocalBitcoinUnit.current
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(
        label = stringResource(id = R.string.paymentdetails_liquidity_service_fee_label),
        helpMessage = stringResource(id = R.string.paymentdetails_liquidity_service_fee_help)
    ) {
        Text(text = payment.serviceFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
    }
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(
        label = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_label),
        helpMessage = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_help)
    ) {
        Text(text = payment.miningFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
    }
}
