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

package fr.acinq.phoenix.android.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.buttons.openLink
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.CardHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import fr.acinq.phoenix.android.home.TorDisconnectedDialog
import fr.acinq.phoenix.android.navigation.Screen
import fr.acinq.phoenix.android.services.ChannelsWatcher
import fr.acinq.phoenix.android.utils.converters.AmountFormatter.toPrettyString
import fr.acinq.phoenix.android.utils.converters.DateFormatter.toAbsoluteDateTimeString
import fr.acinq.phoenix.android.utils.converters.DateFormatter.toRelativeDateString
import fr.acinq.phoenix.android.utils.extensions.safeLet
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.data.WatchTowerOutcome
import kotlinx.coroutines.launch
import java.text.DecimalFormat


@Composable
fun NotificationsView(
    business: PhoenixBusiness,
    noticesViewModel: NoticesViewModel,
    onBackClick: () -> Unit,
) {
    val notificationsManager = business.notificationsManager
    // TODO: filter rejected payments where the fee policy was X, but the fee policy is now Y
    val notices = noticesViewModel.notices.sortedBy { it.priority }
    val notifications by notificationsManager.notifications.collectAsState(emptyList())

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.inappnotif_title))
        if (notices.isEmpty() && notifications.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            TextWithIcon(
                text = stringResource(id = R.string.inappnotif_empty),
                textStyle = MaterialTheme.typography.caption,
                icon = R.drawable.ic_sleep,
                iconTint = MaterialTheme.typography.caption.color,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            LazyColumn {
                // -- notices
                if (notices.isNotEmpty()) {
                    item {
                        CardHeader(text = stringResource(id = R.string.inappnotif_notices_title))
                    }
                }
                items(notices) {
                    PermamentNotice(it)
                }

                // -- some vertical space if needed
                if (notices.isNotEmpty() && notifications.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // -- payments notifications
                if (notifications.isNotEmpty()) {
                    item {
                        CardHeader(text = stringResource(id = R.string.inappnotif_payments_title))
                    }
                }
                items(notifications) {
                    val relatedNotifications = it.first
                    PaymentNotification(it.second, onNotificationRead = { notificationsManager.dismissNotifications(relatedNotifications) })
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterialApi::class)
@Composable
private fun PermamentNotice(
    notice: Notice
) {
    val context = LocalContext.current
    val internalPrefs = LocalInternalPrefs.current
    val userPrefs = LocalUserPrefs.current
    val nc = LocalNavController.current
    val scope = rememberCoroutineScope()

    var showTorDisconnectedDialog by remember { mutableStateOf(false) }
    if (showTorDisconnectedDialog) {
        TorDisconnectedDialog(onDismiss = { showTorDisconnectedDialog = false })
    }

    when (notice) {
        Notice.BackupSeedReminder -> {
            ImportantNotification(
                icon = R.drawable.ic_key,
                message = stringResource(id = R.string.inappnotif_backup_seed_message),
                actionText = stringResource(id = R.string.inappnotif_backup_seed_action),
                onActionClick = { nc?.navigate(Screen.BusinessNavGraph.DisplaySeed.route) }
            )
        }

        Notice.CriticalUpdateAvailable -> {
            ImportantNotification(
                icon = R.drawable.ic_arrow_down_circle,
                message = stringResource(id = R.string.inappnotif_upgrade_critical_message),
                actionText = stringResource(id = R.string.inappnotif_upgrade_button),
                onActionClick = { openLink(context, "https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet") }
            )
        }

        Notice.UpdateAvailable -> {
            ImportantNotification(
                icon = R.drawable.ic_arrow_down_circle,
                message = stringResource(id = R.string.inappnotif_upgrade_message),
                actionText = stringResource(id = R.string.inappnotif_upgrade_button),
                onActionClick = { openLink(context, "https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet") }
            )
        }

        Notice.NotificationPermission -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationPermission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
                if (!notificationPermission.status.isGranted) {
                    val isPermissionDenied = notificationPermission.status.shouldShowRationale
                    val dismissState = rememberDismissState(
                        confirmStateChange = {
                            if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                                scope.launch {
                                    userPrefs?.saveShowNotificationPermissionReminder(false)
                                }
                            }
                            true
                        }
                    )
                    SwipeToDismiss(
                        state = dismissState,
                        background = {},
                        dismissThresholds = { FractionalThreshold(0.8f) },
                    ) {
                        ImportantNotification(
                            icon = R.drawable.ic_notification,
                            message = stringResource(id = R.string.inappnotif_notification_permission_message),
                            actionText = stringResource(id = R.string.inappnotif_notification_permission_enable),
                            onActionClick = {
                                if (isPermissionDenied) {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    })
                                } else {
                                    notificationPermission.launchPermissionRequest()
                                }
                            }
                        )
                    }
                }
            }
        }

        Notice.MempoolFull -> {
            ImportantNotification(
                icon = R.drawable.ic_info,
                message = stringResource(id = R.string.inappnotif_mempool_full_message),
                actionText = stringResource(id = R.string.inappnotif_mempool_full_action),
                onActionClick = { openLink(context, "https://phoenix.acinq.co/faq#is-phoenix-affected-by-high-on-chain-fees") },
            )
        }

        Notice.WatchTowerLate -> {
            ImportantNotification(
                icon = R.drawable.ic_eye,
                message = stringResource(id = R.string.inappnotif_watchtower_late_message),
                actionText = stringResource(id = R.string.inappnotif_watchtower_late_action),
                onActionClick = {
                    scope.launch { internalPrefs?.saveChannelsWatcherOutcome(ChannelsWatcher.Outcome.Nominal(currentTimestampMillis())) }
                }
            )
        }

        is Notice.SwapInCloseToTimeout -> {
            ImportantNotification(
                icon = R.drawable.ic_alert_triangle,
                message = stringResource(id = R.string.inappnotif_swapin_timeout_message),
                actionText = stringResource(id = R.string.inappnotif_swapin_timeout_action),
                onActionClick = { nc?.navigate(Screen.BusinessNavGraph.WalletInfo.SwapInWallet.route) },
            )
        }

        is Notice.RemoteMessage -> {
            ImportantNotification(
                icon = R.drawable.ic_info,
                message = notice.notice.message,
                actionText = stringResource(id = R.string.btn_ok),
                onActionClick = {
                    scope.launch {
                        internalPrefs?.saveLastReadWalletNoticeIndex(notice.notice.index)
                    }
                },
            )
        }

        is Notice.TorDisconnected -> {
            ImportantNotification(
                icon = R.drawable.ic_tor_shield,
                message = stringResource(R.string.inappnotif_tor_disconnected_message),
                actionText = stringResource(R.string.inappnotif_tor_disconnected_action),
                onActionClick = { showTorDisconnectedDialog = true }
            )
        }
    }
}

