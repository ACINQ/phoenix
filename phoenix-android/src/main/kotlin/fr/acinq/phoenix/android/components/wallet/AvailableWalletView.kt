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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.Clickable
import fr.acinq.phoenix.android.utils.datastore.UserWalletMetadata
import fr.acinq.phoenix.android.utils.positiveColor

@Composable
fun AvailableWalletView(
    modifier: Modifier = Modifier,
    nodeId: String,
    metadata: UserWalletMetadata,
    isCurrent: Boolean,
    onClick: (String) -> Unit,
) {
    Clickable(
        modifier = modifier,
        onClick = { if (isCurrent) return@Clickable else onClick(nodeId) },
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).weight(1f), verticalAlignment = Alignment.CenterVertically) {
                WalletAvatar(metadata.avatar, backgroundColor = Color.Transparent, internalPadding = PaddingValues(4.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = metadata.name(), modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.body2)
                    Spacer(Modifier.height(2.dp))
                    Text(text = nodeId, modifier = Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.subtitle2.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                }
            }
            Spacer(Modifier.width(24.dp))
            if (isCurrent) {
                Image(painter = painterResource(R.drawable.ic_check), contentDescription = "Current active wallet", modifier = Modifier.padding(8.dp).size(18.dp), colorFilter = ColorFilter.tint(positiveColor))
            }
        }
    }
}
