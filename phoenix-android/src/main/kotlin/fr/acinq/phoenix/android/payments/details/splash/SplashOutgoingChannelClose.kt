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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.android.LocalBitcoinUnits
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.converters.DateFormatter.toRelativeDateString
import fr.acinq.phoenix.android.utils.converters.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.annotatedStringResource
import fr.acinq.phoenix.android.utils.extensions.smartDescription
import fr.acinq.phoenix.android.utils.isLegacyMigration
import fr.acinq.phoenix.android.utils.mutedTextColor
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.data.WalletPaymentMetadata

@Composable
fun SplashChannelClose(
    payment: ChannelCloseOutgoingPayment,
    metadata: WalletPaymentMetadata,
    onMetadataDescriptionUpdate: (UUID, String?) -> Unit,
) {
    val peer by business.peerManager.peerState.collectAsState()

    val isLegacyMigration = payment.isLegacyMigration(metadata, peer)
    val description = when (isLegacyMigration) {
        null -> stringResource(id = R.string.paymentdetails_desc_closing_channel) // not sure yet, but we still know it's a closing
        true -> stringResource(id = R.string.paymentdetails_desc_legacy_migration)
        false -> payment.smartDescription()
    }

    SplashDescription(
        description = description,
        userDescription = metadata.userDescription,
        paymentId = payment.id,
        onMetadataDescriptionUpdate = onMetadataDescriptionUpdate
    )
    SplashDestination(payment)
    SplashFee(payment = payment)
}

@Composable
fun SplashStatusChannelClose(payment: ChannelCloseOutgoingPayment, fromEvent: Boolean, onCpfpSuccess: () -> Unit) {
    when (payment.confirmedAt) {
        null -> {
            PaymentStatusIcon(
                message = null,
                imageResId = R.drawable.ic_payment_details_pending_onchain_static,
                isAnimated = false,
                color = mutedTextColor,
            )
            SplashConfirmationView(payment.txId, payment.channelId, isConfirmed = false, canBeBumped = false, onCpfpSuccess = onCpfpSuccess)
        }
        else -> {
            PaymentStatusIcon(
                message = {
                    Text(text = annotatedStringResource(id = R.string.paymentdetails_status_channelclose_confirmed, payment.completedAt?.toRelativeDateString() ?: ""))
                },
                imageResId = if (fromEvent) R.drawable.ic_payment_details_success_animated else R.drawable.ic_payment_details_success_static,
                isAnimated = fromEvent,
                color = positiveColor,
            )
            SplashConfirmationView(payment.txId, payment.channelId, isConfirmed = true, canBeBumped = false, onCpfpSuccess)
        }
    }
}

@Composable
private fun SplashDestination(payment: ChannelCloseOutgoingPayment) {
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_destination_label), icon = R.drawable.ic_chain) {
        SelectionContainer {
            Text(text = payment.address)
        }
    }
}

@Composable
private fun SplashFee(payment: ChannelCloseOutgoingPayment) {
    val btcUnit = LocalBitcoinUnits.current.primary
    if (payment.fees > 0.msat) {
        Spacer(modifier = Modifier.height(8.dp))
        SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
            Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
        }
    }
}