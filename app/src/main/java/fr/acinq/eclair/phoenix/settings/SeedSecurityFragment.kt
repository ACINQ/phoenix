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
    model.pinUpdateState.observe(viewLifecycleOwner, Observer { state ->
      when (state) {
        SeedSecurityViewModel.PinUpdateState.ABORT_PIN_UPDATE -> Handler().postDelayed({ model.pinUpdateState.value = SeedSecurityViewModel.PinUpdateState.IDLE }, 2500)
        SeedSecurityViewModel.PinUpdateState.SUCCESS_PIN_UPDATE -> Handler().postDelayed({ model.pinUpdateState.value = SeedSecurityViewModel.PinUpdateState.IDLE }, 4500)
        SeedSecurityViewModel.PinUpdateState.SET_NEW_PIN -> promptNewPin()
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
      model.pinUpdateState.value = SeedSecurityViewModel.PinUpdateState.ENTER_PIN
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
        model.pinUpdateState.value = SeedSecurityViewModel.PinUpdateState.IDLE
      }
    }).show()
  }

  private fun confirmNewPin(firstPin: String) {
    getPinDialog(R.string.seedsec_pindialog_title_confirm_new, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        if (pinCode == firstPin) {
          model.encryptSeed(context, pinCode)
        } else {
          model.pinUpdateState.value = SeedSecurityViewModel.PinUpdateState.ABORT_PIN_UPDATE
          model.errorMessage.value = R.string.seedsec_error_pins_match
        }
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.pinUpdateState.value = SeedSecurityViewModel.PinUpdateState.IDLE
      }
    }).show()
  }
}

class SeedSecurityViewModel : ViewModel() {

  enum class PinUpdateState {
    ENTER_PIN, SET_NEW_PIN, ENCRYPTING, ABORT_PIN_UPDATE, SUCCESS_PIN_UPDATE, IDLE
  }

  private val log = LoggerFactory.getLogger(DisplaySeedViewModel::class.java)

  private val seed = MutableLiveData<ByteArray>(null)
  val pinUpdateState = MutableLiveData(PinUpdateState.IDLE)
  val errorMessage = MutableLiveData<Int>(null)
  val isSeedEncrypted = MutableLiveData(false)

  val newPinInProgress: LiveData<Boolean> = Transformations.map(pinUpdateState) {
    it == PinUpdateState.ENTER_PIN || it == PinUpdateState.SET_NEW_PIN || it == PinUpdateState.ENCRYPTING
  }

  /**
   * Reads the seed from file and update state
   */
  @UiThread
  fun readSeed(context: Context?, pin: String) {
    if (pinUpdateState.value != PinUpdateState.IDLE) {
      log.error("cannot read seed in state ${pinUpdateState.value}")
    }
    if (context != null) {
      viewModelScope.launch {
        withContext(Dispatchers.Default) {
          try {
            seed.postValue(EncryptedSeed.readSeedFile(context, pin))
            pinUpdateState.postValue(PinUpdateState.SET_NEW_PIN)
          } catch (e: Exception) {
            log.error("could not read seed: ", e)
            if (pin == Wallet.DEFAULT_PIN) {
              // preference pin status is probably obsolete
              Prefs.setIsSeedEncrypted(context)
              isSeedEncrypted.postValue(true)
            }
            pinUpdateState.postValue(PinUpdateState.ABORT_PIN_UPDATE)
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
          if (pinUpdateState.value != PinUpdateState.SET_NEW_PIN) {
            throw java.lang.RuntimeException("cannot encrypt seed in state ${pinUpdateState.value}")
          }
          pinUpdateState.postValue(PinUpdateState.ENCRYPTING)
          EncryptedSeed.writeSeedToFile(context!!, seed.value ?: throw RuntimeException("empty seed"), pin)
          pinUpdateState.postValue(PinUpdateState.SUCCESS_PIN_UPDATE)
        } catch (e: java.lang.Exception) {
          log.error("could not encrypt seed: ", e)
          pinUpdateState.postValue(PinUpdateState.ABORT_PIN_UPDATE)
          errorMessage.postValue(R.string.seedsec_error_generic)
        }
      }
    }
  }
}