@Composable
private fun PaymentNotification(
    notification: Notification,
    onNotificationRead: (UUID) -> Unit,
) {
    val nc = LocalNavController.current
    val btcUnit = LocalBitcoinUnits.current.primary
    when (notification) {
        is Notification.PaymentRejected -> {
            DimissibleNotification(
                title = stringResource(
                    id = if (notification.source == LiquidityEvents.Source.OnChainWallet) R.string.inappnotif_payment_onchain_pending_title else R.string.inappnotif_payment_rejected_title,
                    notification.amount.toPrettyString(btcUnit, withUnit = true)
                ),
                body = when (notification) {
                    is Notification.FeePolicyDisabled -> stringResource(id = R.string.inappnotif_payment_rejected_disabled)
                    is Notification.OverAbsoluteFee -> stringResource(
                        id = R.string.inappnotif_payment_rejected_over_absolute,
                        notification.fee.toPrettyString(btcUnit, withUnit = true),
                        notification.maxAbsoluteFee.toPrettyString(btcUnit, withUnit = true),
                    )
                    is Notification.OverRelativeFee -> stringResource(
                        id = R.string.inappnotif_payment_rejected_over_relative,
                        notification.fee.toPrettyString(btcUnit, withUnit = true),
                        DecimalFormat("0.##").format(notification.maxRelativeFeeBasisPoints.toDouble() / 100),
                    )
                    is Notification.MissingOffChainAmountTooLow -> stringResource(id = R.string.notif_rejected_amount_too_low)
                    is Notification.GenericError -> stringResource(id = R.string.notif_rejected_generic_error)
                },
                bottomText = when (notification) {
                    is Notification.OverAbsoluteFee, is Notification.OverRelativeFee, is Notification.FeePolicyDisabled -> {
                        if (notification.source == LiquidityEvents.Source.OnChainWallet) {
                            stringResource(id = R.string.inappnotif_payment_rejected_view_wallet)
                        } else {
                            stringResource(id = R.string.inappnotif_payment_rejected_tweak_setting)
                        }
                    }
                    else -> null
                },
                timestamp = notification.createdAt,
                onRead = { onNotificationRead(notification.id) },
                extraActionClick = when (notification) {
                    is Notification.FeePolicyDisabled, is Notification.OverAbsoluteFee, is Notification.OverRelativeFee -> {
                        if (notification.source == LiquidityEvents.Source.OnChainWallet) {
                            { nc?.navigate(Screen.BusinessNavGraph.WalletInfo.SwapInWallet.route) }
                        } else {
                            { nc?.navigate(Screen.BusinessNavGraph.LiquidityPolicy.route) }
                        }
                    }

                    else -> null
                }
            )
        }

        is WatchTowerOutcome.Nominal -> DimissibleNotification(
            title = stringResource(id = R.string.inappnotif_watchtower_nominal_title),
            body = if (notification.channelsWatchedCount == 1) {
                stringResource(id = R.string.inappnotif_watchtower_nominal_description_one, notification.channelsWatchedCount, notification.createdAt.toAbsoluteDateTimeString())
            } else {
                stringResource(id = R.string.inappnotif_watchtower_nominal_description_many, notification.channelsWatchedCount, notification.createdAt.toAbsoluteDateTimeString())
            },
            timestamp = notification.createdAt,
            onRead = { onNotificationRead(notification.id) },
        )

        is WatchTowerOutcome.RevokedFound -> DimissibleNotification(
            title = stringResource(id = R.string.inappnotif_watchtower_revokedfound_title),
            body = stringResource(id = R.string.inappnotif_watchtower_revokedfound_description, notification.createdAt.toAbsoluteDateTimeString(), notification.channels.joinToString { it.toHex() }),
            timestamp = notification.createdAt,
            onRead = { onNotificationRead(notification.id) },
        )

        is WatchTowerOutcome.Unknown -> {
            // ignored
        }
    }
}

