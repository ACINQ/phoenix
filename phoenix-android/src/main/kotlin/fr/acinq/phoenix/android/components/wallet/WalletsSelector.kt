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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.UserWallet
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.HSeparator
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.datastore.getByWalletIdOrDefault

@Composable
fun WalletsSelector(
    modifier: Modifier = Modifier,
    wallets: Map<WalletId, UserWallet>,
    walletsMetadata: Map<WalletId, UserWalletMetadata>,
    activeWalletId: WalletId?,
    canEdit: Boolean,
    onWalletClick: (UserWallet) -> Unit,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    topContent: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
) {

    val currentWallet = remember(wallets) { wallets.entries.firstOrNull { it.key == activeWalletId }?.value }
    val otherWalletsList = remember(wallets, walletsMetadata) { wallets.entries.filterNot { it.key == activeWalletId || walletsMetadata[it.key]?.isHidden == true }.toList() }

    LazyColumn(modifier = modifier, verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment) {
        topContent?.let {
            item { it.invoke() }
        }
        if (currentWallet != null) {
            item {
                AvailableWalletView(
                    userWallet = currentWallet,
                    metadata = walletsMetadata.getByWalletIdOrDefault(currentWallet.walletId),
                    isCurrent = true,
                    canEdit = canEdit,
                    onClick = { onWalletClick(currentWallet) }
                )
                Spacer(Modifier.height(12.dp))
                HSeparator()
                Spacer(Modifier.height(8.dp))
            }
        }
        items(items = otherWalletsList) { (walletId, userWallet) ->
            AvailableWalletView(
                userWallet = userWallet,
                metadata = walletsMetadata.getByWalletIdOrDefault(walletId),
                isCurrent = false,
                canEdit = canEdit,
                onClick = { onWalletClick(userWallet) }
            )
            Spacer(Modifier.height(8.dp))
        }
        bottomContent?.let {
            item { it.invoke() }
        }
    }
}

@Composable
private fun AvailableWalletView(
    modifier: Modifier = Modifier,
    userWallet: UserWallet,
    metadata: UserWalletMetadata,
    isCurrent: Boolean,
    canEdit: Boolean,
    onClick: (WalletId) -> Unit,
) {
    var showWalletEditDialog by remember { mutableStateOf(false) }
    if (showWalletEditDialog) {
        EditWalletDialog(
            onDismiss = { showWalletEditDialog = false },
            walletId = userWallet.walletId,
            metadata = metadata,
        )
    }

    Clickable(
        modifier = modifier,
        onClick = {
            if (isCurrent) {
                if (canEdit) showWalletEditDialog = true else return@Clickable
            } else {
                onClick(userWallet.walletId)
            }
        },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.surface,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WalletAvatar(avatar = metadata.avatar, backgroundColor = Color.Transparent, internalPadding = PaddingValues(4.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = metadata.nameOrDefault(), modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.body2)
                    Spacer(Modifier.height(2.dp))
                    Text(text = userWallet.nodeId, modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.subtitle2.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                }
            }
            if (isCurrent && canEdit) {
                Spacer(Modifier.width(8.dp))
                PhoenixIcon(R.drawable.ic_edit, tint = MaterialTheme.colors.primary)
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}
