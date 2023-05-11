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

package fr.acinq.phoenix.legacy.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.FragmentSettingsDisplaySeedBinding
import fr.acinq.phoenix.legacy.utils.BindingHelpers
import fr.acinq.phoenix.legacy.utils.Converter
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.Wallet
import fr.acinq.phoenix.legacy.utils.crypto.AuthHelper
import fr.acinq.phoenix.legacy.utils.crypto.EncryptedSeed
import fr.acinq.phoenix.legacy.utils.crypto.SeedManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.GeneralSecurityException
import javax.crypto.Cipher

class DisplaySeedFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsDisplaySeedBinding
  private lateinit var model: DisplaySeedViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsDisplaySeedBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    mBinding.instructions.text = Converter.html(getString(R.string.legacy_displayseed_instructions))
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(DisplaySeedViewModel::class.java)
    mBinding.model = model
    model.state.observe(viewLifecycleOwner, Observer { state ->
      when (state) {
        is DisplaySeedState.Error.Generic -> {
          context?.let { Toast.makeText(it, getString(R.string.legacy_displayseed_error_generic), Toast.LENGTH_SHORT).show() }
        }
        is DisplaySeedState.Error.InvalidAuth -> {
          context?.let { Toast.makeText(it, getString(R.string.legacy_startup_error_auth_failed), Toast.LENGTH_SHORT).show() }
        }
        is DisplaySeedState.Done -> {
          context?.run { getSeedDialog(this, state.words) }?.show()
        }
        else -> {}
      }
    })
  }

  override fun onStart() {
    super.onStart()
    mBinding.unlockButton.setOnClickListener { context?.let { unlockWallet(it) } }
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
  }

  @UiThread
  private fun unlockWallet(context: Context) {
    if (model.state.value !is DisplaySeedState.Unlocking) {
      model.state.value = DisplaySeedState.Unlocking
      val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(context))
      if (encryptedSeed is EncryptedSeed.V2.NoAuth) {
        if (Prefs.isScreenLocked(context)) {
          AuthHelper.promptSoftAuth(this,
            onSuccess = {
              model.decrypt(encryptedSeed, null)
            }, onFailure = {
              model.state.value = DisplaySeedState.Error.InvalidAuth
            }, onCancel = {
              model.state.value = DisplaySeedState.Init
            })
        } else {
          model.decrypt(encryptedSeed, null)
        }
      } else if (encryptedSeed is EncryptedSeed.V2.WithAuth) {
        val cipher = try {
          encryptedSeed.getDecryptionCipher()
        } catch (e: Exception) {
          model.state.value = DisplaySeedState.Error.InvalidAuth
          return
        }
        AuthHelper.promptHardAuth(this,
          cipher = cipher,
          onSuccess = {
            model.decrypt(encryptedSeed, it?.cipher)
          },
          onFailure = { model.state.value = DisplaySeedState.Error.InvalidAuth })
      }
    }
  }

  private fun getSeedDialog(context: Context, words: List<String>): AlertDialog {
    val view = layoutInflater.inflate(R.layout.dialog_seed, null)
    val wordsTable = view.findViewById<TableLayout>(R.id.seed_dialog_words_table)
    val backupDoneCheckbox = view.findViewById<CheckBox>(R.id.seed_dialog_backup_done_checkbox)

    // only show the backup checkbox if needed
    BindingHelpers.show(backupDoneCheckbox, Prefs.getMnemonicsSeenTimestamp(context) == 0L)

    wordsTable.removeAllViews()
    var i = 0
    while (i < words.size / 2) {
      val row = TableRow(context)
      row.gravity = Gravity.CENTER
      row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
      row.addView(buildWordView(i, words[i], true))
      row.addView(buildWordView(i + words.size / 2, words[i + (words.size / 2)], false))
      wordsTable.addView(row)
      i += 1
    }

    val dialog = AlertDialog.Builder(context, R.style.default_dialogTheme)
      .setView(view)
      .setPositiveButton(R.string.legacy_btn_ok, null)
      .create()

    // disable screen capture
    dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

    dialog.setOnShowListener {
      val confirmButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
      confirmButton.setOnClickListener {
        if (backupDoneCheckbox.isChecked) {
          Prefs.setMnemonicsSeenTimestamp(context, System.currentTimeMillis())
        }
        dialog.dismiss()
      }
    }
    return dialog
  }

  private fun buildWordView(i: Int, word: String, hasRightPadding: Boolean): TextView {
    val bottomPadding = resources.getDimensionPixelSize(R.dimen.space_xxs)
    val rightPadding = if (hasRightPadding) resources.getDimensionPixelSize(R.dimen.space_lg) else 0
    val textView = TextView(context)
    textView.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
    textView.text = Converter.html(getString(R.string.legacy_displayseed_words_td, i + 1, word))
    textView.setPadding(0, 0, rightPadding, bottomPadding)
    return textView
  }
}

sealed class DisplaySeedState {
  object Init : DisplaySeedState()
  object Unlocking : DisplaySeedState()
  data class Done(val words: List<String>) : DisplaySeedState()
  sealed class Error : DisplaySeedState() {
    object InvalidAuth : Error()
    object Generic : Error()
  }
}

class DisplaySeedViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(DisplaySeedViewModel::class.java)
  val state = MutableLiveData<DisplaySeedState>()

  init {
    state.value = DisplaySeedState.Init
  }

  @UiThread
  fun decrypt(encryptedSeed: EncryptedSeed.V2, cipher: Cipher?) {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("could not decrypt seed: ", e)
      state.postValue(when (e) {
        is GeneralSecurityException -> DisplaySeedState.Error.InvalidAuth
        else -> DisplaySeedState.Error.Generic
      })
    }) {
      when (encryptedSeed) {
        is EncryptedSeed.V2.NoAuth -> encryptedSeed.decrypt()
        is EncryptedSeed.V2.WithAuth -> encryptedSeed.decrypt(cipher)
      }.let {
        val words = EncryptedSeed.byteArray2String(it).split(" ")
        state.postValue(DisplaySeedState.Done(words))
      }
    }
  }
}
