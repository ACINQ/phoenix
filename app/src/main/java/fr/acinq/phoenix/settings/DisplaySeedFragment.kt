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

package fr.acinq.phoenix.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.annotation.UiThread
import androidx.biometric.BiometricManager
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsDisplaySeedBinding
import fr.acinq.phoenix.security.PinDialog
import fr.acinq.phoenix.utils.*
import fr.acinq.phoenix.utils.seed.EncryptedSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex
import java.security.GeneralSecurityException

class DisplaySeedFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsDisplaySeedBinding
  private lateinit var model: DisplaySeedViewModel

  private var mPinDialog: PinDialog? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsDisplaySeedBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    mBinding.instructions.text = Converter.html(getString(R.string.displayseed_instructions))
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(DisplaySeedViewModel::class.java)
    mBinding.model = model
    model.state.observe(viewLifecycleOwner, Observer { state ->
      when (state) {
        is DisplaySeedState.Error.Generic -> {
          context?.let { Toast.makeText(it, getString(R.string.displayseed_error_generic), Toast.LENGTH_SHORT).show() }
        }
        is DisplaySeedState.Error.WrongPassword -> {
          context?.let { Toast.makeText(it, getString(R.string.displayseed_error_wrong_password), Toast.LENGTH_SHORT).show() }
        }
        is DisplaySeedState.Done -> {
          context?.run { getSeedDialog(this, state.words) }?.show()
        }
      }
    })
  }

  override fun onStart() {
    super.onStart()
    mBinding.unlockButton.setOnClickListener { unlockWallet() }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
  }

  override fun onStop() {
    super.onStop()
    mPinDialog?.dismiss()
  }

  private fun unlockWallet() {
    if (model.state.value !is DisplaySeedState.Unlocking) {
      context?.let { ctx ->
        mPinDialog = getPinDialog()
        when {
          Prefs.useBiometrics(ctx) && BiometricManager.from(ctx).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS ->
            getBiometricAuth(negativeCallback = {
              mPinDialog?.reset()
              mPinDialog?.show()
            }, successCallback = {
              try {
                model.state.value = DisplaySeedState.Unlocking
                val pin = KeystoreHelper.decryptPin(ctx)?.toString(Charsets.UTF_8)
                model.getSeed(ctx, pin!!)
              } catch (e: Exception) {
                log.error("could not decrypt pin: ", e)
                model.state.value = DisplaySeedState.Error.Generic
              }
            })
          Prefs.isSeedEncrypted(ctx) -> {
            mPinDialog?.reset()
            mPinDialog?.show()
          }
          else -> {
            model.state.value = DisplaySeedState.Unlocking
            model.getSeed(ctx, null)
          }
        }
      }
    }
  }

  private fun getPinDialog(): PinDialog? {
    return mPinDialog ?: getPinDialog(object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        context?.let {
          model.state.value = DisplaySeedState.Unlocking
          model.getSeed(it, pinCode)
        }
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {}
    })
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
      .setPositiveButton(R.string.btn_ok, null)
      .create()

    // disable screen capture
    dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

    dialog.setOnShowListener {
      val confirmButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
      confirmButton.setOnClickListener {
        if (backupDoneCheckbox.isChecked) {
          Prefs.setMnemonicsSeenTimestamp(context, System.currentTimeMillis())
          dialog.dismiss()
          findNavController().navigate(R.id.global_action_any_to_main)
        } else {
          dialog.dismiss()
        }
      }
    }
    return dialog
  }

  private fun buildWordView(i: Int, word: String, hasRightPadding: Boolean): TextView {
    val bottomPadding = resources.getDimensionPixelSize(R.dimen.space_xxs)
    val rightPadding = if (hasRightPadding) resources.getDimensionPixelSize(R.dimen.space_lg) else 0
    val textView = TextView(context)
    textView.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
    textView.text = Converter.html(getString(R.string.displayseed_words_td, i + 1, word))
    textView.setPadding(0, 0, rightPadding, bottomPadding)
    return textView
  }
}

sealed class DisplaySeedState {
  object Init : DisplaySeedState()
  object Unlocking : DisplaySeedState()
  data class Done(val words: List<String>) : DisplaySeedState()
  sealed class Error : DisplaySeedState() {
    object WrongPassword : Error()
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
  fun getSeed(context: Context, pin: String?) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val words = String(Hex.decode(EncryptedSeed.readSeedFromDir(Wallet.getDatadir(context), pin)), Charsets.UTF_8).split(" ")
        state.postValue(DisplaySeedState.Done(words))
      } catch (t: Throwable) {
        log.error("could not read seed: ", t)
        when (t) {
          is GeneralSecurityException -> state.postValue(DisplaySeedState.Error.WrongPassword)
          else -> state.postValue(DisplaySeedState.Error.Generic)
        }
      }
    }
  }
}
