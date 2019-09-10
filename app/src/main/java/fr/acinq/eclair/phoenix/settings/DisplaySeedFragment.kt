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

package fr.acinq.eclair.phoenix.settings

import android.content.Context
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableRow
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsDisplaySeedBinding
import fr.acinq.eclair.phoenix.security.PinDialog
import fr.acinq.eclair.phoenix.utils.Prefs
import fr.acinq.eclair.phoenix.utils.Wallet
import fr.acinq.eclair.phoenix.utils.encrypt.EncryptedSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex

class DisplaySeedFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsDisplaySeedBinding
  private lateinit var model: DisplaySeedViewModel

  private var mPinDialog: PinDialog? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsDisplaySeedBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    mBinding.instructions.text = Html.fromHtml(getString(R.string.displayseed_instructions), Html.FROM_HTML_MODE_COMPACT)
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(DisplaySeedViewModel::class.java)
    mBinding.model = model
    model.words.observe(viewLifecycleOwner, Observer { words ->
      mBinding.wordsTable.removeAllViews()
      var i = 0
      while (i < words.size / 2) {
        val row = TableRow(context)
        row.gravity = Gravity.CENTER
        row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
        row.addView(buildWordView(i, words[i], true))
        row.addView(buildWordView(i + words.size / 2, words[i + (words.size / 2)], false))
        mBinding.wordsTable.addView(row)
        i += 1
      }
    })
    model.errorMessage.observe(viewLifecycleOwner, Observer {
      mBinding.errorView.text = getString(R.string.displayseed_error, it)
    })
  }

  override fun onStart() {
    super.onStart()
    refreshBackupWarning()
    context?.let {
      mBinding.backupWarningButton.setOnClickListener { _ ->
        if (mBinding.backupWarningCheckbox.isChecked) {
          Prefs.setMnemonicsSeenTimestamp(it, System.currentTimeMillis())
          refreshBackupWarning()
        }
      }
    }

    mBinding.backupWarningCheckbox.setOnCheckedChangeListener { _, isChecked -> model.userHasSavedSeed.value = isChecked }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })

    // -- retrieve seed if adequate
    if (model.state.value == DisplaySeedState.INIT && model.words.value.isNullOrEmpty()) {
      context?.let {
        model.state.value = DisplaySeedState.UNLOCKING
        if (Prefs.getIsSeedEncrypted(it)) {
          getPinDialog().show()
        } else {
          model.getSeed(it, Wallet.DEFAULT_PIN)
        }
      }
    }
  }

  override fun onStop() {
    super.onStop()
    mPinDialog?.dismiss()
  }

  private fun refreshBackupWarning() {
    context?.let {
      model.showUserBackupWarning.value = Prefs.getMnemonicsSeenTimestamp(it) == 0L
    }
  }

  private fun getPinDialog(): PinDialog {
    return mPinDialog ?: getPinDialog(object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        context?.let { model.getSeed(it, pinCode) }
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {}
    })
  }

  private fun buildWordView(i: Int, word: String, hasRightPadding: Boolean): TextView {
    val bottomPadding = resources.getDimensionPixelSize(R.dimen.space_xxs)
    val rightPadding = if (hasRightPadding) resources.getDimensionPixelSize(R.dimen.space_lg) else 0
    val textView = TextView(context)
    textView.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
    textView.text = Html.fromHtml(getString(R.string.newseed_words_td, i + 1, word))
    textView.setPadding(0, 0, rightPadding, bottomPadding)
    return textView
  }
}

enum class DisplaySeedState {
  INIT, UNLOCKING, DONE, ERROR
}

class DisplaySeedViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(DisplaySeedViewModel::class.java)

  val state = MutableLiveData(DisplaySeedState.INIT)
  val errorMessage = MutableLiveData("")
  val words = MutableLiveData<List<String>>()

  val showUserBackupWarning = MutableLiveData(true)
  val userHasSavedSeed = MutableLiveData(false)

  @UiThread
  fun getSeed(context: Context, pin: String) {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        try {
          words.postValue(String(Hex.decode(EncryptedSeed.readSeedFile(context, pin)), Charsets.UTF_8).split(" "))
          state.postValue(DisplaySeedState.DONE)
        } catch (t: Throwable) {
          log.error("could not read seed: ", t)
          state.postValue(DisplaySeedState.ERROR)
          errorMessage.postValue(t.message)
        }
      }
    }
  }
}
