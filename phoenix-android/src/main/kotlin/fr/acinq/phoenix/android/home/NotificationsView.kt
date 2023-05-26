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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.Converter.toPrettyString
import fr.acinq.phoenix.android.utils.Converter.toRelativeDateString
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.android.utils.safeLet
import fr.acinq.phoenix.data.Notification
import kotlinx.coroutines.launch


@Composable
fun NotificationsView(
    noticesViewModel: NoticesViewModel,
    onBackClick: () -> Unit,
) {
    val log = logger("NotificationsView")
    val notificationsManager = business.notificationsManager
    // TODO: filter rejected payments where the fee policy was X, but the fee policy is now Y
    val notifications by notificationsManager.notifications.collectAsState(emptyList())

    DefaultScreenLayout(isScrollable = false) {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.inappnotif_title))
        LazyColumn {
            // -- notices
            val notices = noticesViewModel.notices.values.toList()
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

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterialApi::class)
@Composable
private fun PermamentNotice(
    notice: Notice
) {
    val context = LocalContext.current
    val nc = LocalNavController.current
    when (notice) {
        Notice.BackupSeedReminder -> {
            ImportantNotification(
                icon = R.drawable.ic_key,
                message = stringResource(id = R.string.inappnotif_backup_seed_message),
                actionText = stringResource(id = R.string.inappnotif_backup_seed_action),
                onActionClick = { nc?.navigate(Screen.DisplaySeed.route) }
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
            val notificationPermission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
            if (!notificationPermission.status.isGranted) {
                val isPermissionDenied = notificationPermission.status.shouldShowRationale
                val scope = rememberCoroutineScope()
                val dismissState = rememberDismissState(
                    confirmStateChange = {
                        if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                            scope.launch {
                                UserPrefs.saveShowNotificationPermissionReminder(context, false)
                            }
                        }
                        true
                    }
                )
                SwipeToDismiss(
                    state = dismissState,
                    background = {},
                    dismissThresholds = { FractionalThreshold(0.8f) }
                ) {
                    ImportantNotification(
                        icon = R.drawable.ic_notification,
                        message = stringResource(id = R.string.inappnotif_notification_permission_message),
                        actionText = stringResource(id = R.string.inappnotif_notification_permission_enable),
                        onActionClick = {
                            if (isPermissionDenied || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
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
        Notice.MempoolFull -> {
            ImportantNotification(
                icon = R.drawable.ic_alert_triangle,
                message = stringResource(id = R.string.inappnotif_mempool_full_message),
                actionText = stringResource(id = R.string.inappnotif_mempool_full_action),
                onActionClick = { openLink(context, "https://phoenix.acinq.co/faq#high-mempool-size-impacts") }
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
    val btcUnit = LocalBitcoinUnit.current
    when (notification) {
        is Notification.PaymentRejected -> {
            val btcUnit = LocalBitcoinUnit.current
            PaymentRejectedNotification(
                title = stringResource(id = R.string.inappnotif_payment_rejected_title, notification.amount.toPrettyString(btcUnit, withUnit = true)),
                body = when (notification) {
                    is Notification.RejectedManually -> stringResource(id = R.string.inappnotif_payment_rejected_by_user)
                    is Notification.FeePolicyDisabled -> stringResource(id = R.string.inappnotif_payment_rejected_disabled)
                    is Notification.FeeTooExpensive -> stringResource(
                        id = R.string.inappnotif_payment_rejected_too_expensive,
                        notification.expectedFee.toPrettyString(btcUnit, withUnit = true),
                        notification.maxAllowedFee.toPrettyString(btcUnit, withUnit = true),
                    )
                    is Notification.ChannelsInitializing -> stringResource(id = R.string.inappnotif_payment_rejected_channel_initializing)
                },
                bottomText = when (notification) {
                    is Notification.ChannelsInitializing -> stringResource(id = R.string.inappnotif_payment_rejected_tweak_setting)
                    is Notification.FeeTooExpensive, is Notification.FeePolicyDisabled -> stringResource(id = R.string.inappnotif_payment_rejected_tweak_setting)
                    else -> null
                },
                timestamp = notification.createdAt,
                onRead = { onNotificationRead(notification.id) },
                extraActionClick = when (notification) {
                    is Notification.FeePolicyDisabled -> {
                        { nc?.navigate(Screen.LiquidityPolicy.route) }
                    }
                    is Notification.FeeTooExpensive -> {
                        { nc?.navigate(Screen.LiquidityPolicy.route) }
                    }
                    else -> null
                }
            )
        }
        else -> TODO()
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
            PhoenixIcon(resourceId = icon, tint = MaterialTheme.colors.primary, modifier = Modifier
                .size(18.dp)
                .offset(y = 1.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.alignByBaseline()) {
                Text(text = message, style = MaterialTheme.typography.body1.copy(fontSize = 16.sp))
                safeLet(actionText, onActionClick) { text, onClick ->
                    Button(
                        text = text, textStyle = MaterialTheme.typography.body2.copy(fontSize = 16.sp),
                        modifier = Modifier.offset(x = (-16).dp),
                        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        onClick = onClick,
                    )
                } ?: Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun PaymentRejectedNotification(
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
            Row {
                Text(text = title, style = MaterialTheme.typography.body2)
                Spacer(modifier = Modifier
                    .widthIn(min = 8.dp)
                    .weight(1f))
                Text(text = timestamp.toRelativeDateString(), style = MaterialTheme.typography.caption.copy(fontSize = 12.sp))
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = body, style = MaterialTheme.typography.body1.copy(fontSize = 15.sp))
            bottomText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.caption.copy(fontSize = 14.sp))
            }
        }
    }
}
