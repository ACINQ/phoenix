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

package fr.acinq.phoenix.utils.customviews

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.CustomSwitchViewBinding
import fr.acinq.phoenix.utils.ThemeHelper


class SwitchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.buttonViewStyle) : ConstraintLayout(context, attrs, R.attr.buttonViewStyle) {

  private var mBinding: CustomSwitchViewBinding = DataBindingUtil.inflate(LayoutInflater.from(context),
    R.layout.custom_switch_view, this, true)

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(attrs, R.styleable.SwitchView, defStyleAttr, R.style.default_buttonStyle)
      background = ContextCompat.getDrawable(context, R.drawable.button_bg_square)
      mBinding.text.text = arr.getString(R.styleable.SwitchView_text)
      if (arr.hasValue(R.styleable.SwitchView_text_size)) {
        mBinding.text.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.SwitchView_text_size, R.dimen.text_lg).toFloat())
      }
      arr.getString(R.styleable.SwitchView_subtitle).orEmpty().let {
        if (it.isBlank()) {
          mBinding.subtitle.visibility = View.GONE
        } else {
          mBinding.subtitle.text = it
        }
      }
      mBinding.text.setTextColor(arr.getColor(R.styleable.SwitchView_text_color, ThemeHelper.color(context, R.attr.textColor)))

      // optional image
      if (arr.hasValue(R.styleable.SwitchView_icon)) {
        mBinding.icon.setImageDrawable(arr.getDrawable(R.styleable.SwitchView_icon))
        if (arr.hasValue(R.styleable.SwitchView_icon_tint)) {
          mBinding.icon.imageTintList = ColorStateList.valueOf(arr.getColor(R.styleable.SwitchView_icon_tint, ThemeHelper.color(context, R.attr.textColor)))
        }
      } else {
        mBinding.icon.visibility = GONE
        val params = LayoutParams(0, LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, 0)
        mBinding.text.layoutParams = params
      }

      arr.recycle()
    }
  }

  fun setText(text: String) {
    mBinding.text.text = text
  }

  fun setSubtitle(text: String) {
    mBinding.subtitle.text = text
    mBinding.subtitle.visibility = View.VISIBLE
  }

  fun isChecked(): Boolean {
    return mBinding.switchButton.isChecked
  }

  fun setChecked(isChecked: Boolean) {
    mBinding.switchButton.isChecked = isChecked
  }

  fun setIcon(icon: Drawable) {
    mBinding.icon.setImageDrawable(icon)
  }
}
