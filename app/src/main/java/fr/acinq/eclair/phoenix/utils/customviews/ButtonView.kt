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

package fr.acinq.eclair.phoenix.utils.customviews

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.databinding.DataBindingUtil
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.CustomButtonViewBinding


class ButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.buttonViewStyle) : ConstraintLayout(context, attrs, R.attr.buttonViewStyle) {

  private var mBinding: CustomButtonViewBinding = DataBindingUtil.inflate(LayoutInflater.from(context),
    R.layout.custom_button_view, this, true)

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(attrs, R.styleable.ButtonView, defStyleAttr, R.style.default_buttonStyle)
      setPadding(resources.getDimensionPixelOffset(R.dimen.space_md))

      // optional text
      if (arr.hasValue(R.styleable.ButtonView_text)) {
        mBinding.text.text = arr.getString(R.styleable.ButtonView_text)
        if (arr.hasValue(R.styleable.ButtonView_text_size)) {
          mBinding.text.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.ButtonView_text_size, R.dimen.text_lg).toFloat())
        }
        mBinding.text.setTextColor(arr.getColor(R.styleable.ButtonView_text_color, ContextCompat.getColor(getContext(), R.color.dark)))
      } else {
        mBinding.text.visibility = View.GONE
        val params = LayoutParams(resources.getDimensionPixelOffset(R.dimen.button_height), resources.getDimensionPixelOffset(R.dimen.button_height))
        params.setMargins(0, 0, 0, 0)
        mBinding.image.layoutParams = params
      }

      // optional image
      if (arr.hasValue(R.styleable.ButtonView_icon)) {
        mBinding.image.setImageDrawable(arr.getDrawable(R.styleable.ButtonView_icon))
        if (arr.hasValue(R.styleable.ButtonView_icon_tint)) {
          mBinding.image.imageTintList = ColorStateList.valueOf(arr.getColor(R.styleable.ButtonView_icon_tint, ContextCompat.getColor(getContext(), R.color.dark)))
        }
      } else {
        mBinding.image.visibility = GONE
      }

      arr.recycle()
    }
  }
}
