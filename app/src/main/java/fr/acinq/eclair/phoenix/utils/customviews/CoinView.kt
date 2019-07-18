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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.common.base.Strings
import fr.acinq.bitcoin.MilliSatoshi
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.CustomCoinViewBinding
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.Prefs
import org.slf4j.LoggerFactory
import scala.Option

class CoinView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = R.style.default_theme) : ConstraintLayout(context, attrs, defStyle) {

  private val log = LoggerFactory.getLogger(CoinView::class.java)

  val mBinding: CustomCoinViewBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.custom_coin_view, this, true)
  private var isEditable: Boolean = false

  init {
    attrs?.let {
      val arr = context.obtainStyledAttributes(it, R.styleable.CoinView, 0, 0)

      try {
        mBinding.amount.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.CoinView_amount_size, R.dimen.text_lg).toFloat())
        mBinding.amount.setTextColor(arr.getColor(R.styleable.CoinView_amount_color, ContextCompat.getColor(context, R.color.dark)))
        mBinding.amount.typeface = Typeface.create(if (arr.getBoolean(R.styleable.CoinView_thin, true)) "sans-serif-light" else "sans-serif", Typeface.NORMAL)

        val coinUnit = Prefs.getCoin(context)
        isEditable = arr.getBoolean(R.styleable.CoinView_editable, false)
        if (isEditable) {
          if (arr.hasValue(R.styleable.CoinView_hint)) {
            mBinding.hint.text = context.getString(arr.getResourceId(R.styleable.CoinView_hint, R.string.utils_default_coin_view_hint))
          } else {
            mBinding.hint.text = context.getString(R.string.utils_default_coin_view_hint, coinUnit.code())
          }
          mBinding.amount.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
          mBinding.amount.focusable = View.FOCUSABLE
          mBinding.amount.isClickable = true

          this.setOnClickListener {
            mBinding.amount.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(mBinding.amount, InputMethodManager.RESULT_UNCHANGED_SHOWN)
          }
        } else {
          mBinding.amount.inputType = InputType.TYPE_NULL
          mBinding.amount.focusable = View.NOT_FOCUSABLE
          mBinding.amount.isClickable = false
        }

        handleEmptyAmountIfEditable()

        mBinding.unit.text = coinUnit.code()
        mBinding.unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, arr.getDimensionPixelSize(R.styleable.CoinView_unit_size, R.dimen.text_sm).toFloat())
        mBinding.unit.setTextColor(arr.getColor(R.styleable.CoinView_unit_color, ContextCompat.getColor(context, R.color.dark)))
      } catch (e: Exception) {
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

  fun setAmountWatcher(textWatcher: TextWatcher) {
    mBinding.amount.addTextChangedListener(textWatcher)
  }

  fun handleEmptyAmountIfEditable() {
    if (isEditable) {
      if (Strings.isNullOrEmpty(mBinding.amount.text.toString())) {
        mBinding.unit.visibility = View.GONE
        mBinding.hint.visibility = View.VISIBLE
      } else {
        mBinding.unit.visibility = View.VISIBLE
        mBinding.hint.visibility = View.GONE
      }
    }
  }

  fun getAmount(): Option<MilliSatoshi> {
    return Converter.string2Msat_opt_safe(mBinding.amount.text.toString(), context)
  }

  fun setAmount(amount: Satoshi) {
    setAmount(Converter.sat2msat(amount))
  }

  fun setAmount(amount: MilliSatoshi) {
    if (isEditable) {
      mBinding.amount.setText(Converter.rawAmountPrint(amount, context))
      handleEmptyAmountIfEditable()
    } else {
      mBinding.amount.setText(Converter.formatAmount(amount, context))
    }
    mBinding.unit.text = Prefs.getCoin(context).code()
  }

  open class CoinViewWatcher : TextWatcher {
    override fun afterTextChanged(s: Editable?) {}

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    }
  }
}
