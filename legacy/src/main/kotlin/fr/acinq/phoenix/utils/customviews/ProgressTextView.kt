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
import android.graphics.PorterDuff
import android.text.Spanned
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.CustomProgressTextViewBinding

class ProgressTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = R.style.default_theme) : ConstraintLayout(context, attrs, defStyle) {
  val mBinding = DataBindingUtil.inflate<CustomProgressTextViewBinding>(LayoutInflater.from(getContext()), R.layout.custom_progress_text_view, this, true)

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(it, R.styleable.ProgressTextView, 0, defStyle)
      mBinding.label.text = arr.getString(R.styleable.ProgressTextView_text)
      if (arr.hasValue(R.styleable.ProgressTextView_text_color)) {
        mBinding.label.setTextColor(arr.getColor(R.styleable.ProgressTextView_text_color, R.attr.textColor))
      }

      if (arr.hasValue(R.styleable.ProgressTextView_text_size)) {
        mBinding.label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
          arr.getDimensionPixelSize(R.styleable.ProgressTextView_text_size, R.dimen.text_lg).toFloat())
      }

      if (arr.hasValue(R.styleable.ProgressTextView_progress_tint)) {
        mBinding.progressBar.indeterminateDrawable.setColorFilter(arr.getColor(R.styleable.ProgressTextView_progress_tint, R.attr.textColor), PorterDuff.Mode.SRC_IN)
      }
      arr.recycle()
    }
  }

  fun setText(s: Spanned) {
    mBinding.label.text = s
  }

  fun setText(s: String) {
    mBinding.label.text = s
  }
}
