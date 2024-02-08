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

package fr.acinq.phoenix.android.payments.receive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.feedback.InfoMessage
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.share
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore

@Composable
fun BitcoinAddressView(
    vm: ReceiveViewModel,
    maxWidth: Dp
) {
    val context = LocalContext.current

    InvoiceHeader(
        icon = R.drawable.ic_chain,
        helpMessage = stringResource(id = R.string.receive_bitcoin_help),
        content = { Text(text = stringResource(id = R.string.receive_bitcoin_title)) },
    )

    when (val state = vm.currentSwapAddress) {
        is BitcoinAddressState.Init -> {
            Box(contentAlignment = Alignment.Center) {
                QRCodeView(bitmap = null, data = null, maxWidth = maxWidth)
                Card(shape = RoundedCornerShape(16.dp)) { ProgressView(text = stringResource(id = R.string.receive_bitcoin_generating)) }
            }
            Spacer(modifier = Modifier.height(32.dp))
            CopyShareEditButtons(onCopy = { }, onShare = { }, onEdit = null, maxWidth = maxWidth)
        }
        is BitcoinAddressState.Show -> {
            QRCodeView(data = state.currentAddress, bitmap = state.image, maxWidth = maxWidth)
            Spacer(modifier = Modifier.height(32.dp))
            CopyShareEditButtons(
                onCopy = { copyToClipboard(context, data = state.currentAddress) },
                onShare = { share(context, "bitcoin:${state.currentAddress}", context.getString(R.string.receive_bitcoin_share_subject), context.getString(R.string.receive_bitcoin_share_title)) },
                onEdit = null,
                maxWidth = maxWidth,
            )
            Spacer(modifier = Modifier.height(24.dp))
            HSeparator(width = 50.dp)
            Spacer(modifier = Modifier.height(16.dp))
            QRCodeDetail(label = stringResource(id = R.string.receive_bitcoin_address_label), value = state.currentAddress)

            val isFromLegacy by LegacyPrefsDatastore.hasMigratedFromLegacy(context).collectAsState(initial = false)
            if (isFromLegacy) {
                Spacer(modifier = Modifier.height(24.dp))
                InfoMessage(
                    header = stringResource(id = R.string.receive_onchain_legacy_warning_title),
                    details = stringResource(id = R.string.receive_onchain_legacy_warning),
                    detailsStyle = MaterialTheme.typography.subtitle2,
                    alignment = Alignment.CenterHorizontally
                    )
            }
        }
        is BitcoinAddressState.Error -> {
            ErrorMessage(
                header = stringResource(id = R.string.receive_bitcoin_error),
                details = state.e.message
            )
        }
    }
}
