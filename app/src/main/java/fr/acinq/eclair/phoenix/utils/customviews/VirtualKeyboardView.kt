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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.CustomVirtualKeyboardViewBinding
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference

/**
 * This is a virtual keyboard containing the [A-Z ] keys (i.e. 27 keys). The space character is allowed. It's basically
 * a grid of buttons. Pressing a button on this keyboard will dispatch an event that you can listen to by implementing
 * a [VirtualKeyboard.OnKeyPressedListener].
 */

//
class VirtualKeyboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = R.style.default_theme) : ConstraintLayout(context, attrs, defStyle) {

  private val log = LoggerFactory.getLogger(VirtualKeyboardView::class.java)

  // field to handle key repetition (long press on delete for example)
  private var mHandler: Handler? = null

  private var mListener: OnKeyPressedListener? = null

  /**
   * Listener for the virtual key press event. Implement this to react when a key is pressed.
   */
  interface OnKeyPressedListener {

    /**
     * Called when a virtual key of the virtual keyboard is pressed.
     *
     * @param keyCode Unicode value of the character for the key.
     */
    fun onEvent(keyCode: Int)
  }

  fun setOnKeyPressedListener(eventListener: OnKeyPressedListener) {
    mListener = eventListener
  }

  private fun dispatchCharEvent(keyCode: Int) {
    mHandler!!.removeMessages(MSG_REPEAT)
    if (mListener != null) {
      if (keyCode != KEY_DELETE) {
        isHapticFeedbackEnabled = true
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
      }
      mListener!!.onEvent(keyCode)
    }
  }

  val mBinding: CustomVirtualKeyboardViewBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.custom_virtual_keyboard_view, this, true)

  init {

    mBinding.keyA.setOnClickListener { dispatchCharEvent('a'.toInt()) }
    mBinding.keyB.setOnClickListener { dispatchCharEvent('b'.toInt()) }
    mBinding.keyC.setOnClickListener { dispatchCharEvent('c'.toInt()) }
    mBinding.keyD.setOnClickListener { dispatchCharEvent('d'.toInt()) }
    mBinding.keyE.setOnClickListener { dispatchCharEvent('e'.toInt()) }
    mBinding.keyF.setOnClickListener { dispatchCharEvent('f'.toInt()) }
    mBinding.keyG.setOnClickListener { dispatchCharEvent('g'.toInt()) }
    mBinding.keyH.setOnClickListener { dispatchCharEvent('h'.toInt()) }
    mBinding.keyI.setOnClickListener { dispatchCharEvent('i'.toInt()) }
    mBinding.keyJ.setOnClickListener { dispatchCharEvent('j'.toInt()) }
    mBinding.keyK.setOnClickListener { dispatchCharEvent('k'.toInt()) }
    mBinding.keyL.setOnClickListener { dispatchCharEvent('l'.toInt()) }
    mBinding.keyM.setOnClickListener { dispatchCharEvent('m'.toInt()) }
    mBinding.keyN.setOnClickListener { dispatchCharEvent('n'.toInt()) }
    mBinding.keyO.setOnClickListener { dispatchCharEvent('o'.toInt()) }
    mBinding.keyP.setOnClickListener { dispatchCharEvent('p'.toInt()) }
    mBinding.keyQ.setOnClickListener { dispatchCharEvent('q'.toInt()) }
    mBinding.keyR.setOnClickListener { dispatchCharEvent('r'.toInt()) }
    mBinding.keyS.setOnClickListener { dispatchCharEvent('s'.toInt()) }
    mBinding.keyT.setOnClickListener { dispatchCharEvent('t'.toInt()) }
    mBinding.keyU.setOnClickListener { dispatchCharEvent('u'.toInt()) }
    mBinding.keyV.setOnClickListener { dispatchCharEvent('v'.toInt()) }
    mBinding.keyW.setOnClickListener { dispatchCharEvent('w'.toInt()) }
    mBinding.keyX.setOnClickListener { dispatchCharEvent('x'.toInt()) }
    mBinding.keyY.setOnClickListener { dispatchCharEvent('y'.toInt()) }
    mBinding.keyZ.setOnClickListener { dispatchCharEvent('z'.toInt()) }
    mBinding.keySpace.setOnClickListener { dispatchCharEvent(' '.toInt()) }
    handleDelete()
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun handleDelete() {
    mBinding.keyDelete.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          dispatchCharEvent(KEY_DELETE)
          mHandler?.removeMessages(MSG_LONGPRESS)
          mHandler?.sendMessageDelayed(mHandler!!.obtainMessage(MSG_LONGPRESS, KEY_DELETE), LONGPRESS_TIMEOUT.toLong())
        }
        MotionEvent.ACTION_UP -> {
          mHandler?.removeMessages(MSG_REPEAT)
          mHandler?.removeMessages(MSG_LONGPRESS)
        }
        MotionEvent.ACTION_CANCEL -> {
          mHandler?.removeMessages(MSG_REPEAT)
          mHandler?.removeMessages(MSG_LONGPRESS)
        }
        else -> {
          // do nothing
        }
      }
      false
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (mHandler == null) {
      mHandler = RepeatKeyHandler(this)
    }
  }

  internal class RepeatKeyHandler(keyboard: VirtualKeyboardView) : Handler() {
    private val log = LoggerFactory.getLogger(RepeatKeyHandler::class.java)

    private val mKeyboard: WeakReference<VirtualKeyboardView> = WeakReference(keyboard)

    override fun handleMessage(msg: Message) {
      when (msg.what) {
        MSG_LONGPRESS -> sendMessage(obtainMessage(MSG_REPEAT, msg.obj))
        MSG_REPEAT -> {
          mKeyboard.get()?.let {
            val keyCode = msg.obj as Int
            it.dispatchCharEvent(keyCode)
            sendMessageDelayed(obtainMessage(MSG_REPEAT, keyCode), REPEAT_INTERVAL.toLong())
          }
        }
      }
    }
  }

  companion object {
    const val KEY_DELETE = -1
    private const val REPEAT_INTERVAL = 50 // ~20 keys per second
    private const val MSG_LONGPRESS = 4
    private const val MSG_REPEAT = 3
    private val LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout()
  }
}
