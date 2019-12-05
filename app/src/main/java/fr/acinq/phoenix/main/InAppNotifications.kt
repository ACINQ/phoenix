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

package fr.acinq.phoenix.main

import fr.acinq.phoenix.R

// priority sets the order in the notification list. The lower the more important, i.e must be higher up in the list.
enum class InAppNotifications(val priority: Int, val messageResId: Int, val imageResId: Int, val actionResId: Int?) {
  UPGRADE_WALLET_CRITICAL(1, R.string.inappnotif_upgrade_critical, R.drawable.ic_alert_triangle, null),
  MNEMONICS_NEVER_SEEN(2, R.string.inappnotif_mnemonics_never_seen, R.drawable.ic_alert_triangle, R.string.inappnotif_mnemonics_never_seen_action),
  NO_PIN_SET(3, R.string.inappnotif_set_up_pin, R.drawable.ic_unlock, R.string.inappnotif_set_up_pin_action),
  MNEMONICS_REMINDER(3, R.string.inappnotif_mnemonics_reminder, R.drawable.ic_alert_triangle, null),
  BACKGROUND_WORKER_CANNOT_RUN(3, R.string.inappnotif_background_worker_cannot_run, R.drawable.ic_battery_charging, null),
  UPGRADE_WALLET(3, R.string.inappnotif_upgrade, R.drawable.ic_refresh, null),
}
