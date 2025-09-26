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

package fr.acinq.phoenix.android.components.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.android.LocalBusiness
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.AmountView
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.components.dialogs.PopupBasicDialog
import fr.acinq.phoenix.android.globalPrefs
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.datastore.getByWalletIdOrDefault
import fr.acinq.phoenix.android.utils.monoTypo
import kotlinx.coroutines.flow.flowOf

@Composable
fun WalletView(
    walletId: WalletId,
    modifier: Modifier = Modifier,
    internalPadding: PaddingValues = PaddingValues(12.dp),
    avatarBackgroundColor: Color = MaterialTheme.colors.surface,
    avatarInternalPadding: PaddingValues = PaddingValues(10.dp),
) {
    val metadataMap by globalPrefs.getAvailableWalletsMeta.collectAsState(emptyMap())
    val metadata = metadataMap.getByWalletIdOrDefault(walletId)

    Row(modifier = modifier.padding(internalPadding), verticalAlignment = Alignment.CenterVertically) {
        WalletAvatar(metadata.avatar, backgroundColor = avatarBackgroundColor, internalPadding = avatarInternalPadding)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = metadata.nameOrDefault(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(2.dp))
            val nodeParams by (LocalBusiness.current?.nodeParamsManager?.nodeParams ?: flowOf(null)).collectAsState(null)
            nodeParams?.nodeId?.toString()?.let {
                Text(text = it, modifier = Modifier.widthIn(max = 250.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = monoTypo.copy(color = MaterialTheme.typography.caption.color))
            }
        }
    }
}

@Composable
fun ClickableWalletView(
    walletId: WalletId,
    onClick: () -> Unit,
) {
    Clickable(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        WalletView(walletId)
    }
}

@Composable
fun CompactWalletViewWithBalance(
    walletId: WalletId,
    showBalance: Boolean,
    showInbound: Boolean,
) {
    val metadataMap by globalPrefs.getAvailableWalletsMeta.collectAsState(emptyMap())
    val metadata = metadataMap.getByWalletIdOrDefault(walletId)

    var showDetailsDialog by remember { mutableStateOf(false) }

    Clickable(onClick = { showDetailsDialog = true }) {
        WalletAvatar(avatar = metadata.avatar, fontSize = 20.sp, borderColor = if (showDetailsDialog) MaterialTheme.colors.primary else Color.Transparent, internalPadding = PaddingValues(8.dp))
    }

    if (showDetailsDialog) {
        ActiveWalletBalanceDialog(onDismiss = { showDetailsDialog = false }, showBalance = showBalance, showInbound = showInbound, metadata = metadata)
    }
}

@Composable
private fun ActiveWalletBalanceDialog(
    showBalance: Boolean,
    showInbound: Boolean,
    metadata: UserWalletMetadata,
    onDismiss: () -> Unit,
) {
    val business = LocalBusiness.current
    if (business != null) {
        PopupBasicDialog(
            onDismiss = onDismiss,
            internalPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            borderStroke = null,
        ) {
            val balance by business.balanceManager.balance.collectAsState()
            val channelsState by business.peerManager.channelsFlow.collectAsState()
            val inboundLiquidity = remember(channelsState) { channelsState?.values?.mapNotNull { it.availableForReceive }?.sum() }

            Text(text = metadata.nameOrDefault(), style = MaterialTheme.typography.h4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            HSeparator(width = 100.dp)

            if (showBalance) {
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.send_balance_prefix), style = MaterialTheme.typography.subtitle2)
                Spacer(Modifier.height(4.dp))
                Row {
                    PhoenixIcon(resourceId = R.drawable.ic_send)
                    Spacer(Modifier.width(4.dp))
                    balance?.let { AmountView(amount = it) } ?: ProgressView(stringResource(R.string.utils_loading_data))
                }
            }

            if (showInbound) {
                Spacer(Modifier.height(8.dp))
                Text(text = "Inbound liquidity", style = MaterialTheme.typography.subtitle2)
                Spacer(Modifier.height(4.dp))
                Row {
                    PhoenixIcon(resourceId = R.drawable.ic_bucket)
                    Spacer(Modifier.width(4.dp))
                    inboundLiquidity?.let { AmountView(amount = it) } ?: ProgressView(stringResource(R.string.utils_loading_data))
                }
            }
        }
    }
}
