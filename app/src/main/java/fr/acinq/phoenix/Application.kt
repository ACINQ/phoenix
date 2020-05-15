/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.phoenix

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.Logging
import org.slf4j.LoggerFactory

class Application : Application() {

  val log = LoggerFactory.getLogger(Application::class.java)

  override fun onCreate() {
    super.onCreate()
    init()
    log.info("app created")
  }

  private fun init() {
    Logging.setupLogger(applicationContext)
    Converter.refreshCoinPattern(applicationContext)

    // notification channels (android 8+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channelWatcherChannel = NotificationChannel(Constants.WATCHER_NOTIFICATION_CHANNEL_ID,
        getString(R.string.notification_channels_watcher_title), NotificationManager.IMPORTANCE_HIGH)
      channelWatcherChannel.description = getString(R.string.notification_channels_watcher_desc)

      // Register notifications channels with the system
      getSystemService(NotificationManager::class.java)?.createNotificationChannel(channelWatcherChannel)
    }
  }
}
