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
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.*
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsSeedSecurityBinding
import fr.acinq.eclair.phoenix.security.PinDialog
import fr.acinq.eclair.phoenix.utils.Prefs
import fr.acinq.eclair.phoenix.utils.Wallet
import fr.acinq.eclair.phoenix.utils.encrypt.EncryptedSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SeedSecurityFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsSeedSecurityBinding
  private lateinit var model: SeedSecurityViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsSeedSecurityBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(SeedSecurityViewModel::class.java)
    model.state.observe(viewLifecycleOwner, Observer { state ->
      when (state) {
        SeedSecurityState.ABORT_PIN_UPDATE -> Handler().postDelayed({ model.state.value = SeedSecurityState.IDLE }, 2500)
        SeedSecurityState.SUCCESS_PIN_UPDATE -> Handler().postDelayed({ model.state.value = SeedSecurityState.IDLE }, 4500)
        SeedSecurityState.SET_NEW_PIN -> promptNewPin()
        else -> {
        }
      }
    })
    model.errorMessage.observe(viewLifecycleOwner, Observer {
      it?.let { mBinding.error.text = getString(it) }
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    val isSeedEncrypted = context?.let { ctx -> Prefs.getIsSeedEncrypted(ctx) } ?: true
    model.isSeedEncrypted.value = isSeedEncrypted
    mBinding.setPinButton.setOnClickListener {
      model.state.value = SeedSecurityState.ENTER_PIN
      if (isSeedEncrypted) {
        promptExistingPin()
      } else {
        model.readSeed(context, Wallet.DEFAULT_PIN)
      }
    }
  }

  private fun promptExistingPin() {
    getPinDialog(R.string.seedsec_pindialog_title_unlock, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        model.readSeed(context, pinCode)
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {}
    }).show()
  }

  private fun promptNewPin() {
    getPinDialog(R.string.seedsec_pindialog_title_set_new, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        confirmNewPin(pinCode)
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.state.value = SeedSecurityState.IDLE
      }
    }).show()
  }

  private fun confirmNewPin(firstPin: String) {
    getPinDialog(R.string.seedsec_pindialog_title_confirm_new, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        if (pinCode == firstPin) {
          model.encryptSeed(context, pinCode)
        } else {
          model.state.value = SeedSecurityState.ABORT_PIN_UPDATE
          model.errorMessage.value = R.string.seedsec_error_pins_match
        }
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.state.value = SeedSecurityState.IDLE
      }
    }).show()
  }
}

enum class SeedSecurityState {
  ENTER_PIN, SET_NEW_PIN, ENCRYPTING, ABORT_PIN_UPDATE, SUCCESS_PIN_UPDATE, IDLE
}

class SeedSecurityViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(DisplaySeedViewModel::class.java)

  private val seed = MutableLiveData<ByteArray>(null)
  val state = MutableLiveData(SeedSecurityState.IDLE)
  val errorMessage = MutableLiveData<Int>(null)
  val isSeedEncrypted = MutableLiveData(false)

  val newPinInProgress: LiveData<Boolean> = Transformations.map(state) {
    it == SeedSecurityState.ENTER_PIN || it == SeedSecurityState.SET_NEW_PIN || it == SeedSecurityState.ENCRYPTING
  }

  /**
   * Reads the seed from file and update state
   */
  @UiThread
  fun readSeed(context: Context?, pin: String) {
    if (state.value != SeedSecurityState.IDLE) {
      log.error("cannot read seed in state ${state.value}")
    }
    if (context != null) {
      viewModelScope.launch {
        withContext(Dispatchers.Default) {
          try {
            seed.postValue(EncryptedSeed.readSeedFile(context, pin))
            state.postValue(SeedSecurityState.SET_NEW_PIN)
          } catch (e: Exception) {
            log.error("could not read seed: ", e)
            if (pin == Wallet.DEFAULT_PIN) {
              // preference pin status is probably obsolete
              Prefs.setIsSeedEncrypted(context)
              isSeedEncrypted.postValue(true)
            }
            state.postValue(SeedSecurityState.ABORT_PIN_UPDATE)
            errorMessage.postValue(R.string.seedsec_error_wrong_pin)
          }
        }
      }
    }
  }

  /**
   * Write a seed to file with a synchronous coroutine
   */
  @UiThread
  fun encryptSeed(context: Context?, pin: String) {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        try {
          if (state.value != SeedSecurityState.SET_NEW_PIN) {
            throw java.lang.RuntimeException("cannot encrypt seed in state ${state.value}")
          }
          state.postValue(SeedSecurityState.ENCRYPTING)
          EncryptedSeed.writeSeedToFile(context!!, seed.value ?: throw RuntimeException("empty seed"), pin)
          state.postValue(SeedSecurityState.SUCCESS_PIN_UPDATE)
        } catch (e: java.lang.Exception) {
          log.error("could not encrypt seed: ", e)
          state.postValue(SeedSecurityState.ABORT_PIN_UPDATE)
          errorMessage.postValue(R.string.seedsec_error_generic)
        }
      }
    }
  }
}
