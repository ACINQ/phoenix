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

package fr.acinq.phoenix.startup

import android.content.Context
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.AppLock
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.MainActivity
import fr.acinq.phoenix.R
import fr.acinq.phoenix.background.KitState
import fr.acinq.phoenix.databinding.FragmentStartupBinding
import fr.acinq.phoenix.security.PinDialog
import fr.acinq.phoenix.send.ReadInputFragmentDirections
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.Wallet
import fr.acinq.phoenix.utils.crypto.AuthHelper
import fr.acinq.phoenix.utils.crypto.EncryptedSeed
import fr.acinq.phoenix.utils.crypto.KeystoreHelper
import fr.acinq.phoenix.utils.crypto.SeedManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * This fragment is the navigation entry point. It checks the state of the app and redirects to
 * the main fragment when the kit is started. If not, it starts the kit using EclairNodeService.
 */
class StartupFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentStartupBinding
  private var mPinDialog: PinDialog? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentStartupBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    context?.let { ctx ->
      val torAnim = ctx.getDrawable(R.drawable.ic_tor_shield_animated)
      mBinding.startingTorAnimation.setImageDrawable(torAnim)
      if (torAnim is Animatable2) {
        torAnim.registerAnimationCallback(object : Animatable2.AnimationCallback() {
          override fun onAnimationEnd(drawable: Drawable) {
            torAnim.start()
          }
        })
        torAnim.start()
      }
    }
    mBinding.appModel = app
    app.lockState.observe(viewLifecycleOwner, { lockState ->
      when (lockState) {
        is AppLock.Locked.AuthFailure -> {
          mBinding.authenticationMessage.text = lockState.code?.let {
            getString(R.string.startup_error_auth_failed_with_details, AuthHelper.translateAuthState(requireContext(), it))
          } ?: getString(R.string.startup_error_auth_failed)
          Handler().postDelayed({
            val lock = app.lockState.value
            val blockingCodes = listOf(BiometricConstants.ERROR_LOCKOUT, BiometricConstants.ERROR_LOCKOUT_PERMANENT, BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
            if (lock is AppLock.Locked.AuthFailure && !blockingCodes.contains(lock.code)) {
              app.lockState.value = AppLock.Locked.Default
            }
          }, 3000)
        }
        else -> mBinding.authenticationMessage.text = getString(R.string.startup_unlock_required)
      }
    })
  }

  override fun onStart() {
    super.onStart()
    mBinding.errorRestartButton.setOnClickListener {
      if (app.state.value is KitState.Error) {
        app.service?.shutdown()
        context?.let { checkStateAndLock(it) }
      }
    }
    mBinding.errorSettingsButton.setOnClickListener {
      findNavController().navigate(R.id.global_action_any_to_settings)
    }
    mBinding.unlockButton.setOnClickListener {
      context?.let { checkStateAndLock(it) }
    }
  }

  override fun onStop() {
    super.onStop()
    mPinDialog?.dismiss()
  }

  override fun handleAppState(state: KitState) {
    context?.let { checkStateAndLock(it) }
  }

  private fun checkStateAndLock(context: Context) {
    val kitState = app.state.value
    val lockState = app.lockState.value
    val seed = SeedManager.getSeedFromDir(Wallet.getDatadir(context))

    when {
      seed == null && kitState is KitState.Off -> {
        log.info("kit=${kitState.getName()} with no seed found, redirect to wallet initialization")
        findNavController().navigate(R.id.global_action_any_to_init_wallet)
      }
      seed == null -> {
        log.info("kit=${kitState?.getName()} with no seed found, standing by...")
      }
      kitState is KitState.Started -> {
        log.info("kit=${kitState.getName()}, check that the app is unlocked...")
        checkLock(context)
      }
      kitState is KitState.Off -> {
        log.info("kit=${kitState.getName()} seed=${seed.name()} lock=${lockState}, unlocking wallet and starting kit")
        unlockAndStart(context, seed)
      }
      kitState is KitState.Error.Generic -> mBinding.errorMessage.text = getString(R.string.startup_error_generic, kitState.message)
      kitState is KitState.Error.InvalidElectrumAddress -> mBinding.errorMessage.text = getString(R.string.startup_error_electrum_address)
      kitState is KitState.Error.Tor -> mBinding.errorMessage.text = getString(R.string.startup_error_tor, kitState.message)
      kitState is KitState.Error.UnreadableData -> mBinding.errorMessage.text = getString(R.string.startup_error_unreadable)
      kitState is KitState.Error.NoConnectivity -> mBinding.errorMessage.text = getString(R.string.startup_error_network)
      else -> {
        log.debug("kit=${kitState?.getName()}, standing by...")
      }
    }
  }

  private fun checkLock(context: Context) {
    val lockState = app.lockState.value
    if (app.state.value !is KitState.Started) {
      log.debug("ignore checkLock with state=${app.state.value?.getName()}")
    }
    if (Prefs.isScreenLocked(context) && AuthHelper.canUseSoftAuth(context) && lockState is AppLock.Locked) {
      log.debug("app is locked")
      AuthHelper.promptSoftAuth(this,
        onSuccess = { redirectToNext() },
        onFailure = { code -> app.lockState.value = AppLock.Locked.AuthFailure(code) })
    } else {
      log.info("app is unlocked, redirect to next screen")
      redirectToNext()
    }
  }

  private fun redirectToNext() {
    val uriIntent = app.currentURIIntent.value
    if (uriIntent == null) {
      log.info("redirecting to main screen")
      findNavController().navigate(R.id.action_startup_to_main)
    } else {
      log.info("redirecting to read input screen with intent=$uriIntent")
      findNavController().navigate(ReadInputFragmentDirections.globalActionAnyToReadInput(uriIntent))
      app.currentURIIntent.value = null
    }
  }

  /** Decrypt seed and start the node. A authentication dialog may be displayed depending on the seed/prefs. */
  private fun unlockAndStart(context: Context, encryptedSeed: EncryptedSeed) {
    if (app.state.value !is KitState.Off) {
      log.debug("ignore start request in state=${app.state.value}")
      return
    }

    // if the seed is version 1, it must be decrypted and migrated to [EncryptedSeed.V2]
    if (encryptedSeed is EncryptedSeed.V1) {
      handleV1Seed(context, encryptedSeed)
    } else if (encryptedSeed is EncryptedSeed.V2.NoAuth) {
      val onSuccess = {
        lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
          log.error("failed to decrypt ${encryptedSeed.name()}: ", e)
          app.lockState.postValue(AppLock.Locked.AuthFailure())
        }) {
          val seed = EncryptedSeed.byteArray2ByteVector(encryptedSeed.decrypt())
          lifecycleScope.launch(Dispatchers.Main) {
            app.lockState.value = AppLock.Unlocked
            app.service?.startKit(seed)
          }
        }
      }
      if (Prefs.isScreenLocked(context) && AuthHelper.canUseSoftAuth(context)) {
        AuthHelper.promptSoftAuth(this,
          onSuccess = { onSuccess() },
          onFailure = { code -> app.lockState.value = AppLock.Locked.AuthFailure(code) })
      } else {
        onSuccess()
      }
    } else if (encryptedSeed is EncryptedSeed.V2.WithAuth) {
      val cipher = try {
        encryptedSeed.getDecryptionCipher()
      } catch (e: Exception) {
        when (e) {
          is KeyPermanentlyInvalidatedException -> {
            log.error("key has been permanently invalidated: ", e)
            app.lockState.value = AppLock.Locked.AuthFailure(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
          }
          else -> {
            log.error("could not get cipher for ${encryptedSeed.name()}")
            app.lockState.value = AppLock.Locked.AuthFailure()
          }
        }
        return
      }
      AuthHelper.promptHardAuth(this,
        cipher = cipher,
        onSuccess = { crypto ->
          lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to decrypt ${encryptedSeed.name()}: ", e)
            app.lockState.postValue(AppLock.Locked.AuthFailure())
          }) {
            val seed = EncryptedSeed.byteArray2ByteVector(encryptedSeed.decrypt(crypto!!.cipher!!))
            lifecycleScope.launch(Dispatchers.Main) {
              app.lockState.value = AppLock.Unlocked
              app.service?.startKit(seed)
            }
          }
        },
        onFailure = { code ->
          app.lockState.value = AppLock.Locked.AuthFailure(code)
        })
    }
  }

  /** Unlock a [EncryptedSeed.V1] seed, migrate it to a [EncryptedSeed.V2.NoAuth] seed and start the node. */
  private fun handleV1Seed(context: Context, encryptedSeed: EncryptedSeed.V1) {
    val migrationCallback: (pin: String) -> Unit = {
      lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        log.error("failed to handle v1 encrypted seed: ", e)
        app.lockState.postValue(AppLock.Locked.AuthFailure())
      }) {
        val seed = EncryptedSeed.migration_v1_v2(context, encryptedSeed, it).run {
          SeedManager.writeSeedToDisk(Wallet.getDatadir(context), this)
          EncryptedSeed.byteArray2ByteVector(this.decrypt())
        }
        log.info("completed v1->v2 encrypted seed migration")
        lifecycleScope.launch(Dispatchers.Main) {
          app.lockState.value = AppLock.Unlocked
          app.service?.startKit(seed)
        }
      }
    }

    // -- the legacy pin dialog will call the migration callback once pin is confirmed
    mPinDialog = activity?.let {
      val pinDialog = PinDialog((it as MainActivity).getActivityThis(), R.style.dialog_fullScreen, pinCallback = object : PinDialog.PinDialogCallback {
        override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
          migrationCallback(pinCode)
          dialog.dismiss()
        }

        override fun onPinCancel(dialog: PinDialog) {}
      }, cancelable = false)
      pinDialog.setCanceledOnTouchOutside(false)
      pinDialog
    }

    when {
      Prefs.useBiometrics(context) && BiometricManager.from(context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS -> {
        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
          .setTitle(getString(R.string.biometricprompt_title))
          .setDeviceCredentialAllowed(false)
          .setNegativeButtonText(getString(R.string.biometricprompt_negative))

        val onNegative = {
          mPinDialog?.reset()
          mPinDialog?.show()
        }

        val onSuccess = {
          try {
            val pin = KeystoreHelper.decryptPin(context).toString(Charsets.UTF_8)
            migrationCallback(pin)
          } catch (e: Exception) {
            log.error("could not decrypt pin: ", e)
            app.lockState.postValue(AppLock.Locked.AuthFailure())
          }
        }

        val biometricPrompt = BiometricPrompt(this, { runnable -> Handler(Looper.getMainLooper()).post(runnable) }, object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            log.info("biometric auth error ($errorCode): $errString")
            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricConstants.ERROR_USER_CANCELED) {
              onNegative()
            }
          }

          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            try {
              onSuccess()
            } catch (e: Exception) {
              log.error("could not handle successful biometric auth callback: ", e)
            }
          }
        })

        biometricPrompt.authenticate(biometricPromptInfo.build())
      }
      Prefs.isSeedEncrypted(context) -> {
        mPinDialog?.reset()
        mPinDialog?.show()
      }
      else -> {
        migrationCallback(Constants.DEFAULT_PIN)
      }
    }
  }

}
