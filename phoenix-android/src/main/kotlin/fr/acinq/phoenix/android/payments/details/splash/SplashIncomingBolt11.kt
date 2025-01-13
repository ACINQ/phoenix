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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
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
fun SplashIncomingBolt11(
    payment: Bolt11IncomingPayment,
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
    SplashFeeLightningIncoming(payment)
}

@Composable
fun SplashFeeLightningIncoming(payment: LightningIncomingPayment) {
    val paymentsManager = business.paymentsManager
    val txIds = remember(payment) { payment.parts.filterIsInstance<LightningIncomingPayment.Part.Htlc>().mapNotNull { it.fundingFee?.fundingTxId } }
    val relatedLiquidityPayments by produceState(initialValue = emptyList()) {
        value = txIds.mapNotNull { paymentsManager.getLiquidityPurchaseForTxId(it) }
    }

    val serviceFee = remember(payment, relatedLiquidityPayments) {
        payment.fees + relatedLiquidityPayments.map { it.feePaidFromFutureHtlc.serviceFee.toMilliSatoshi() }.sum()
    }
    val miningFee = remember(relatedLiquidityPayments) {
        relatedLiquidityPayments.map { it.feePaidFromFutureHtlc.miningFee }.sum()
    }

    if (serviceFee > 0.msat) {
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(
            label = stringResource(id = R.string.paymentdetails_service_fees_label),
            helpMessage = stringResource(R.string.paymentdetails_service_fees_desc)
        ) {
            Text(text = serviceFee.toPrettyString(LocalBitcoinUnit.current, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW))
        }
    }

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