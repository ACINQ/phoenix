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

package fr.acinq.phoenix.android.components.buttons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.wallet.CompactWalletViewWithBalance
import fr.acinq.phoenix.android.utils.mutedTextColor

/** Button for navigation purpose, with the back arrow. */
@Composable
fun BackButton(onClick: () -> Unit, backgroundColor: Color = Color.Transparent) {
    BackHandler(onBack = onClick)
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 50.dp, bottomEnd = 50.dp, bottomStart = 0.dp),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor,
            disabledBackgroundColor = Color.Unspecified,
            contentColor = MaterialTheme.colors.onSurface,
            disabledContentColor = mutedTextColor,
        ),
        elevation = null,
        modifier = Modifier.size(width = 58.dp, height = 52.dp)
    ) {
        PhoenixIcon(resourceId = R.drawable.ic_arrow_back, Modifier.width(24.dp))
    }
}

@Composable
fun BackButtonWithActiveWallet(
    onBackClick: () -> Unit,
    walletId: WalletId,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onClick = onBackClick)
        Spacer(Modifier.weight(1f))
        CompactWalletViewWithBalance(
            walletId = walletId,
            showBalance = true,
            showInbound = false,
        )
        Spacer(Modifier.width(16.dp))
    }
}