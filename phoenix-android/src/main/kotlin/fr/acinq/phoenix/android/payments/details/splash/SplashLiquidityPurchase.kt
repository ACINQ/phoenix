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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.BottomSheetDialog
import fr.acinq.phoenix.android.components.Clickable
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.navigateToPaymentDetails
import fr.acinq.phoenix.android.payments.details.PaymentLine
import fr.acinq.phoenix.android.payments.details.PaymentLineLoading
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata

@Composable
fun SplashLiquidityPurchase(
    payment: InboundLiquidityOutgoingPayment,
    metadata: WalletPaymentMetadata,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
) {
    SplashPurchase(purchase = payment.purchase)
    Spacer(modifier = Modifier.height(12.dp))
    SplashFee(payment = payment)

    // FIXME: dangerous!!
    //    In general, FromChannelBalance only happens for manual purchases OR automated swap-ins with additional liquidity.
    //    However, swap-ins do not **yet** request additional liquidity, so **for now** we can make a safe approximation.
    //    Eventually, once swap-ins are upgraded to request liquidity, this will have to be fixed, .
    if (payment.purchase.paymentDetails !is LiquidityAds.PaymentDetails.FromChannelBalance) {
        AutoLiquidityDetails(purchase = payment.purchase)
    }
}

@Composable
fun SplashLiquidityStatus(payment: InboundLiquidityOutgoingPayment, fromEvent: Boolean) {
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
                    if (payment.purchase.paymentDetails is LiquidityAds.PaymentDetails.FromChannelBalance) {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_inbound_liquidity_success, lockedAt.toRelativeDateString()))
                    } else {
                        Text(text = annotatedStringResource(id = R.string.paymentdetails_status_inbound_liquidity_auto_success, lockedAt.toRelativeDateString()))
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
    SplashLabelRow(
        label = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_label),
        helpMessage = stringResource(id = R.string.paymentdetails_liquidity_miner_fee_help)
    ) {
        Text(text = payment.miningFees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
    }
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(
        label = stringResource(id = R.string.paymentdetails_liquidity_service_fee_label),
        helpMessage = stringResource(id = R.string.paymentdetails_liquidity_service_fee_help)
    ) {
        Text(text = payment.serviceFees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
        if (payment.purchase is LiquidityAds.Purchase.WithFeeCredit) {
            Text(text = "Paid with fee credit")
        }
    }
}

@Composable
private fun SplashPurchase(
    purchase: LiquidityAds.Purchase
) {
    val btcUnit = LocalBitcoinUnit.current
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = "Liquidity") {
        Text(text = purchase.amount.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutoLiquidityDetails(
    purchase: LiquidityAds.Purchase
) {
    val navController = navController
    var showPaymentsDialog by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(32.dp))
    BorderButton(
        text = "What is this?",
        icon = R.drawable.ic_help_circle,
        onClick = { showPaymentsDialog = true },
        maxLines = 1,
    )

    if (showPaymentsDialog) {
        BottomSheetDialog(onDismiss = { showPaymentsDialog = false }, modifier = Modifier.fillMaxHeight(.6f), internalPadding = PaddingValues(bottom = 32.dp)) {
            val pagerState = rememberPagerState(pageCount = { 2 })
            HorizontalPager(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                state = pagerState,
                verticalAlignment = Alignment.Top,
                beyondBoundsPageCount = 1
            ) { index ->
                when (index) {
                    0 -> {
                        Column {
                            Text(
                                text = "Why did this payment happen?",
                                style = MaterialTheme.typography.h4,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Your Lightning channel had to be resized, which is an on-chain operation incurring fees.", modifier = Modifier.padding(horizontal = 24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "This operation was necessary to accommodate new incoming payments.", modifier = Modifier.padding(horizontal = 24.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Swipe right to see these payments.",
                                style = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "How to optimise channels resizing?",
                                style = MaterialTheme.typography.h4,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Clickable(onClick = { navController.navigate(Screen.LiquidityPolicy.route) }, modifier = Modifier.padding(horizontal = 12.dp), shape = RoundedCornerShape(10.dp)) {
                                Column(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextWithIcon(
                                        text = "Configure automated management",
                                        icon = R.drawable.ic_settings,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "Cap fees, or disable them altogether", style = MaterialTheme.typography.subtitle2)
                                }
                            }
                            Clickable(onClick = { navController.navigate(Screen.LiquidityRequest.route) }, modifier = Modifier.padding(horizontal = 12.dp), shape = RoundedCornerShape(10.dp)) {
                                Column(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextWithIcon(
                                        text = "Purchase liquidity in advance",
                                        icon = R.drawable.ic_idea,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "Requires some planning, but is most optimal", style = MaterialTheme.typography.subtitle2)
                                }
                            }
                        }
                    }
                    1 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(text = "Operation triggered by...", style = MaterialTheme.typography.h4, modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            val paymentHashes = when (val details = purchase.paymentDetails) {
                                is LiquidityAds.PaymentDetails.FromFutureHtlc -> details.paymentHashes
                                is LiquidityAds.PaymentDetails.FromChannelBalanceForFutureHtlc -> details.paymentHashes
                                else -> emptyList()
                            }
                            TriggeredBy(paymentHashes = paymentHashes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggeredBy(paymentHashes: List<ByteVector32>) {
    val context = LocalContext.current
    val navController = navController
    val paymentsManager = business.paymentsManager
    paymentHashes.forEach { paymentHash ->
        val id = remember(paymentHash) { WalletPaymentId.IncomingPaymentId(paymentHash) }
        val paymentInfo by produceState<WalletPaymentInfo?>(initialValue = null) {
            value = paymentsManager.getPayment(id = id, options = WalletPaymentFetchOptions.None)
        }

        paymentInfo?.let {
            PaymentLine(paymentInfo = it, contactInfo = null, onPaymentClick = { navigateToPaymentDetails(navController, id, isFromEvent = false) })
        } ?: PaymentLineLoading(paymentId = id, onPaymentClick = { navigateToPaymentDetails(navController, id, isFromEvent = false) })
    }
}
