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
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsSeedSecurityBinding
import fr.acinq.eclair.phoenix.security.PinDialog
import fr.acinq.eclair.phoenix.utils.KeystoreHelper
import fr.acinq.eclair.phoenix.utils.Prefs
import fr.acinq.eclair.phoenix.utils.Wallet
import fr.acinq.eclair.phoenix.utils.encrypt.EncryptedSeed
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

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
    model.protectionState.observe(viewLifecycleOwner, Observer { state ->
      when (state!!) {
        SeedSecurityViewModel.ProtectionState.NONE -> {
          mBinding.biometricsCheckbox.setChecked(false)
          mBinding.setPinButton.setText(getString(R.string.seedsec_setup_pin))
        }
        SeedSecurityViewModel.ProtectionState.PIN_ONLY -> {
          mBinding.biometricsCheckbox.setChecked(false)
          mBinding.setPinButton.setText(getString(R.string.seedsec_change_pin))
        }
        SeedSecurityViewModel.ProtectionState.PIN_AND_BIOMETRICS -> {
          mBinding.biometricsCheckbox.setChecked(true)
          mBinding.setPinButton.setText(getString(R.string.seedsec_change_pin))
        }
      }
    })
    model.protectionUpdateState.observe(viewLifecycleOwner, Observer { state ->
      when (state) {
        SeedSecurityViewModel.ProtectionUpdateState.ERROR -> Handler().postDelayed({ model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE }, 2500)
        SeedSecurityViewModel.ProtectionUpdateState.SET_NEW_PIN -> promptNewPin()
        else -> Unit
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

    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })

    mBinding.setPinButton.setOnClickListener {
      model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.ENTER_PIN
      if (isSeedEncrypted) {
        promptExistingPin { pin: String -> model.readSeed(context, pin) }
      } else {
        model.readSeed(context, Wallet.DEFAULT_PIN)
      }
    }

    mBinding.biometricsCheckbox.setOnClickListener {
      context?.let { ctx ->
        if (mBinding.biometricsCheckbox.isChecked()) {
          disableBiometrics(ctx)
        } else {
          enrollBiometrics(ctx)
        }
      }
    }
  }

  private fun disableBiometrics(context: Context) {
    // we just need biometric confirmation
    val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(getString(R.string.seedsec_disable_bio_prompt_title))
      .setSubtitle(getString(R.string.seedsec_disable_bio_prompt_subtitle))
      .setNegativeButtonText(getString(R.string.seedsec_disable_bio_prompt_negative))
      .build()

    val biometricPrompt = BiometricPrompt(this@SeedSecurityFragment, Executors.newSingleThreadExecutor(), object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        log.info("biometric auth error ($errorCode): $errString")
        model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        log.info("biometric auth is not recognized")
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        try {
          Prefs.saveUseBiometrics(context, false)
          KeystoreHelper.deleteKeyForPin()
          model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
          model.protectionState.postValue(SeedSecurityViewModel.ProtectionState.PIN_ONLY)
        } catch (e: Exception) {
          log.error("could not disable bio: ", e)
        }
      }
    })

    biometricPrompt.authenticate(biometricPromptInfo)
  }

  private fun enrollBiometrics(context: Context) {
    promptExistingPin { pin: String ->
      lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
        log.error("error when enrolling biometric: ", exception)
        model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.ERROR
      }) {
        model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.ENROLLING_BIOMETRICS
        if (model.checkPIN(context, pin)) {
          // generate new key for pin code encryption (if necessary remove the old one)
          KeystoreHelper.deleteKeyForPin()
          KeystoreHelper.generateKeyForPin()

          val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.seedsec_bio_prompt_title))
            .setSubtitle(getString(R.string.seedsec_bio_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.seedsec_bio_prompt_negative))
            .build()

          val biometricPrompt = BiometricPrompt(this@SeedSecurityFragment, Executors.newSingleThreadExecutor(), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
              super.onAuthenticationError(errorCode, errString)
              log.info("biometric auth error ($errorCode): $errString")
              model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
            }

            override fun onAuthenticationFailed() {
              super.onAuthenticationFailed()
              log.info("biometric auth is not recognized")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
              super.onAuthenticationSucceeded(result)
              try {
                KeystoreHelper.encryptPin(context, pin)
                Prefs.saveUseBiometrics(context, true)
                model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
                model.protectionState.postValue(SeedSecurityViewModel.ProtectionState.PIN_AND_BIOMETRICS)
              } catch (e: Exception) {
                log.error("could not encrypt pin in keystore: ", e)
              }
            }
          })

          biometricPrompt.authenticate(biometricPromptInfo)
        } else {
          throw java.lang.RuntimeException("invalid PIN")
        }
      }
    }
  }

  private fun promptExistingPin(callback: (pin: String) -> Unit) {
    getPinDialog(R.string.seedsec_pindialog_title_unlock, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        callback(pinCode)
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.protectionUpdateState.postValue(SeedSecurityViewModel.ProtectionUpdateState.IDLE)
      }
    }).show()
  }

  private fun promptNewPin() {
    getPinDialog(R.string.seedsec_pindialog_title_set_new, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        confirmNewPin(pinCode)
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE
      }
    }).show()
  }

  private fun confirmNewPin(firstPin: String) {
    getPinDialog(R.string.seedsec_pindialog_title_confirm_new, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
        if (pinCode == firstPin) {
          model.encryptSeed(context, pinCode)
        } else {
          model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.ERROR
          model.errorMessage.value = R.string.seedsec_error_pins_match
        }
        dialog.dismiss()
      }

      override fun onPinCancel(dialog: PinDialog) {
        model.protectionUpdateState.value = SeedSecurityViewModel.ProtectionUpdateState.IDLE
      }
    }).show()
  }
}

