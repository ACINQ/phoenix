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
import android.content.SharedPreferences
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.preference.PreferenceManager
import fr.acinq.bitcoin.BtcAmount
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.CustomCoinViewBinding
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.ThemeHelper
import org.slf4j.LoggerFactory
import scala.Option

class CoinView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = R.style.default_theme) : ConstraintLayout(context, attrs, defStyle) {

  val mBinding: CustomCoinViewBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.custom_coin_view, this, true)
  private val log = LoggerFactory.getLogger(this::class.java)
  private var _amount: Option<MilliSatoshi> = Option.apply(null)

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    if (key == Prefs.PREFS_SHOW_AMOUNT_IN_FIAT) {
      refreshFields()
    }
  }

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(it, R.styleable.CoinView, 0, 0)
      try {
        mBinding.amount.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.CoinView_amount_size, R.dimen.text_lg).toFloat())
        mBinding.amount.setTextColor(arr.getColor(R.styleable.CoinView_amount_color, ThemeHelper.color(context, R.attr.textColor)))
        mBinding.amount.typeface = Typeface.create(if (arr.getBoolean(R.styleable.CoinView_thin, true)) "sans-serif-light" else "sans-serif", Typeface.NORMAL)

        mBinding.unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.CoinView_unit_size, R.dimen.text_sm).toFloat())
        mBinding.unit.setTextColor(arr.getColor(R.styleable.CoinView_unit_color, ThemeHelper.color(context, R.attr.textColor)))
        mBinding.unit.typeface = Typeface.create(if (arr.getBoolean(R.styleable.CoinView_thin, true)) "sans-serif-light" else "sans-serif", Typeface.NORMAL)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        mBinding.clickable.setOnClickListener {
          val showAmountInFiat = Prefs.getShowAmountInFiat(context)
          Prefs.setShowAmountInFiat(context, !showAmountInFiat)
        }
      } catch (e: Exception) {
        log.error("error in CoinView: ", e)
      } finally {
        arr.recycle()
      }
    }
  }

  fun setAmountSize(size: Float) {
    mBinding.amount.textSize = size
    postInvalidate()
  }

  fun setUnitSize(size: Float) {
    mBinding.unit.textSize = size
    postInvalidate()
  }

  override fun getBaseline(): Int {
    return mBinding.amount.baseline
  }

  fun getAmount(): Option<MilliSatoshi> {
    return Converter.string2Msat_opt_safe(mBinding.amount.text.toString(), context)
  }

  fun setAmount(amount: Satoshi) {
    _amount = Option.apply(Converter.any2Msat(amount))
    refreshFields()
  }

  fun setAmount(amount: MilliSatoshi) {
    _amount = Option.apply(amount)
    refreshFields()
  }

  private fun refreshFields() {
    val showAmountInFiat = Prefs.getShowAmountInFiat(context)
    val coinUnit = Prefs.getCoinUnit(context)
    if (_amount.isDefined) {
      if (showAmountInFiat) {
        mBinding.amount.text = Converter.printFiatPretty(context, _amount.get())
        mBinding.unit.text = Prefs.getFiatCurrency(context)
      } else {
        mBinding.amount.text = Converter.printAmountPretty(_amount.get(), context)
        mBinding.unit.text = coinUnit.code()
      }
    } else {
      mBinding.amount.text = ""
    }
    invalidate()
    requestLayout()
  }
}
