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

package fr.acinq.eclair.phoenix.utils

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import fr.acinq.eclair.phoenix.R

object AlertHelper {

  fun build(inflater: LayoutInflater, titleResId: Int?, messageResId: Int?): AlertDialog.Builder {
    val context = inflater.context
    return build(inflater, titleResId?.let { context.getString(it) }, messageResId?.let { context.getString(it) })
  }

  fun build(inflater: LayoutInflater, title: CharSequence?, message: CharSequence?): AlertDialog.Builder {
    val context = inflater.context
    val view = inflater.inflate(R.layout.dialog_alert, null)
    val titleView = view.findViewById<TextView>(R.id.alert_title)
    val messageView = view.findViewById<TextView>(R.id.alert_message)

    title?.run {
      titleView.visibility = View.VISIBLE
      titleView.text = this
    }

    message?.run {
      messageView.visibility = View.VISIBLE
      messageView.text = this
    }

    return AlertDialog.Builder(context, R.style.default_dialogTheme)
      .setView(view)
  }
}
