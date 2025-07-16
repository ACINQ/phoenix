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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.positiveColor

@Composable
fun AvailableWalletView(nodeId: String, walletMetadata: UserWalletMetadata?, isCurrent: Boolean, isActive: Boolean, onClick: (String) -> Unit) {
    Clickable(
        onClick = { onClick(nodeId) },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        val metadata = walletMetadata ?: UserWalletMetadata(nodeId = nodeId, name = null, createdAt = null)
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = metadata.color,
                    shape = CircleShape,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_wallet),
                        contentDescription = "wallet",
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colors.onPrimary)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = metadata.name?.takeIf { it.isNotBlank() } ?: "Unnamed Wallet", modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.body2)
                    Spacer(Modifier.height(2.dp))
                    Text(text = nodeId, modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.subtitle2.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
//                    metadata?.createdAt?.let {
//                        Spacer(Modifier.height(2.dp))
//                        Text(text = it.toAbsoluteDateString(), modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.subtitle2)
//                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            if (isCurrent) {
                Image(painter = painterResource(R.drawable.ic_check), contentDescription = "Current active wallet", modifier = Modifier.padding(8.dp).size(18.dp), colorFilter = ColorFilter.tint(positiveColor))
            }
            Button(icon = R.drawable.ic_menu_dots, onClick = { }, modifier = Modifier.fillMaxHeight())
        }
    }
}
