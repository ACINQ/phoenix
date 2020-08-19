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

package fr.acinq.phoenix.utils

import android.app.AlertDialog
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import fr.acinq.phoenix.R

object AlertHelper {

  fun build(inflater: LayoutInflater, titleResId: Int?, messageResId: Int?): AlertDialog.Builder {
    val context = inflater.context
    return build(inflater, titleResId?.let { context.getString(it) }, messageResId?.let { context.getString(it) })
  }

  fun build(inflater: LayoutInflater, title: CharSequence?, message: CharSequence?): AlertDialog.Builder {
    val view = inflater.inflate(R.layout.dialog_alert, null)
    default(view, title, message)
    return AlertDialog.Builder(inflater.context, R.style.default_dialogTheme).setView(view)
  }

  fun buildWithInput(inflater: LayoutInflater, title: CharSequence?, message: CharSequence?, callback: (String) -> Unit, defaultValue: String, inputType: Int = InputType.TYPE_CLASS_TEXT): AlertDialog.Builder {
    val view = inflater.inflate(R.layout.dialog_alert_input, null)
    default(view, title, message)
    val input = view.findViewById<EditText>(R.id.alert_input).apply {
      this.inputType = inputType
      this.setText(defaultValue)
    }
    return AlertDialog.Builder(inflater.context, R.style.default_dialogTheme)
      .setView(view)
      .setPositiveButton(inflater.context.getString(R.string.btn_confirm)) { _, _ -> callback(input.text.toString()) }
  }

  private fun default(view: View, title: CharSequence?, message: CharSequence?) = view.apply {
    findViewById<TextView>(R.id.alert_title).apply {
      if (title != null) {
        visibility = View.VISIBLE
        text = title
      }
    }
    findViewById<TextView>(R.id.alert_message).apply {
      if (message != null) {
        visibility = View.VISIBLE
        text = message
      }
    }
  }
}
