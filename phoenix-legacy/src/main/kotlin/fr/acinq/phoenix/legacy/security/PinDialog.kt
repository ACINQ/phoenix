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

package fr.acinq.phoenix.legacy.security

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import androidx.databinding.DataBindingUtil
import com.google.common.base.Strings
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.DialogPinBinding
import fr.acinq.phoenix.legacy.utils.Converter
import fr.acinq.phoenix.legacy.utils.Prefs

class PinDialog @JvmOverloads constructor(context: Context, themeResId: Int, private val pinCallback: PinDialogCallback,
  titleResId: Int = R.string.legacy_pindialog_title_default, cancelable: Boolean = true) : Dialog(context, themeResId) {

  private val mBinding: DialogPinBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.dialog_pin, null, false)
  private var mPinValue: String = ""

  init {
    setContentView(mBinding.root)
    setOnCancelListener { pinCallback.onPinCancel(this@PinDialog) }
    mBinding.pinTitle.text = Converter.html(getContext().getString(titleResId))
    setCancelable(cancelable)

    // disable screen capture
    window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

    // randomly sorted pin buttons
    val pinButtons = listOf(
      mBinding.pinNum0,
      mBinding.pinNum1,
      mBinding.pinNum2,
      mBinding.pinNum3,
      mBinding.pinNum4,
      mBinding.pinNum5,
      mBinding.pinNum6,
      mBinding.pinNum7,
      mBinding.pinNum8,
      mBinding.pinNum9
    )

    for (v in pinButtons) {
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

    if (Prefs.isPinScrambled(context)) {
      mBinding.pinGrid.removeAllViews()
      pinButtons.shuffled().withIndex().forEach { indexedBtn ->
        if (indexedBtn.index == pinButtons.size - 1) {
          mBinding.pinGrid.addView(mBinding.pinNumClear)
          mBinding.pinGrid.addView(indexedBtn.value)
          mBinding.pinGrid.addView(mBinding.pinBackspace)
        } else {
          mBinding.pinGrid.addView(indexedBtn.value)
        }
      }
    }

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
