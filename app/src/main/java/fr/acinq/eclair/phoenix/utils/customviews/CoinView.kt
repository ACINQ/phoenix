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
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import fr.acinq.bitcoin.MilliSatoshi
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.CustomCoinViewBinding
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.Prefs

class CoinView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = R.style.default_theme) : ConstraintLayout(context, attrs, defStyle) {
  val mBinding: CustomCoinViewBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.custom_coin_view, this, true)

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(it, R.styleable.CoinView, 0, 0)
      mBinding.amount.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.CoinView_amount_size, 0).toFloat())
//      mBinding.amount.setTextColor(arr.getColor(R.styleable.CoinView_amount_color, ContextCompat.getColor(context, R.color.white)))
      mBinding.amount.typeface = Typeface.create(if (arr.getBoolean(R.styleable.CoinView_thin, true)) "sans-serif-light" else "sans-serif", Typeface.NORMAL)

      mBinding.unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.CoinView_unit_size, 0).toFloat())
//      mBinding.unit.setTextColor(arr.getColor(R.styleable.CoinView_unit_color, ContextCompat.getColor(context, R.color.white)))
      arr.recycle()
    }
  }

  override fun getBaseline(): Int {
    return mBinding.amount.baseline
  }

  fun setAmount(amount: Satoshi) {
    setAmount(fr.acinq.bitcoin.`package$`.`MODULE$`.satoshi2millisatoshi(amount))
  }

  fun setAmount(amount: MilliSatoshi) {
    mBinding.amount.text = Converter.formatAmount(amount, context)
    mBinding.unit.text = Prefs.prefCoin(context).code()
  }
}
