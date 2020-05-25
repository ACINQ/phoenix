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

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsSeedSecurityBinding
import fr.acinq.phoenix.security.PinDialog
import fr.acinq.phoenix.utils.*
import fr.acinq.phoenix.utils.seed.EncryptedSeed
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SeedSecurityFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsSeedSecurityBinding
  private lateinit var model: SeedSecurityViewModel

  private var promptPin: PinDialog? = null
  private var newPin: PinDialog? = null
  private var confirmPin: PinDialog? = null
  private var biometricPrompt: BiometricPrompt? = null

  private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String ->
    when (key) {
      Prefs.PREFS_SCRAMBLE_PIN -> context?.let {
        val isPinScrambled = Prefs.isPinScrambled(it)
        mBinding.pinScramble.setChecked(isPinScrambled)
      }
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsSeedSecurityBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(SeedSecurityViewModel::class.java)
    model.protectionState.observe(viewLifecycleOwner, Observer { state ->
      when (state!!) {
        SeedSecurityViewModel.ProtectionState.NONE -> {
          mBinding.pinSwitch.setChecked(false)
          mBinding.biometricsSwitch.setChecked(false)
        }
        SeedSecurityViewModel.ProtectionState.PIN_ONLY -> {
          mBinding.pinSwitch.setChecked(true)
          mBinding.biometricsSwitch.setChecked(false)
        }
        SeedSecurityViewModel.ProtectionState.PIN_AND_BIOMETRICS -> {
          mBinding.pinSwitch.setChecked(true)
          mBinding.biometricsSwitch.setChecked(true)
        }
      }
    })
    model.protectionUpdateState.observe(viewLifecycleOwner, Observer { state ->
      when (state) {
        SeedSecurityViewModel.ProtectionUpdateState.SET_NEW_PIN -> promptNewPin()
        else -> Unit
      }
    })
    model.messageEvent.observe(viewLifecycleOwner, Observer {
      it?.let { Toast.makeText(context, getString(it), Toast.LENGTH_SHORT).show() }
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    context?.let {
      val isPinScrambled = Prefs.isPinScrambled(it)
      mBinding.pinScramble.setChecked(isPinScrambled)
      PreferenceManager.getDefaultSharedPreferences(it).registerOnSharedPreferenceChangeListener(prefsListener)
    }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.pinSwitch.setOnClickListener {
      model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.ENTER_PIN
      model.readSeed(context, null)
    }
    mBinding.pinScramble.setOnClickListener {
      context?.let {
        Prefs.savePinScrambled(it, !mBinding.pinScramble.isChecked())
      }
    }
    mBinding.updatePinButton.setOnClickListener {
      model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.ENTER_PIN
      promptExistingPin { pin: String -> model.readSeed(context, pin) }
    }
    mBinding.biometricsSwitch.setOnClickListener {
      context?.let { ctx ->
        when (BiometricManager.from(ctx).canAuthenticate()) {
          BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Toast.makeText(context, R.string.seedsec_biometric_support_no_hw, Toast.LENGTH_SHORT).show()
          BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Toast.makeText(context, R.string.seedsec_biometric_support_hw_unavailable, Toast.LENGTH_SHORT).show()
          BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Toast.makeText(context, R.string.seedsec_biometric_support_none_enrolled, Toast.LENGTH_SHORT).show()
          BiometricManager.BIOMETRIC_SUCCESS -> if (mBinding.biometricsSwitch.isChecked()) {
            disableBiometrics(ctx)
          } else {
            enrollBiometrics(ctx)
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    checkState()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    biometricPrompt?.cancelAuthentication()
  }

  override fun onStop() {
    super.onStop()
    promptPin?.dismiss()
    newPin?.dismiss()
    confirmPin?.dismiss()
    model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE
    PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(prefsListener)
  }

  private fun checkState() {
    val isSeedEncrypted = context?.let { ctx -> Prefs.isSeedEncrypted(ctx) } ?: true
    val useBiometrics = context?.let { ctx -> Prefs.useBiometrics(ctx) } ?: false
    model.protectionState.value = if (isSeedEncrypted) {
      if (useBiometrics) {
        SeedSecurityViewModel.ProtectionState.PIN_AND_BIOMETRICS
      } else {
        SeedSecurityViewModel.ProtectionState.PIN_ONLY
      }
    } else {
      SeedSecurityViewModel.ProtectionState.NONE
    }
  }

  private fun disableBiometrics(context: Context) {
    biometricPrompt = getBiometricAuth(R.string.seedsec_disable_bio_prompt_title, R.string.seedsec_disable_bio_prompt_negative, R.string.seedsec_disable_bio_prompt_desc, {
      model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
    }, {
      try {
        Prefs.useBiometrics(context, false)
        KeystoreHelper.deleteKeyForPin()
        model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
        model.protectionState.postValue(SeedSecurityViewModel.ProtectionState.PIN_ONLY)
        model.messageEvent.postValue(R.string.seedsec_biometric_disabled)
      } catch (e: Exception) {
        log.error("could not disable bio: ", e)
      }
    })
  }

  private fun enrollBiometrics(context: Context) {
    promptExistingPin { pin: String ->
      lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
        log.error("error when enrolling biometric: ", exception)
        model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE
        model.messageEvent.value = R.string.seedsec_biometric_enrollment_error
      }) {
        model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.ENROLLING_BIOMETRICS
        if (model.checkPIN(context, pin)) {
          // generate new key for pin code encryption (if necessary remove the old one)
          KeystoreHelper.deleteKeyForPin()
          KeystoreHelper.generateKeyForPin()
          biometricPrompt = getBiometricAuth(R.string.seedsec_bio_prompt_title, R.string.seedsec_bio_prompt_negative, null, {
            model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
          }, {
            try {
              KeystoreHelper.encryptPin(context, pin)
              Prefs.useBiometrics(context, true)
              model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
              model.protectionState.postValue(SeedSecurityViewModel.ProtectionState.PIN_AND_BIOMETRICS)
              model.messageEvent.postValue(R.string.seedsec_biometric_enabled)
            } catch (e: Exception) {
              log.error("could not encrypt pin in keystore: ", e)
            }
          })
        } else {
          model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE
          model.messageEvent.value = R.string.seedsec_error_wrong_pin
        }
      }
    }
  }

  private fun promptExistingPin(callback: (pin: String) -> Unit) {
    promptPin = getPinDialog(R.string.seedsec_pindialog_title_unlock, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        callback(pinCode)
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
      }
    })
    promptPin?.reset()
    promptPin?.show()
  }

  private fun promptNewPin() {
    newPin = getPinDialog(R.string.seedsec_pindialog_title_set_new, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        confirmNewPin(pinCode)
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE
      }
    })
    newPin?.reset()
    newPin?.show()
  }

  private fun confirmNewPin(firstPin: String) {
    confirmPin = getPinDialog(R.string.seedsec_pindialog_title_confirm_new, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        if (pinCode == firstPin) {
          model.encryptSeed(context, pinCode)
        } else {
          model.messageEvent.value = R.string.seedsec_error_pins_match
          model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE
        }
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE
      }
    })
    confirmPin?.reset()
    confirmPin?.show()
  }
}

