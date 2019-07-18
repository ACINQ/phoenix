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

import fr.acinq.eclair.phoenix.R

enum class NotificationTypes(val priority: Int, val messageResId: Int, val imageResId: Int, val actionResId: Int?) {
  NO_PIN_SET(1, R.string.notifications_set_up_pin, R.drawable.ic_alert_triangle, null), //R.string.notifications_set_up_pin_action),
  MNEMONICS_NEVER_SEEN(1, R.string.notifications_mnemonics_never_seen, R.drawable.ic_alert_triangle, R.string.notifications_mnemonics_never_seen_action),
  MNEMONICS_REMINDER(2, R.string.notifications_mnemonics_reminder, R.drawable.ic_alert_triangle, null),
}
