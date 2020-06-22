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

import android.view.View
import android.widget.EditText
import androidx.databinding.BindingAdapter
import androidx.databinding.BindingConversion
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.lifecycle.MutableLiveData
import java.lang.Exception

object BindingHelpers {

  @BindingAdapter("enableOrFade")
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

  @BindingAdapter("visible")
  @JvmStatic
  fun show(view: View, isVisible: Boolean) {
    view.visibility = if (isVisible) View.VISIBLE else View.GONE
  }

  @BindingAdapter("android:text")
  @JvmStatic
  fun setLong(view: EditText, liveDataLong: MutableLiveData<Long>) {
    liveDataLong.value?.let { newValue ->
      view.text.toString().toLongOrNull().let { oldValue ->
        if (oldValue != newValue) {
          view.setText(newValue.toString())
        }
      }
    }
  }

  @InverseBindingAdapter(attribute = "android:text", event = "android:textAttrChanged")
  @JvmStatic
  fun getLong(view: EditText): Long {
    return try {
      view.text.toString().toLong()
    } catch (e: Exception) {
      0L
    }
  }
}
