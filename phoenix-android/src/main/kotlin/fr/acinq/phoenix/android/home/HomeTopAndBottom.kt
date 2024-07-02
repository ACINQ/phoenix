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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.Button
import fr.acinq.phoenix.android.components.Dialog
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.VSeparator
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.utils.isBadCertificate
import fr.acinq.phoenix.android.utils.negativeColor
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
    isFCMUnavailable: Boolean,
    isPowerSaverMode: Boolean,
    inFlightPaymentsCount: Int,
    showRequestLiquidity: Boolean,
    onRequestLiquidityClick: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(40.dp)
            .clipToBounds()
    ) {
        ConnectionBadge(
            onConnectionsStateButtonClick = onConnectionsStateButtonClick,
            connections = connections,
            electrumBlockheight = electrumBlockheight,
            onTorClick = onTorClick,
            isTorEnabled = isTorEnabled,
        )

        if (inFlightPaymentsCount > 0) {
            InflightPaymentsBadge(inFlightPaymentsCount)
        }

        BackgroundRestrictionBadge(
            isFCMUnavailable = isFCMUnavailable,
            isTorEnabled = isTorEnabled == true,
            isPowerSaverMode = isPowerSaverMode
        )

        Spacer(modifier = Modifier.weight(1f))

        if (showRequestLiquidity) {
            BorderButton(
                text = stringResource(id = R.string.home_request_liquidity),
                icon = R.drawable.ic_bucket,
                onClick = onRequestLiquidityClick,
                textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp, color = MaterialTheme.colors.onSurface),
                backgroundColor = MaterialTheme.colors.surface,
                space = 8.dp,
                padding = PaddingValues(8.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        TopBadgeButton(
            text = stringResource(R.string.home__faq_button),
            icon = R.drawable.ic_help_circle,
            iconTint = MaterialTheme.colors.onSurface,
            onClick = { openLink(context, "https://phoenix.acinq.co/faq") },
        )
    }
}

@Composable
private fun ConnectionBadge(
    onConnectionsStateButtonClick: () -> Unit,
    connections: Connections,
    electrumBlockheight: Int,
    onTorClick: () -> Unit,
    isTorEnabled: Boolean?,
) {
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

    if (connections.electrum !is Connection.ESTABLISHED || connections.peer !is Connection.ESTABLISHED) {
        val electrumConnection = connections.electrum
        val isBadElectrumCert = electrumConnection is Connection.CLOSED && electrumConnection.isBadCertificate()
        TopBadgeButton(
            text = stringResource(id = if (isBadElectrumCert) R.string.home__connection__bad_cert else R.string.home__connection__connecting),
            icon = if (isBadElectrumCert) R.drawable.ic_alert_triangle else R.drawable.ic_connection_lost,
            iconTint = if (isBadElectrumCert) negativeColor else MaterialTheme.colors.onSurface,
            onClick = onConnectionsStateButtonClick,
            modifier = Modifier.alpha(connectionsButtonAlpha)
        )
    } else if (electrumBlockheight < 795_000) {
        // FIXME use a dynamic blockheight ^
        TopBadgeButton(
            text = stringResource(id = R.string.home__connection__electrum_late),
            icon = R.drawable.ic_alert_triangle,
            iconTint = warningColor,
            onClick = onConnectionsStateButtonClick,
            modifier = Modifier.alpha(connectionsButtonAlpha)
        )
    } else if (isTorEnabled == true) {
        if (connections.tor is Connection.ESTABLISHED) {
            TopBadgeButton(
                text = stringResource(id = R.string.home__connection__tor_active),
                icon = R.drawable.ic_tor_shield_ok,
                iconTint = positiveColor,
                onClick = onTorClick,
            )
        }
    }

    Spacer(modifier = Modifier.width(4.dp))
}

@Composable
private fun TopBadgeButton(
    text: String?,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colors.primary,
) {
    FilledButton(
        text = text,
        icon = icon,
        iconTint = iconTint,
        onClick = onClick,
        textStyle = MaterialTheme.typography.button.copy(fontSize = 12.sp),
        backgroundColor = MaterialTheme.colors.surface,
        space = 8.dp,
        padding = PaddingValues(8.dp),
        modifier = modifier.widthIn(max = 120.dp),
        maxLines = 1
    )
}

@Composable
private fun BackgroundRestrictionBadge(
    isTorEnabled: Boolean,
    isPowerSaverMode: Boolean,
    isFCMUnavailable: Boolean,
) {
    if (isTorEnabled || isPowerSaverMode || isFCMUnavailable) {
        var showDialog by remember { mutableStateOf(false) }

        TopBadgeButton(
            text = null,
            icon = R.drawable.ic_alert_triangle,
            iconTint = warningColor,
            onClick = { showDialog = true },
        )
        Spacer(modifier = Modifier.width(4.dp))

        if (showDialog) {
            Dialog(
                onDismiss = { showDialog = false },
                title = stringResource(id = R.string.home_background_restriction_title)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(text = stringResource(id = R.string.home_background_restriction_body_1))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = stringResource(id = R.string.home_background_restriction_body_2))
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isTorEnabled) {
                            TextWithIcon(text = stringResource(id = R.string.home_background_restriction_tor), icon = R.drawable.ic_tor_shield_ok)
                        }
                        if (isPowerSaverMode) {
                            TextWithIcon(text = stringResource(id = R.string.home_background_restriction_powersaver), icon = R.drawable.ic_battery_charging)
                        }
                        if (isFCMUnavailable) {
                            TextWithIcon(text = stringResource(id = R.string.home_background_restriction_fcm), icon = R.drawable.ic_cloud_off)
                            Text(
                                text = stringResource(id = R.string.home_background_restriction_fcm_details),
                                style = MaterialTheme.typography.caption.copy(fontSize = 14.sp),
                                modifier = Modifier.padding(start = 26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InflightPaymentsBadge(
    count: Int,
) {
    var showInflightPaymentsDialog by remember { mutableStateOf(false) }

    TopBadgeButton(
        text = "$count",
        icon = R.drawable.ic_send,
        onClick = { showInflightPaymentsDialog = true },
    )
    Spacer(modifier = Modifier.width(4.dp))

    if (showInflightPaymentsDialog) {
        Dialog(onDismiss = { showInflightPaymentsDialog = false }) {
            Text(
                text = stringResource(id = R.string.home_inflight_payments, count),
                modifier = Modifier.padding(24.dp)
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
            .height(82.dp)
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