class SeedSecurityViewModel : ViewModel() {

  enum class ProtectionUpdateState {
    ENTER_PIN, SET_NEW_PIN, ENCRYPTING, ENROLLING_BIOMETRICS, IDLE
  }

  enum class ProtectionState {
    NONE, PIN_ONLY, PIN_AND_BIOMETRICS
  }

  private val log = LoggerFactory.getLogger(DisplaySeedViewModel::class.java)

  private val seed = MutableLiveData<ByteArray>(null)
  val protectionState = MutableLiveData(ProtectionState.NONE)
  val protectionUpdateState = MutableLiveData(ProtectionUpdateState.IDLE)
  val messageEvent = SingleLiveEvent<Int>()

  @UiThread
  suspend fun checkPIN(context: Context, pin: String): Boolean {
    return coroutineScope {
      async(Dispatchers.IO) {
        try {
          EncryptedSeed.readSeedFromDir(Wallet.getDatadir(context), pin)
          true
        } catch (e: Exception) {
          log.error("pin is not valid: ", e)
          false
        }
      }
    }.await()
  }

  @UiThread
  fun readSeed(context: Context?, pin: String?) {
    if (context != null) {
      viewModelScope.launch(Dispatchers.IO) {
        try {
          seed.postValue(EncryptedSeed.readSeedFromDir(Wallet.getDatadir(context), pin))
          protectionUpdateState.postValue(ProtectionUpdateState.SET_NEW_PIN)
        } catch (e: Exception) {
          log.error("could not read seed: ", e)
          if (pin == null) {
            // preference encryption state is not in sync
            Prefs.setIsSeedEncrypted(context, true)
          }
          messageEvent.postValue(R.string.seedsec_error_wrong_pin)
          protectionUpdateState.postValue(ProtectionUpdateState.IDLE)
        }
      }
    }
  }

  @UiThread
  fun encryptSeed(context: Context?, pin: String?) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        if (protectionUpdateState.value != ProtectionUpdateState.SET_NEW_PIN) {
          throw java.lang.RuntimeException("cannot encrypt seed in state ${protectionUpdateState.value}")
        }
        protectionUpdateState.postValue(ProtectionUpdateState.ENCRYPTING)
        EncryptedSeed.writeSeedToDir(Wallet.getDatadir(context!!), seed.value!!, pin)
        protectionUpdateState.postValue(ProtectionUpdateState.IDLE)
        protectionState.postValue(ProtectionState.PIN_ONLY)
        KeystoreHelper.deleteKeyForPin()
        Prefs.setIsSeedEncrypted(context, true)
        Prefs.useBiometrics(context, false)
        messageEvent.postValue(R.string.seedsec_pin_update_success)
      } catch (e: java.lang.Exception) {
        log.error("could not encrypt seed: ", e)
        messageEvent.postValue(R.string.seedsec_error_generic)
        protectionUpdateState.postValue(ProtectionUpdateState.IDLE)
      }
    }
  }
}
