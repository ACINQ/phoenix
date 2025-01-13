/*
 * Copyright 2025 ACINQ SAS
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
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.PrimarySeparator
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.utils.extensions.WalletPaymentState

@Composable
fun SplashAmount(
    amount: MilliSatoshi,
    state: WalletPaymentState,
    isOutgoing: Boolean,
) {
    // hide the total if this is a liquidity purchase that's not paid from the balance
//    if (payment is InboundLiquidityOutgoingPayment && payment.feePaidFromChannelBalance.total == 0.sat) {
//        Unit
//    } else {
        AmountView(
            amount = amount,
//            amount = when (payment) {
//                is InboundLiquidityOutgoingPayment -> payment.feePaidFromChannelBalance.total.toMilliSatoshi()
//                is OutgoingPayment -> payment.amount - payment.fees
//                is IncomingPayment -> payment.amount
//            },
            amountTextStyle = MaterialTheme.typography.body1.copy(fontSize = 30.sp),
            separatorSpace = 4.dp,
            prefix = stringResource(id = if (isOutgoing) R.string.paymentline_prefix_sent else R.string.paymentline_prefix_received)
        )
        Spacer(modifier = Modifier.height(36.dp))
        PrimarySeparator(
            height = 6.dp,
            color = when (state) {
                WalletPaymentState.Failure -> negativeColor
                WalletPaymentState.SuccessOffChain, WalletPaymentState.SuccessOnChain -> positiveColor
                else -> mutedBgColor
            }
        )
//    }
    Spacer(modifier = Modifier.height(36.dp))
}