@Composable
private fun ImportantNotification(
    icon: Int,
    message: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        withBorder = true,
        externalPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)) {
            PhoenixIcon(resourceId = icon, tint = MaterialTheme.colors.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.alignByBaseline()) {
                Text(text = message, style = MaterialTheme.typography.body1.copy(fontSize = 16.sp))
                safeLet(actionText, onActionClick) { text, onClick ->
                    fr.acinq.phoenix.android.components.buttons.Button(
                        text = text, textStyle = MaterialTheme.typography.body2.copy(fontSize = 16.sp),
                        icon = R.drawable.ic_chevron_right,
                        modifier = Modifier.offset(x = (-16).dp),
                        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        space = 4.dp,
                        onClick = onClick,
                    )
                } ?: Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DimissibleNotification(
    title: String,
    timestamp: Long,
    body: String,
    bottomText: String? = null,
    onRead: () -> Unit,
    extraActionClick: (() -> Unit)? = null,
) {
    val dismissState = rememberDismissState(
        confirmStateChange = {
            if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                onRead()
            }
            true
        }
    )
    SwipeToDismiss(
        state = dismissState,
        background = {},
        dismissThresholds = { FractionalThreshold(0.8f) }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            externalPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            internalPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            onClick = extraActionClick
        ) {
            Text(text = title, style = MaterialTheme.typography.body2)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = body, style = MaterialTheme.typography.body1.copy(fontSize = 15.sp))
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                bottomText?.let {
                    Text(text = it, style = MaterialTheme.typography.caption.copy(fontSize = 14.sp), modifier = Modifier.alignByBaseline())
                }
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 8.dp)
                )
                Text(
                    text = timestamp.toRelativeDateString(),
                    style = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}
