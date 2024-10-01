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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.BottomSheetDialog
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.SplashClickableContent
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigateToPaymentDetails
import fr.acinq.phoenix.android.payments.details.PaymentLine
import fr.acinq.phoenix.android.payments.details.PaymentLineLoading
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.extensions.isManualPurchase
import fr.acinq.phoenix.utils.extensions.isPaidInTheFuture
import fr.acinq.phoenix.utils.extensions.relatedPaymentIds
import kotlinx.coroutines.launch

@Composable
fun SplashLiquidityPurchase(
    payment: InboundLiquidityOutgoingPayment,
) {
    SplashPurchase(payment = payment)
    SplashFee(payment = payment)
    SplashRelatedPayments(payment)
}

@Composable
private fun SplashPurchase(
    payment: InboundLiquidityOutgoingPayment,
) {
    val btcUnit = LocalBitcoinUnit.current
    SplashLabelRow(label = "Liquidity") {
        Text(text = payment.purchase.amount.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
    }
}

@Composable
private fun SplashFee(
    payment: InboundLiquidityOutgoingPayment
) {
    val btcUnit = LocalBitcoinUnit.current
    if (!payment.isPaidInTheFuture()) {
        Spacer(modifier = Modifier.height(8.dp))
        val miningFee = payment.feePaidFromChannelBalance.miningFee
        val serviceFee = payment.feePaidFromChannelBalance.serviceFee
        SplashLabelRow(
            label = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_label),
            helpMessage = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_help)
        ) {
            Text(text = miningFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
        }
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(
            label = stringResource(id = R.string.paymentdetails_liquidity_service_fee_label),
            helpMessage = stringResource(id = R.string.paymentdetails_liquidity_service_fee_help)
        ) {
            Text(text = serviceFee.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
        }
    }
}

@Composable
private fun SplashRelatedPayments(payment: InboundLiquidityOutgoingPayment) {
    val relatedPaymentIds = payment.relatedPaymentIds()
    if (relatedPaymentIds.isNotEmpty()) {
        val navController = navController
        val paymentId = relatedPaymentIds.first()
        Spacer(modifier = Modifier.height(4.dp))
        SplashLabelRow(
            label = "Caused by",
            helpMessage = if (payment.isManualPurchase()) null else "This liquidity was required to receive a payment.",
            helpLink = "See how to optimise" to "https://acinq.co/faq",
        ) {
            Button(
                text = paymentId.dbId,
                icon = R.drawable.ic_zap,
                onClick = { navigateToPaymentDetails(navController, paymentId, isFromEvent = false) },
                maxLines = 1,
                padding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                space = 4.dp,
                shape = RoundedCornerShape(12.dp),
                backgroundColor = mutedBgColor,
                modifier = Modifier.widthIn(max = 170.dp)
            )
        }
    }
}
