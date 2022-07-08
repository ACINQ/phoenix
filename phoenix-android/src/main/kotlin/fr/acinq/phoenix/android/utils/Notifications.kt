/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.R

object Notifications {
    const val HEADLESS_NOTIF_ID = 354321
    const val HEADLESS_NOTIF_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.FCM_NOTIF"

    fun registerNotificationChannels(context: Context) {
        // notification channels (android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(
                listOf(
                    NotificationChannel(HEADLESS_NOTIF_CHANNEL_ID, context.getString(R.string.notification_headless_title), NotificationManager.IMPORTANCE_HIGH).apply {
                        description = context.getString(R.string.notification_headless_desc)
                    },
                )
            )
        }
    }
}