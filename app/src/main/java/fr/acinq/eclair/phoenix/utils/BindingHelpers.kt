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

import android.view.View
import androidx.databinding.BindingAdapter
import androidx.databinding.BindingConversion

object BindingHelpers {

  @BindingAdapter("app:hideIfZero")
  @JvmStatic
  fun hideIfZero(view: View, number: Int) {
    view.visibility = if (number == 0) View.GONE else View.VISIBLE
  }

  @BindingAdapter("app:enableOrFade")
  @JvmStatic
  fun enableOrFade(view: View, enabled: Boolean) {
    view.isEnabled = enabled
    view.alpha = if (enabled) 1f else .5f
  }

  @BindingConversion
  @JvmStatic
  fun booleanToVisibility(isVisible: Boolean): Int {
    return if (isVisible) View.VISIBLE else View.GONE
  }
}
