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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.copyToClipboard
import fr.acinq.phoenix.android.utils.share

@Composable
fun ColumnScope.BitcoinAddressView(
    vm: ReceiveViewModel,
    columnWidth: Dp
) {
    val context = LocalContext.current
    val (address, image) = remember(vm.bitcoinAddressState) { vm.bitcoinAddressState?.let { it.currentAddress to it.image } ?: (null to null) }

    InvoiceHeader(
        text = stringResource(id = R.string.receive_label_bitcoin),
        icon = R.drawable.ic_chain,
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(columnWidth)) {
        QRCodeView(bitmap = image, data = address, loadingLabel = stringResource(id = R.string.receive_bitcoin_generating))
        address?.let {
            Spacer(modifier = Modifier.height(16.dp))
            QRCodeLabel(label = stringResource(R.string.receive_bitcoin_address_label)) {
                Text(
                    text = it,
                    maxLines = 3,
                    overflow = TextOverflow.MiddleEllipsis,
                    style = MaterialTheme.typography.body2.copy(fontSize = 14.sp),
                )
            }
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    address?.let {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CopyShareButtons(
                onCopy = { copyToClipboard(context, data = it) },
                onShare = { share(context, "bitcoin:$it", context.getString(R.string.receive_bitcoin_share_subject), context.getString(R.string.receive_bitcoin_share_title)) },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
