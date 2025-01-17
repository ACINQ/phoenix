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
import fr.acinq.lightning.db.OnChainIncomingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.extensions.smartDescription
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.utils.extensions.state


@Composable
fun SplashIncomingNewChannel(
    payment: NewChannelIncomingPayment,
    metadata: WalletPaymentMetadata,
    onMetadataDescriptionUpdate: (UUID, String?) -> Unit,
) {
    SplashAmount(amount = payment.amount, state = payment.state(), isOutgoing = false)
    SplashDescription(
        description = payment.smartDescription(),
        userDescription = metadata.userDescription,
        paymentId = payment.id,
        onMetadataDescriptionUpdate = onMetadataDescriptionUpdate,
    )
    SplashFee(payment = payment)
}

@Composable
private fun SplashFee(payment: OnChainIncomingPayment) {
    val paymentsManager = business.paymentsManager
    val channelManagementFees by produceState(initialValue = ChannelManagementFees(0.sat, 0.sat)) {
        val liquidityPayments = paymentsManager.listPaymentsForTxId(payment.txId).filterIsInstance<InboundLiquidityOutgoingPayment>()
        value = liquidityPayments.map { it.feePaidFromChannelBalance }.fold(ChannelManagementFees(0.sat, 0.sat)) { a, b ->
            ChannelManagementFees(miningFee = a.miningFee + b.miningFee, serviceFee = a.serviceFee + b.serviceFee)
        }
    }

    val serviceFee = payment.serviceFee + channelManagementFees.serviceFee.toMilliSatoshi()
    if (serviceFee > 0.msat) {
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(
            label = stringResource(id = R.string.paymentdetails_service_fees_label),
            helpMessage = stringResource(R.string.paymentdetails_service_fees_desc)
        ) {
            Text(text = serviceFee.toPrettyString(LocalBitcoinUnit.current, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW))
        }
    }

    val miningFee = payment.miningFee + channelManagementFees.miningFee
    if (miningFee > 0.sat) {
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(
            label = stringResource(id = R.string.paymentdetails_funding_fees_label),
            helpMessage = stringResource(R.string.paymentdetails_funding_fees_desc)
        ) {
            Text(text = miningFee.toPrettyString(LocalBitcoinUnit.current, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.HIDE))
        }
    }
}