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
import androidx.databinding.DataBindingUtil
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.CustomButtonViewBinding
import fr.acinq.phoenix.utils.ThemeHelper


class ButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.buttonViewStyle) : ConstraintLayout(context, attrs, R.attr.buttonViewStyle) {

  private var mBinding: CustomButtonViewBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.custom_button_view, this, true)

  private var isPaused: Boolean = false
  private var defaultText: String? = null
  private var pausedText: String? = null
  private var hasIcon: Boolean = false

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(attrs, R.styleable.ButtonView, defStyleAttr, R.style.default_buttonStyle)

      defaultText = arr.getString(R.styleable.ButtonView_text)
      pausedText = arr.getString(R.styleable.ButtonView_paused_text)

      // optional text
      if (arr.hasValue(R.styleable.ButtonView_text)) {
        mBinding.text.text = arr.getString(R.styleable.ButtonView_text)
        if (arr.hasValue(R.styleable.ButtonView_text_size)) {
          mBinding.text.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.ButtonView_text_size, R.dimen.text_lg).toFloat())
        }
        mBinding.text.setTextColor(arr.getColor(R.styleable.ButtonView_text_color, ThemeHelper.color(context, R.attr.textColor)))

        // display subtitle only if a title is set
        if (arr.hasValue(R.styleable.ButtonView_subtitle)) {
          mBinding.subtitle.text = arr.getString(R.styleable.ButtonView_subtitle)
          mBinding.subtitle.visibility = View.VISIBLE
        } else {
          mBinding.subtitle.visibility = View.GONE
        }
      } else {
        mBinding.textContainer.visibility = View.GONE
        mBinding.spacer.visibility = View.GONE
      }

      if (arr.hasValue(R.styleable.ButtonView_hz_bias)) {
        val params = mBinding.image.layoutParams as LayoutParams
        params.horizontalBias = arr.getFloat(R.styleable.ButtonView_hz_bias, 0f)
      }

      // optional image
      if (arr.hasValue(R.styleable.ButtonView_icon)) {
        hasIcon = true
        mBinding.image.setImageDrawable(arr.getDrawable(R.styleable.ButtonView_icon))
        if (arr.hasValue(R.styleable.ButtonView_icon_tint)) {
          mBinding.image.imageTintList = ColorStateList.valueOf(arr.getColor(R.styleable.ButtonView_icon_tint, ThemeHelper.color(context, R.attr.textColor)))
        }
        if (arr.hasValue(R.styleable.ButtonView_icon_size)) {
          val imageSize = arr.getDimensionPixelOffset(R.styleable.ButtonView_icon_size, R.dimen.button_height)
          mBinding.image.layoutParams.width = imageSize
          mBinding.image.layoutParams.height = imageSize
        }
      } else {
        mBinding.image.visibility = View.GONE
        mBinding.spacer.visibility = View.GONE
      }

      mBinding.progress.indeterminateTintList = ColorStateList.valueOf(arr.getColor(R.styleable.ButtonView_icon_tint, ThemeHelper.color(context, R.attr.textColor)))

      // spacer size
      if (arr.hasValue(R.styleable.ButtonView_spacer_size)) {
        mBinding.spacer.layoutParams.width = arr.getDimensionPixelOffset(R.styleable.ButtonView_spacer_size, R.dimen.space_sm)
      }

      isPaused = arr.getBoolean(R.styleable.ButtonView_is_paused, false)

      arr.recycle()
    }
  }

  fun getIsPaused() = isPaused

  fun setIsPaused(b: Boolean) {
    isPaused = b
    // change icon visibility only if necessary
    if (hasIcon) {
      mBinding.image.visibility = if (isPaused) View.INVISIBLE else View.VISIBLE
    }

    // if button is paused, show progress bar with special text
    mBinding.progress.visibility = if (isPaused) View.VISIBLE else if (hasIcon) View.INVISIBLE else View.GONE
    if (isPaused) {
      if (pausedText != null) mBinding.text.text = pausedText
    } else {
      if (defaultText != null) mBinding.text.text = defaultText
    }

    // spacer is shown only if the icon or the progress bar is shown
    val shouldShowSpacer = mBinding.image.visibility == View.VISIBLE || mBinding.progress.visibility == View.VISIBLE
    mBinding.spacer.visibility = if (shouldShowSpacer) View.VISIBLE else View.GONE
  }

  override fun setOnClickListener(l: OnClickListener?) {
    super.setOnClickListener {
      if (!isPaused) {
        l?.onClick(null)
      }
    }
  }

  fun setDefaultText(text: String) {
    defaultText = text
    mBinding.text.text = if (isPaused) pausedText else defaultText
  }

  fun setText(text: String) {
    mBinding.text.text = text
  }

  fun setSubtitle(text: String) {
    mBinding.subtitle.text = text
    mBinding.subtitle.visibility = View.VISIBLE
  }

  fun setIcon(icon: Drawable) {
    mBinding.image.setImageDrawable(icon)
  }

  fun setIconColor(color: Int) {
    mBinding.image.imageTintList = ColorStateList.valueOf(color)
  }
}
