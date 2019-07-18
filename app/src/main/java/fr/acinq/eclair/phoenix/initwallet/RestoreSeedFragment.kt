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

package fr.acinq.eclair.phoenix.initwallet

import android.content.Context
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentInitWalletRestoreBinding
import fr.acinq.eclair.phoenix.utils.customviews.VirtualKeyboardView
import fr.acinq.eclair.phoenix.utils.encrypt.EncryptedSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex

class RestoreSeedFragment : Fragment() {
  private val log = LoggerFactory.getLogger(RestoreSeedFragment::class.java)

  private lateinit var mBinding: FragmentInitWalletRestoreBinding
  private lateinit var model: RestoreSeedViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentInitWalletRestoreBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(RestoreSeedViewModel::class.java)
    model.errorCause.observe(viewLifecycleOwner, Observer {
      mBinding.error.text = getString(R.string.restore_error, it)
    })
    model.state.observe(viewLifecycleOwner, Observer {
      if (it == RestoreSeedState.DONE) {
        findNavController().navigate(R.id.global_action_any_to_startup)
      }
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.keyboard.setOnKeyPressedListener(getOnKeyPressedListener())
    mBinding.error.setOnClickListener { model.state.value = RestoreSeedState.INPUT_DATA }
    mBinding.disclaimerButton.setOnClickListener {
      if (mBinding.disclaimerCheckbox.isChecked) {
        model.state.value = RestoreSeedState.INPUT_DATA
      }
      log.info("click next state=${model.state.value}")
    }
    mBinding.importButton.setOnClickListener {
      context?.let {
        val mnemonics = mBinding.mnemonicsInput.text.toString().trim().toLowerCase()
        model.importAndSaveSeed(it, mnemonics)
      }
    }
  }

  internal val spanBuilder = SpannableStringBuilder()
  internal val autocompleteSpanBuilder = SpannableStringBuilder()

  private fun getOnKeyPressedListener(): VirtualKeyboardView.OnKeyPressedListener {
    return object : VirtualKeyboardView.OnKeyPressedListener {
      override fun onEvent(keyCode: Int) {
        // ==== A-Z characters
        if (Character.isAlphabetic(keyCode)) {
          spanBuilder.append((keyCode.toChar()).toString())
//          refreshStyleLastWord(false)
          mBinding.mnemonicsInput.text = spanBuilder
        } else if (Character.isWhitespace(keyCode)) {
          if (spanBuilder.isNotEmpty() && !Character.isWhitespace(spanBuilder[spanBuilder.length - 1])) {
//            refreshStyleLastWord(true)
            spanBuilder.append(' ')
            mBinding.mnemonicsInput.text = spanBuilder
          }
        } else if (keyCode == VirtualKeyboardView.KEY_DELETE) {
          if (spanBuilder.isEmpty()) {
            spanBuilder.clear()
          } else {
            spanBuilder.delete(spanBuilder.length - 1, spanBuilder.length)
//            refreshStyleLastWord(false)
          }
          mBinding.mnemonicsInput.text = spanBuilder
        }
      }
    }
  }

}

enum class RestoreSeedState {
  INIT, INPUT_DATA, IN_PROGRESS, DONE, ERROR
}

class RestoreSeedViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(RestoreSeedViewModel::class.java)

  val state = MutableLiveData(RestoreSeedState.INIT)
  val errorCause = MutableLiveData("")

  @UiThread
  fun importAndSaveSeed(context: Context, mnemonics: String) {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        try {
          state.postValue(RestoreSeedState.IN_PROGRESS)
          MnemonicCode.validate(mnemonics)
          val seed: ByteArray = Hex.encode(mnemonics.toByteArray(Charsets.UTF_8))
          delay(500)
          EncryptedSeed.writeSeedToFile(context, seed, "tutu")
          log.info("seed written to file")
          state.postValue(RestoreSeedState.DONE)
        } catch (t: Throwable) {
          log.error("cannot import seed: ", t)
          errorCause.postValue(t.localizedMessage)
          state.postValue(RestoreSeedState.ERROR)
        }
      }
    }
  }
}
