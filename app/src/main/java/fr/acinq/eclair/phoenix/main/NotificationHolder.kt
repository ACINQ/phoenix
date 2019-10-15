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

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.phoenix.R

class NotificationHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

  fun bindItem(notification: NotificationTypes, position: Int) {
    val messageView = itemView.findViewById<TextView>(R.id.notif_message)
    val iconView = itemView.findViewById<ImageView>(R.id.notif_icon)
    val actionButton = itemView.findViewById<Button>(R.id.notif_button)
    val spaceMd = itemView.resources.getDimensionPixelSize(R.dimen.space_md)
    val spaceSm = itemView.resources.getDimensionPixelSize(R.dimen.space_sm)

    if (position == 0) {
      (itemView.layoutParams as ViewGroup.MarginLayoutParams).setMargins(spaceMd, 0, spaceMd, 0)
    } else {
      (itemView.layoutParams as ViewGroup.MarginLayoutParams).setMargins(spaceMd, spaceMd, spaceMd, 0)
    }

    messageView.text = itemView.resources.getString(notification.messageResId)
    iconView.setImageDrawable(itemView.resources.getDrawable(notification.imageResId, itemView.context.theme))

    if (notification.actionResId != null) {
      actionButton.visibility = View.VISIBLE
      actionButton.text = itemView.resources.getString(notification.actionResId)
      actionButton.setOnClickListener {
        when (notification) {
          NotificationTypes.NO_PIN_SET -> { itemView.findNavController().navigate(R.id.action_main_to_seed_security) }
          NotificationTypes.MNEMONICS_NEVER_SEEN -> { itemView.findNavController().navigate(R.id.action_main_to_display_seed) }
          NotificationTypes.MNEMONICS_REMINDER -> {}
        }
      }
      itemView.setPadding(spaceSm, spaceSm, spaceSm, 0)
    } else {
      actionButton.visibility = View.GONE
      itemView.setPadding(spaceSm, spaceSm, spaceSm, spaceSm)
    }
  }
}
