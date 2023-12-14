/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.VSeparator
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.isBadCertificate
import fr.acinq.phoenix.android.utils.mutedBgColor
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.orange
import fr.acinq.phoenix.android.utils.positiveColor
import fr.acinq.phoenix.android.utils.warningColor
import fr.acinq.phoenix.managers.Connections

@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    onConnectionsStateButtonClick: () -> Unit,
    connections: Connections,
    electrumBlockheight: Int,
    onTorClick: () -> Unit,
    isTorEnabled: Boolean?,
    onRequestLiquidityClick: () -> Unit,
) {
    val channelsState by business.peerManager.channelsFlow.collectAsState()
    val context = LocalContext.current
    val connectionsTransition = rememberInfiniteTransition(label = "animateConnectionsBadge")
    val connectionsButtonAlpha by connectionsTransition.animateFloat(
        label = "animateConnectionsBadge",
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes { durationMillis = 500 },
            repeatMode = RepeatMode.Reverse
        ),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(40.dp)
            .clipToBounds()
    ) {
        if (connections.electrum !is Connection.ESTABLISHED || connections.peer !is Connection.ESTABLISHED) {
            val electrumConnection = connections.electrum
            val isBadElectrumCert = electrumConnection is Connection.CLOSED && electrumConnection.isBadCertificate()
            FilledButton(
                text = stringResource(id = if (isBadElectrumCert) R.string.home__connection__bad_cert else R.string.home__connection__connecting),
                icon = if (isBadElectrumCert) R.drawable.ic_alert_triangle else R.drawable.ic_connection_lost,
                iconTint = if (isBadElectrumCert) negativeColor else MaterialTheme.colors.onSurface,
                onClick = onConnectionsStateButtonClick,
                textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp, color = if (isBadElectrumCert) negativeColor else MaterialTheme.colors.onSurface),
                backgroundColor = MaterialTheme.colors.surface,
                space = 8.dp,
                padding = PaddingValues(8.dp),
                modifier = Modifier.alpha(connectionsButtonAlpha)
            )
        } else if (electrumBlockheight < 795_000) {
            // FIXME use a dynamic blockheight ^
            FilledButton(
                text = stringResource(id = R.string.home__connection__electrum_late),
                icon = R.drawable.ic_alert_triangle,
                iconTint = warningColor,
                onClick = onConnectionsStateButtonClick,
                textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp),
                backgroundColor = MaterialTheme.colors.surface,
                space = 8.dp,
                padding = PaddingValues(8.dp),
                modifier = Modifier.alpha(connectionsButtonAlpha)
            )
        } else if (isTorEnabled == true) {
            if (connections.tor is Connection.ESTABLISHED) {
                FilledButton(
                    text = stringResource(id = R.string.home__connection__tor_active),
                    icon = R.drawable.ic_tor_shield_ok,
                    iconTint = positiveColor,
                    onClick = onTorClick,
                    textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp),
                    backgroundColor = mutedBgColor,
                    space = 8.dp,
                    padding = PaddingValues(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colors.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!channelsState.isNullOrEmpty()) {
                BorderButton(
                    text = stringResource(id = R.string.home_request_liquidity),
                    icon = R.drawable.ic_arrow_down_circle,
                    onClick = onRequestLiquidityClick,
                    iconTint = MaterialTheme.colors.onSurface,
                    textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp, color = MaterialTheme.colors.onSurface),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 16.dp),
                    space = 8.dp,
                    padding = PaddingValues(8.dp),
                )
            }
            Button(
                text = stringResource(R.string.home__faq_button),
                icon = R.drawable.ic_help_circle,
                iconTint = MaterialTheme.colors.onSurface,
                onClick = { openLink(context, "https://phoenix.acinq.co/faq") },
                textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp),
                space = 8.dp,
                padding = PaddingValues(8.dp),
            )
        }
    }
}

@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colors.surface)
    ) {
        Row {
            Button(
                icon = R.drawable.ic_settings,
                onClick = onSettingsClick,
                iconTint = MaterialTheme.colors.onSurface,
                padding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxHeight()
            )
            VSeparator(PaddingValues(top = 20.dp, bottom = 20.dp))
            Button(
                text = stringResource(id = R.string.menu_receive),
                icon = R.drawable.ic_receive,
                onClick = onReceiveClick,
                iconTint = MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            )
            VSeparator(PaddingValues(top = 20.dp, bottom = 20.dp))
            Button(
                text = stringResource(id = R.string.menu_send),
                icon = R.drawable.ic_scan_qr,
                onClick = onSendClick,
                iconTint = MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            )
        }
        Row(
            Modifier
                .padding(horizontal = 32.dp)
                .align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            ) { }
        }
    }
}