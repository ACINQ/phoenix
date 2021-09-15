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

package fr.acinq.phoenix.legacy.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
  fun color(context: Context, @AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
  }

  private const val lightMode = "light"
  private const val darkMode = "dark"
  const val default = "default"

  fun applyTheme(theme: String) {
    when (theme) {
      lightMode -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
      darkMode -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
      else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
  }
}
