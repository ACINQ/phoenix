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
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.android.LocalNavController
import fr.acinq.phoenix.android.Notice
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.TextWithIcon
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.data.Notification
import kotlinx.coroutines.launch

@Composable
fun HomeNotices(
    modifier: Modifier = Modifier,
    notices: List<Notice>,
    notifications: List<Pair<Set<UUID>, Notification>>,
    onNavigateToSwapInWallet: () -> Unit,
    onNavigateToNotificationsList: () -> Unit,
) {
    val filteredNotices = notices.filterIsInstance<Notice.ShowInHome>().sortedBy { it.priority }
    val now = currentTimestampMillis()
    val recentRejectedOffchainCount = notifications.map { it.second }
        .filterIsInstance<Notification.PaymentRejected>()
        .filter { it.source == LiquidityEvents.Source.OffChainPayment && (now - it.createdAt) < 15 * DateUtils.HOUR_IN_MILLIS }
        .size

    // don't display anything if there are no permanent notices or rejected offchain payments
    if (filteredNotices.isEmpty() && recentRejectedOffchainCount == 0) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recentRejectedOffchainCount > 0) {
            PaymentsRejectedShortView(recentRejectedOffchainCount, onNavigateToNotificationsList)
        }
        filteredNotices.firstOrNull()?.let {
            FirstNoticeView(
                notice = it,
                messagesCount = notices.size + notifications.size,
                onNavigateToSwapInWallet = onNavigateToSwapInWallet,
                onNavigateToNotificationsList = onNavigateToNotificationsList
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun FirstNoticeView(
    modifier: Modifier = Modifier,
    notice: Notice.ShowInHome,
    messagesCount: Int,
    onNavigateToSwapInWallet: () -> Unit,
    onNavigateToNotificationsList: () -> Unit,
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val onClick = if (messagesCount == 1) {
        when (notice) {
            is Notice.MigrationFromLegacy -> {
                { openLink(context, "https://acinq.co/blog/phoenix-splicing-update") }
            }

            is Notice.BackupSeedReminder -> {
                { navController?.navigate(Screen.DisplaySeed.route) ?: Unit }
            }

            is Notice.CriticalUpdateAvailable, is Notice.UpdateAvailable -> {
                { openLink(context, "https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet") }
            }

            is Notice.NotificationPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationPermission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
                    val isPermissionDenied = notificationPermission.status.isGranted
                    if (isPermissionDenied) {
                        {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            })
                        }
                    } else {
                        { notificationPermission.launchPermissionRequest() }
                    }
                } else null
            }

            is Notice.SwapInCloseToTimeout -> onNavigateToSwapInWallet

            is Notice.MempoolFull -> onNavigateToNotificationsList
        }
    } else {
        onNavigateToNotificationsList
    }

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colors.surface)
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick, role = Role.Button, onClickLabel = "Show notifications")
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        when (notice) {
            Notice.MigrationFromLegacy -> {
                NoticeTextView(text = stringResource(id = R.string.inappnotif_migration_from_legacy), icon = R.drawable.ic_party_popper)
            }

            Notice.MempoolFull -> {
                NoticeTextView(text = stringResource(id = R.string.inappnotif_mempool_full_message), icon = R.drawable.ic_alert_triangle)
            }

            Notice.UpdateAvailable -> {
                NoticeTextView(text = stringResource(id = R.string.inappnotif_upgrade_message), icon = R.drawable.ic_restore)
            }

            Notice.CriticalUpdateAvailable -> {
                NoticeTextView(text = stringResource(id = R.string.inappnotif_upgrade_critical_message), icon = R.drawable.ic_restore)
            }

            Notice.BackupSeedReminder -> {
                NoticeTextView(text = stringResource(id = R.string.inappnotif_backup_seed_message), icon = R.drawable.ic_key)
            }

            Notice.NotificationPermission -> {
                NoticeTextView(text = stringResource(id = R.string.inappnotif_notification_permission_message), icon = R.drawable.ic_notification)
            }

            is Notice.SwapInCloseToTimeout -> {
                NoticeTextView(text = stringResource(id = R.string.inappnotif_swapin_timeout_message), icon = R.drawable.ic_alert_triangle)
            }
        }

        if (messagesCount > 1) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .height(18.dp)
                    .width(1.dp)
                    .background(color = borderColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "+${messagesCount - 1}",
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colors.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onPrimary, fontSize = 14.sp)
            )
        }
    }
}

@Composable
private fun PaymentsRejectedShortView(
    rejectedPaymentsCount: Int,
    onNavigateToNotificationsList: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colors.surface)
            .fillMaxWidth()
            .clickable(onClick = onNavigateToNotificationsList, role = Role.Button, onClickLabel = "Show payments notifications")
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        TextWithIcon(
            text = pluralStringResource(id = R.plurals.inappnotif_payments_rejection_overview, count = rejectedPaymentsCount, rejectedPaymentsCount),
            textStyle = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
            icon = R.drawable.ic_info,
            iconTint = MaterialTheme.colors.primary,
            space = 10.dp
        )
    }
}

@Composable
private fun RowScope.NoticeTextView(
    text: String,
    icon: Int? = null,
) {
    if (icon != null) {
        PhoenixIcon(resourceId = icon, tint = MaterialTheme.colors.primary, modifier = Modifier
            .align(Alignment.Top)
            .offset(y = (2).dp))
        Spacer(modifier = Modifier.width(10.dp))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
    )
}
