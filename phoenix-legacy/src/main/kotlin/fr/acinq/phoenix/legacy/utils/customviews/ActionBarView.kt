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

package fr.acinq.phoenix.legacy.utils.customviews

import android.content.Context
import android.text.Spanned
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import com.google.common.base.Strings
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.CustomActionBarViewBinding
import org.slf4j.LoggerFactory


class ActionBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.actionBarViewStyle) :
  ConstraintLayout(context, attrs, R.attr.actionBarViewStyle) {

  private val log = LoggerFactory.getLogger(this::class.java)

  private var mBinding: CustomActionBarViewBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.custom_action_bar_view, this, true)

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(attrs, R.styleable.ActionBarView, defStyleAttr, R.style.default_actionBar)
      // -- title
      if (arr.hasValue(R.styleable.ActionBarView_title)) {
        mBinding.title.text = arr.getString(R.styleable.ActionBarView_title)
      } else {
        mBinding.title.visibility = View.GONE
      }
      // -- subtitle
      if (arr.hasValue(R.styleable.ActionBarView_subtitle)) {
        mBinding.subtitle.text = arr.getString(R.styleable.ActionBarView_subtitle)
      } else {
        mBinding.subtitle.visibility = View.GONE
      }
      if (arr.hasValue(R.styleable.ActionBarView_arrow_color)) {
        mBinding.backButton.iconTint = arr.getColorStateList(R.styleable.ActionBarView_arrow_color)
      }
      arr.recycle()
    }
  }

  fun setSubtitle(s: Spanned) {
    mBinding.subtitle.text = s
    mBinding.subtitle.visibility = View.VISIBLE
  }

  fun setSubtitle(s: String?) {
    if (!Strings.isNullOrEmpty(s)) {
      mBinding.subtitle.text = s
      mBinding.subtitle.visibility = View.VISIBLE
    } else {
      mBinding.subtitle.visibility = View.GONE
    }
    postInvalidate()
  }

  fun setTitle(s: String?) {
    if (!Strings.isNullOrEmpty(s)) {
      mBinding.title.text = s
      mBinding.title.visibility = View.VISIBLE
    } else {
      mBinding.title.visibility = View.GONE
    }
  }

  fun setOnBackAction(l: OnClickListener) {
    mBinding.backButton.setOnClickListener(l)
  }
}
