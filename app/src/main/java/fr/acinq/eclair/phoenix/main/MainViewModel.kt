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
import android.text.format.DateUtils
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.acinq.eclair.phoenix.utils.Prefs

class MainViewModel : ViewModel() {

  private val MNEMONICS_REMINDER_INTERVAL = DateUtils.DAY_IN_MILLIS * 30

  val notifications = MutableLiveData(HashSet<NotificationTypes>())

  fun updateNotifications(context: Context) {
    checkWalletIsSecure(context)
    checkMnemonics(context)
  }

  private fun checkWalletIsSecure(context: Context) {
    if (!Prefs.isSeedEncrypted(context)) {
      notifications.value?.add(NotificationTypes.NO_PIN_SET)
    }
  }

  private fun checkMnemonics(context: Context) {
    val timestamp = Prefs.getMnemonicsSeenTimestamp(context)
    when {
      timestamp == 0L -> notifications.value?.add(NotificationTypes.MNEMONICS_NEVER_SEEN)
      System.currentTimeMillis() - timestamp > MNEMONICS_REMINDER_INTERVAL -> notifications.value?.add(NotificationTypes.MNEMONICS_REMINDER)
    }
  }

}