class SeedSecurityViewModel : ViewModel() {

  enum class ProtectionUpdateState {
    ENTER_PIN, SET_NEW_PIN, ENCRYPTING, ERROR, ENROLLING_BIOMETRICS, IDLE
  }

  enum class ProtectionState {
    NONE, PIN_ONLY, PIN_AND_BIOMETRICS
  }

  private val log = LoggerFactory.getLogger(DisplaySeedViewModel::class.java)

  private val seed = MutableLiveData<ByteArray>(null)
  val protectionState = MutableLiveData(ProtectionState.NONE)
  val protectionUpdateState = MutableLiveData(ProtectionUpdateState.IDLE)
  val errorMessage = MutableLiveData<Int>(null)
  val biometricsSupport = MutableLiveData(true)

  val newPinInProgress: LiveData<Boolean> = Transformations.map(protectionUpdateState) {
    it == ProtectionUpdateState.ENTER_PIN || it == ProtectionUpdateState.SET_NEW_PIN || it == ProtectionUpdateState.ENCRYPTING || it == ProtectionUpdateState.ENROLLING_BIOMETRICS
  }

  /**
   * Check if the PIN is correct
   */
  @UiThread
  suspend fun checkPIN(context: Context, pin: String): Boolean {
    return coroutineScope {
      async(Dispatchers.Default) {
        try {
          EncryptedSeed.readSeedFile(context, pin)
          true
        } catch (e: Exception) {
          log.error("pin is not valid: ", e)
          false
        }
      }
    }.await()
  }

  /**
   * Reads the seed from file and update state
   */
  @UiThread
  fun readSeed(context: Context?, pin: String) {
    if (context != null) {
      viewModelScope.launch {
        withContext(Dispatchers.Default) {
          try {
            seed.postValue(EncryptedSeed.readSeedFile(context, pin))
            protectionUpdateState.postValue(ProtectionUpdateState.SET_NEW_PIN)
          } catch (e: Exception) {
            log.error("could not read seed: ", e)
            if (pin == Wallet.DEFAULT_PIN) {
              // preference pin status is probably obsolete
              Prefs.setIsSeedEncrypted(context)
            }
            protectionUpdateState.postValue(ProtectionUpdateState.ERROR)
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
          if (protectionUpdateState.value != ProtectionUpdateState.SET_NEW_PIN) {
            throw java.lang.RuntimeException("cannot encrypt seed in state ${protectionUpdateState.value}")
          }
          protectionUpdateState.postValue(ProtectionUpdateState.ENCRYPTING)
          EncryptedSeed.writeSeedToFile(context!!, seed.value ?: throw RuntimeException("empty seed"), pin)
          protectionUpdateState.postValue(ProtectionUpdateState.IDLE)
        } catch (e: java.lang.Exception) {
          log.error("could not encrypt seed: ", e)
          protectionUpdateState.postValue(ProtectionUpdateState.ERROR)
          errorMessage.postValue(R.string.seedsec_error_generic)
        }
      }
    }
  }
}
