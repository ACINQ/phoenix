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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata

@Composable
fun ActiveWalletView(
    nodeId: String,
    walletMetadata: UserWalletMetadata?,
    onClick: () -> Unit,
    showMoreButton: Boolean,
) {

    val metadata = walletMetadata ?: UserWalletMetadata(nodeId = nodeId, name = null, createdAt = null)
    var showMoreMenu by remember { mutableStateOf(false) }
    var showWalletEditDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        Clickable(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                Surface(
                    color = metadata.color,
                    shape = CircleShape,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_wallet),
                        contentDescription = "wallet",
                        modifier = Modifier.padding(8.dp).size(18.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = metadata.name?.takeIf { it.isNotBlank() } ?: "Unnamed Wallet", modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.body2)
                    Spacer(Modifier.height(2.dp))
                    Text(text = nodeId, modifier = Modifier.widthIn(max = 200.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.subtitle2)
                }
            }
        }
        if (showMoreButton) {
            Spacer(Modifier.width(32.dp))
            Button(
                icon = R.drawable.ic_menu_dots,
                shape = CircleShape,
                padding = PaddingValues(8.dp, 12.dp),
                backgroundColor = Color.Transparent,
                onClick = { showMoreMenu = true },
            )
            Box(contentAlignment = Alignment.TopEnd) {
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(onClick = { showWalletEditDialog = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        TextWithIcon(text = "Edit wallet...", icon = R.drawable.ic_edit, textStyle = MaterialTheme.typography.body1)
                    }
                    DropdownMenuItem(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        TextWithIcon(text = "Switch wallet...", icon = R.drawable.ic_swap, textStyle = MaterialTheme.typography.body1)
                    }
                }
            }
        }
    }

    if (showWalletEditDialog) {
        EditWalletDialog(
            onDismiss = { showWalletEditDialog = false },
            nodeId = nodeId,
            metadata = metadata,
        )
    }
}