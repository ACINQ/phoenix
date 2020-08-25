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

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.phoenix.R
import fr.acinq.phoenix.utils.ThemeHelper

class NotificationHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

  fun bindItem(notification: InAppNotifications, position: Int) {
    val messageView = itemView.findViewById<TextView>(R.id.notif_message)
    val iconView = itemView.findViewById<ImageView>(R.id.notif_icon)
    val actionButton = itemView.findViewById<Button>(R.id.notif_button)
    val spaceMD = itemView.resources.getDimensionPixelSize(R.dimen.space_md)
    val spaceSM = itemView.resources.getDimensionPixelSize(R.dimen.space_sm)
    val spaceXS = itemView.resources.getDimensionPixelSize(R.dimen.space_xs)

    if (notification.priority == 1) {
      itemView.background = itemView.context.getDrawable(R.drawable.app_notif_bg_critical)
      messageView.setTextColor(itemView.context.getColor(R.color.white))
      iconView.imageTintList = ColorStateList.valueOf(itemView.context.getColor(R.color.white))
    } else {
      itemView.background = itemView.context.getDrawable(R.drawable.app_notif_bg)
      messageView.setTextColor(ThemeHelper.color(itemView.context, R.attr.textColor))
      iconView.imageTintList = ColorStateList.valueOf(ThemeHelper.color(itemView.context, R.attr.textColor))
    }

    if (position == 0) {
      (itemView.layoutParams as ViewGroup.MarginLayoutParams).setMargins(spaceMD, 0, spaceMD, 0)
    } else {
      (itemView.layoutParams as ViewGroup.MarginLayoutParams).setMargins(spaceMD, spaceXS, spaceMD, 0)
    }

    messageView.text = itemView.resources.getString(notification.messageResId)
    iconView.setImageDrawable(itemView.context.getDrawable(notification.imageResId))

    if (notification.actionResId != null) {
      actionButton.visibility = View.VISIBLE
      actionButton.text = itemView.resources.getString(notification.actionResId)
      when (notification) {
        InAppNotifications.MNEMONICS_NEVER_SEEN -> actionButton.setOnClickListener { itemView.findNavController().navigate(R.id.action_main_to_display_seed) }
        else -> {}
      }
      itemView.setPadding(spaceSM, spaceSM, spaceSM, 0)
    } else {
      actionButton.visibility = View.GONE
      itemView.setPadding(spaceSM, spaceSM, spaceSM, spaceSM)
    }
  }
}
