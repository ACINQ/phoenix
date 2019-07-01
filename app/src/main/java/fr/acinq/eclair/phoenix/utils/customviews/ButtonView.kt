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
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.CustomButtonViewBinding

class ButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = R.style.ClassicButtonStyle) : ConstraintLayout(context, attrs, R.style.ClassicButtonStyle) {

  private var mBinding: CustomButtonViewBinding = DataBindingUtil.inflate(LayoutInflater.from(context),
    R.layout.custom_button_view, this, true)

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(attrs, R.styleable.ButtonView, 0, defStyle)

      mBinding.text.text = arr.getString(R.styleable.ButtonView_text)
      if (arr.hasValue(R.styleable.ButtonView_text_size)) {
        mBinding.text.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.ButtonView_text_size, R.dimen.text_lg).toFloat())
      }
      mBinding.text.setTextColor(arr.getColor(R.styleable.ButtonView_text_color, ContextCompat.getColor(getContext(), R.color.dark)))

      // optional image
      mBinding.image.visibility = GONE
      arr.getDrawable(R.styleable.ButtonView_icon)?.let {
        mBinding.image.setImageDrawable(it)
//        val size = arr.getDimensionPixelSize(R.styleable.ButtonView_image_size, R.dimen.space_md_p)
//        mBinding.image.layoutParams.height = size
//        mBinding.image.layoutParams.width = size

        if (arr.hasValue(R.styleable.ButtonView_icon_tint)) {
          mBinding.image.imageTintList = ColorStateList.valueOf(arr.getColor(R.styleable.ButtonView_icon_tint, ContextCompat.getColor(getContext(), R.color.dark)))
        }
        mBinding.image.visibility = VISIBLE
      }

      arr.getDrawable(R.styleable.ButtonView_background)?.let {
        mBinding.root.background = it
      }

      arr.recycle()
    }
  }
}
