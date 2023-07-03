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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.Notice
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.Screen
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.openLink
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.borderColor
import fr.acinq.phoenix.data.Notification

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NoticesButtonRow(
    modifier: Modifier = Modifier,
    notices: List<Notice>,
    notifications: List<Pair<Set<UUID>, Notification>>,
    onNavigateToNotificationsList: () -> Unit,
) {
    val filteredNotices = notices.filterIsInstance<Notice.ShowInHome>()
    // don't display anything if there are no permanent notices
    if (filteredNotices.isEmpty()) return

    val navController = navController
    val context = LocalContext.current
    val notificationPermission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
    val elementsCount = notices.size + notifications.size

    val onClick: (() -> Unit)? = if (elementsCount == 1 && filteredNotices.isNotEmpty()) {
        when (filteredNotices.first()) {
            is Notice.BackupSeedReminder -> {
                { navController.navigate(Screen.DisplaySeed.route) }
            }
            is Notice.CriticalUpdateAvailable, is Notice.UpdateAvailable -> {
                { openLink(context, "https://play.google.com/store/apps/details?id=fr.acinq.phoenix.mainnet") }
            }
            is Notice.NotificationPermission -> {
                val isPermissionDenied = notificationPermission.status.isGranted
                if (isPermissionDenied || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    }
                } else {
                    { notificationPermission.launchPermissionRequest() }
                }
            }
            else -> null
        }
    } else {
        onNavigateToNotificationsList
    }

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colors.surface)
            .fillMaxWidth()
            .then(if (onClick != null) {
                Modifier.clickable(
                    onClick = onClick,
                    role = Role.Button,
                    onClickLabel = "Show notifications",
                )
            } else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filteredNotices.firstOrNull()?.let { notice ->
            when (notice) {
                Notice.MempoolFull -> {
                    NoticeView(text = stringResource(id = R.string.inappnotif_mempool_full_message), icon = R.drawable.ic_alert_triangle)
                }
                Notice.UpdateAvailable -> {
                    NoticeView(text = stringResource(id = R.string.inappnotif_upgrade_message), icon = R.drawable.ic_restore)
                }
                Notice.CriticalUpdateAvailable -> {
                    NoticeView(text = stringResource(id = R.string.inappnotif_upgrade_critical_message), icon = R.drawable.ic_restore)
                }
                Notice.BackupSeedReminder -> {
                    NoticeView(text = stringResource(id = R.string.inappnotif_backup_seed_message), icon = R.drawable.ic_key)
                }
                Notice.NotificationPermission -> {
                    NoticeView(text = stringResource(id = R.string.inappnotif_notification_permission_message), icon = R.drawable.ic_notification)
                }
            }
        }

        if (elementsCount > 1) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                Modifier
                    .height(18.dp)
                    .width(1.dp)
                    .background(color = borderColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "+${elementsCount - 1}",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colors.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onPrimary, fontSize = 14.sp)
            )
        }
    }
}

@Composable
private fun RowScope.NoticeView(
    text: String,
    icon: Int? = null,
) {
    if(icon != null) {
        PhoenixIcon(resourceId = icon, tint = MaterialTheme.colors.primary)
        Spacer(modifier = Modifier.width(10.dp))
    }
    Text(text = text,
        style = MaterialTheme.typography.body1.copy(fontSize = 14.sp),
        modifier = Modifier.weight(1f),
    )
}
