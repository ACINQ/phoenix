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

package fr.acinq.eclair.phoenix.main

import android.content.Context
import android.preference.PreferenceManager
import android.text.format.DateUtils
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.acinq.eclair.db.Payment
import fr.acinq.eclair.phoenix.background.ChannelsWatcher
import fr.acinq.eclair.phoenix.utils.Prefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.DateFormat
import java.util.*
import kotlin.collections.HashSet

class MainViewModel : ViewModel() {
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  private val MNEMONICS_REMINDER_INTERVAL = DateUtils.DAY_IN_MILLIS * 30
  private val DELAY_BEFORE_BACKGROUND_WARNING = DateUtils.DAY_IN_MILLIS * 5

  val notifications = MutableLiveData(HashSet<NotificationTypes>())
  val payments = MutableLiveData<List<Payment>>()

  fun updateNotifications(context: Context) {
    checkWalletIsSecure(context)
    checkMnemonics(context)
    checkBackgroundWorkerCanRun(context)
  }

  /**
   * If the background channels watcher has not run since (now) - (DELAY_BEFORE_BACKGROUND_WARNING), we consider that the device is
   * blocking this application from working in background, and show a notification.
   *
   * Some devices vendors are known to aggressively kill applications (including background jobs) in order to save battery,
   * unless the app is whitelisted by the user in a custom OS setting page. This behaviour is hard to detect and not
   * standard, and does not happen on a stock android. In this case, the user has to whitelist the app.
   */
  private fun checkBackgroundWorkerCanRun(context:Context) {
    val channelsWatchOutcome = Prefs.getWatcherLastAttemptOutcome(context)
    if (channelsWatchOutcome.second > 0 && System.currentTimeMillis() - channelsWatchOutcome.second > DELAY_BEFORE_BACKGROUND_WARNING) {
      log.warn("watcher has not run since {}", DateFormat.getDateTimeInstance().format(Date(channelsWatchOutcome.second)))
      notifications.value?.add(NotificationTypes.BACKGROUND_WORKER_CANNOT_RUN)
    } else {
      notifications.value?.remove(NotificationTypes.BACKGROUND_WORKER_CANNOT_RUN)
    }
  }

  private fun checkWalletIsSecure(context: Context) {
    if (!Prefs.getIsSeedEncrypted(context)) {
      notifications.value?.add(NotificationTypes.NO_PIN_SET)
    } else {
      notifications.value?.remove(NotificationTypes.NO_PIN_SET)
    }
  }

  private fun checkMnemonics(context: Context) {
    val timestamp = Prefs.getMnemonicsSeenTimestamp(context)

    if (timestamp == 0L) {
      notifications.value?.add(NotificationTypes.MNEMONICS_NEVER_SEEN)
    } else {
      notifications.value?.remove(NotificationTypes.MNEMONICS_NEVER_SEEN)
      if (System.currentTimeMillis() - timestamp > MNEMONICS_REMINDER_INTERVAL) {
        //notifications.value?.add(NotificationTypes.MNEMONICS_REMINDER)
      } else {
        notifications.value?.remove(NotificationTypes.MNEMONICS_REMINDER)
      }
    }
  }

}
