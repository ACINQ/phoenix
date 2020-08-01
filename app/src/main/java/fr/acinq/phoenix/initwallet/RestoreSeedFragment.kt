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

package fr.acinq.phoenix.initwallet

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.`MnemonicCode$`
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentInitWalletRestoreBinding
import fr.acinq.phoenix.utils.crypto.KeystoreHelper
import fr.acinq.phoenix.utils.Wallet
import fr.acinq.phoenix.utils.crypto.EncryptedSeed
import fr.acinq.phoenix.utils.customviews.VirtualKeyboardView
import fr.acinq.phoenix.utils.crypto.SeedManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex
import java.util.*

class RestoreSeedFragment : Fragment() {
  private val log = LoggerFactory.getLogger(RestoreSeedFragment::class.java)

  private lateinit var mBinding: FragmentInitWalletRestoreBinding
  private lateinit var model: RestoreSeedViewModel

  private val handler: Handler = Handler()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentInitWalletRestoreBinding.inflate(inflater, container, false)
    mBinding.autocomplete.movementMethod = LinkMovementMethod.getInstance()
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(RestoreSeedViewModel::class.java)

    // -- watch state
    model.state.observe(viewLifecycleOwner, Observer {
      if (it == RestoreSeedState.DONE) {
        findNavController().navigate(R.id.global_action_any_to_startup)
      }
      if (it == RestoreSeedState.ERROR) {
        handler.postDelayed({
          if (model.state.value == RestoreSeedState.ERROR) {
            model.state.value = RestoreSeedState.INPUT_DATA
          }
        }, 2000)
      }
    })

    // -- watch spanbuilder and refresh mnemonics display
    model.mnemonicsSpanBuilder.observe(viewLifecycleOwner, Observer {
      mBinding.mnemonicsInput.text = it
    })
    model.autocompleteSpanBuilder.observe(viewLifecycleOwner, Observer {
      mBinding.autocomplete.text = it
    })

