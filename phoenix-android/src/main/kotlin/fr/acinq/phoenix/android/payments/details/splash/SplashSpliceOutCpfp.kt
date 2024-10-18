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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.phoenix.android.LocalBitcoinUnit
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.SplashLabelRow
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.MSatDisplayPolicy
import fr.acinq.phoenix.android.utils.extensions.smartDescription
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.walletPaymentId

@Composable
fun SplashSpliceOutCpfp(
    payment: SpliceCpfpOutgoingPayment,
    metadata: WalletPaymentMetadata,
    onMetadataDescriptionUpdate: (WalletPaymentId, String?) -> Unit,
) {
    SplashDescription(
        description = payment.smartDescription(),
        userDescription = metadata.userDescription,
        paymentId = payment.walletPaymentId(),
        onMetadataDescriptionUpdate = onMetadataDescriptionUpdate
    )
    SplashDestination()
    SplashFee(payment = payment)
}

@Composable
private fun SplashDestination() {
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_destination_label), icon = R.drawable.ic_chain) {
        SelectionContainer {
            Text(text = stringResource(id = R.string.paymentdetails_destination_cpfp_value))
        }
    }
}

@Composable
private fun SplashFee(payment: SpliceCpfpOutgoingPayment) {
    val btcUnit = LocalBitcoinUnit.current
    Spacer(modifier = Modifier.height(8.dp))
    SplashLabelRow(label = stringResource(id = R.string.paymentdetails_fees_label)) {
        Text(text = payment.fees.toPrettyString(btcUnit, withUnit = true, mSatDisplayPolicy = MSatDisplayPolicy.SHOW_IF_ZERO_SATS))
    }
}
