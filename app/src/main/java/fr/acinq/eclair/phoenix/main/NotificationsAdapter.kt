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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.phoenix.R

class NotificationsAdapter(private var notifications: MutableSet<NotificationTypes>) : RecyclerView.Adapter<NotificationHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.holder_notification, parent, false)
    return NotificationHolder(view)
  }

  override fun onBindViewHolder(holder: NotificationHolder, position: Int) {
    val notif = this.notifications.elementAt(position)
    holder.bindItem(notif, position)
  }

  override fun getItemCount(): Int {
    return this.notifications.size
  }

  fun update(notifs: MutableSet<NotificationTypes>) {
    if (this.notifications != notifs) {
      this.notifications.clear()
      this.notifications.addAll(notifs)
    }
    notifyDataSetChanged()
  }

}