    // -- update model with checkbox state
    mBinding.disclaimerCheckbox.setOnCheckedChangeListener { _, isChecked -> model.userHasCheckedDisclaimer.value = isChecked }

    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    context?.let {
      if (Wallet.hasWalletBeenSetup(it)) {
        findNavController().navigate(R.id.global_action_any_to_startup)
      }
    }
    mBinding.keyboard.setOnKeyPressedListener(getOnKeyPressedListener())
    mBinding.error.setOnClickListener {
      handler.removeCallbacksAndMessages(null)
      model.state.value = RestoreSeedState.INPUT_DATA
    }
    mBinding.disclaimerButton.setOnClickListener {
      if (mBinding.disclaimerCheckbox.isChecked) {
        model.state.value = RestoreSeedState.INPUT_DATA
      }
    }
    mBinding.importButton.setOnClickListener {
      context?.let {
        val mnemonics = mBinding.mnemonicsInput.text.toString().trim().toLowerCase()
        model.importAndSaveSeed(it, mnemonics)
      }
    }
  }

  private fun getOnKeyPressedListener(): VirtualKeyboardView.OnKeyPressedListener {
    return object : VirtualKeyboardView.OnKeyPressedListener {
      override fun onEvent(keyCode: Int) {
        handler.removeCallbacksAndMessages(null)
        if (model.state.value == RestoreSeedState.ERROR) {
          model.state.value = RestoreSeedState.INPUT_DATA
        }
        if (Character.isAlphabetic(keyCode)) {
          model.appendInput((keyCode.toChar()).toString())
        } else if (Character.isWhitespace(keyCode)) {
          if (model.mnemonicsSpanBuilder.value != null && model.mnemonicsSpanBuilder.value!!.isNotEmpty() && !Character.isWhitespace(model.mnemonicsSpanBuilder.value!![model.mnemonicsSpanBuilder.value!!.length - 1])) {
            model.appendInput(' '.toString())
          }
        } else if (keyCode == VirtualKeyboardView.KEY_DELETE) {
          model.removeLastChar()
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
  val userHasCheckedDisclaimer = MutableLiveData<Boolean>()

  private val _mnemonicsSpanBuilder = MutableLiveData(SpannableStringBuilder())
  val mnemonicsSpanBuilder: LiveData<SpannableStringBuilder> get() = _mnemonicsSpanBuilder

  private val _autocompleteSpanBuilder = MutableLiveData(SpannableStringBuilder())
  val autocompleteSpanBuilder: LiveData<SpannableStringBuilder> get() = _autocompleteSpanBuilder

  @UiThread
  fun appendInput(s: String) {
    _mnemonicsSpanBuilder.value?.let {
      _mnemonicsSpanBuilder.value = refreshStyleLastWord(s.isNotEmpty() && s.last().isWhitespace(), it.append(s))
    }
  }

  @UiThread
  fun removeLastChar() {
    _mnemonicsSpanBuilder.value?.let {
      if (it.isNotEmpty()) {
        _mnemonicsSpanBuilder.value = it.delete(it.length - 1, it.length)
      } else {
        it.clear()
      }
      _mnemonicsSpanBuilder.value = refreshStyleLastWord(false, it)
    }
  }

  private fun refreshStyleLastWord(exact: Boolean, sb: SpannableStringBuilder): SpannableStringBuilder {
    val lastWordStartIndex = sb.toString().lastIndexOf(' ') + 1
    val lastWord = sb.subSequence(lastWordStartIndex, sb.length).toString()
    val lastWordSpans = sb.getSpans(lastWordStartIndex, sb.length - 1, StrikethroughSpan::class.java)

    lastWordSpans.forEach { sb.removeSpan(it) }

    // -- strike through last word if it does not match valid BIP39 english words
    val lastWordMatches = getBip39MatchingWords(lastWord)
    if (exact) {
      if (!`MnemonicCode$`.`MODULE$`.englishWordlist().contains(lastWord)) {
        sb.setSpan(StrikethroughSpan(), lastWordStartIndex, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    } else {
      if (lastWordMatches.isEmpty()) {
        sb.setSpan(StrikethroughSpan(), lastWordStartIndex, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    }

    // -- show autocomplete if needed
    when {
      lastWordMatches.isEmpty() -> clearAutoComplete()
      lastWord.length < 2 -> clearAutoComplete()
      else -> showAutocomplete(lastWordMatches)
    }
    return sb
  }

  private fun clearAutoComplete() {
    _autocompleteSpanBuilder.value?.let {
      it.clear()
      _autocompleteSpanBuilder.value = it
    }
  }

  private fun showAutocomplete(matches: List<String>) {
    _autocompleteSpanBuilder.value?.let {
      it.clear()
      for (w in matches) {
        val start = it.length
        it.append(w)
        it.setSpan(object : ClickableSpan() {
          override fun onClick(v: View) {
            clearAutoComplete()
            _mnemonicsSpanBuilder.value?.let { sb ->
              val lastWordStart = sb.toString().lastIndexOf(' ') + 1
              if (lastWordStart >= 0 && lastWordStart <= sb.length) {
                sb.delete(lastWordStart, sb.length)
                sb.append("$w ")
                _mnemonicsSpanBuilder.value = sb
              }
            }
          }
        }, start, start + w.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        it.append("    ")
      }
      _autocompleteSpanBuilder.value = it
    }
  }

  private fun getBip39MatchingWords(start: String): List<String> {
    val matches = ArrayList<String>()
    val it = `MnemonicCode$`.`MODULE$`.englishWordlist().iterator()
    while (it.hasNext()) {
      val word = it.next()
      if (word != null && word.startsWith(start)) {
        matches.add(word)
      }
    }
    return matches
  }

  @UiThread
  fun importAndSaveSeed(context: Context, mnemonics: String) {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        try {
          state.postValue(RestoreSeedState.IN_PROGRESS)
          MnemonicCode.validate(mnemonics)
          val seed: ByteArray = Hex.encode(mnemonics.toByteArray(Charsets.UTF_8))
          delay(500)
          EncryptedSeed.V2.NoAuth.encrypt(seed).let { SeedManager.writeSeedToDisk(Wallet.getDatadir(context), it) }
          log.info("seed written to file")
          state.postValue(RestoreSeedState.DONE)
        } catch (t: Throwable) {
          log.error("cannot import seed: ", t)
          state.postValue(RestoreSeedState.ERROR)
        }
      }
    }
  }
}
