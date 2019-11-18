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

package fr.acinq.eclair.phoenix.security

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.databinding.DataBindingUtil
import com.google.common.base.Strings
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.DialogPinBinding
import fr.acinq.eclair.phoenix.utils.Converter
import java.util.*

class PinDialog @JvmOverloads constructor(context: Context, themeResId: Int, private val pinCallback: PinDialogCallback, titleResId: Int = R.string.pindialog_title_default, cancelable: Boolean = true) :
  Dialog(context, themeResId) {

  private val mBinding: DialogPinBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.dialog_pin, null, false)
  private var mPinValue: String = ""

  init {
    setContentView(mBinding.root)
    setOnCancelListener { pinCallback.onPinCancel(this@PinDialog) }
    mBinding.pinTitle.text = Converter.html(getContext().getString(titleResId))
    setCancelable(cancelable)

    val mButtonsList = ArrayList<View>()
    mButtonsList.add(mBinding.pinNum1)
    mButtonsList.add(mBinding.pinNum2)
    mButtonsList.add(mBinding.pinNum3)
    mButtonsList.add(mBinding.pinNum4)
    mButtonsList.add(mBinding.pinNum5)
    mButtonsList.add(mBinding.pinNum6)
    mButtonsList.add(mBinding.pinNum7)
    mButtonsList.add(mBinding.pinNum8)
    mButtonsList.add(mBinding.pinNum9)
    mButtonsList.add(mBinding.pinNum0)

    mBinding.pinDisplay.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (s != null && s.length == PIN_LENGTH) {
          // automatically confirm pin when pin is long enough
          Handler().postDelayed({ pinCallback.onPinConfirm(this@PinDialog, mPinValue) }, 300)
        }
      }

      override fun afterTextChanged(s: Editable) {}
    })

    for (v in mButtonsList) {
      v.setOnClickListener { view ->
        view.isHapticFeedbackEnabled = true
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        if (mPinValue == "" || mPinValue.length != PIN_LENGTH) {
          val digit = (view as Button).text.toString()
          mPinValue += digit
          mBinding.pinDisplay.text = Strings.repeat(PIN_PLACEHOLDER, mPinValue.length)
        }
      }
    }

    mBinding.pinNumClear.setOnClickListener {
      mPinValue = ""
      mBinding.pinDisplay.text = ""
    }

    mBinding.pinBackspace.setOnClickListener {
      if (mPinValue.isNotEmpty()) {
        mPinValue = mPinValue.substring(0, mPinValue.length - 1)
        mBinding.pinDisplay.text = Strings.repeat(PIN_PLACEHOLDER, mPinValue.length)
      }
    }
  }

  fun reset() {
    mPinValue = ""
    mBinding.pinDisplay.text = ""
  }

  fun animateSuccess() {
    this.dismiss()
  }

  fun animateFailure() {
    this.dismiss()
  }

  interface PinDialogCallback {
    fun onPinConfirm(dialog: PinDialog, pinCode: String)

    fun onPinCancel(dialog: PinDialog)
  }

  companion object {

    private const val PIN_PLACEHOLDER = "\u25CF"
    private const val PIN_LENGTH = 6
  }
}
