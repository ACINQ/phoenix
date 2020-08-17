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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.MainActivity
import fr.acinq.phoenix.R
import fr.acinq.phoenix.background.KitState
import fr.acinq.phoenix.databinding.FragmentStartupBinding
import fr.acinq.phoenix.security.PinDialog
import fr.acinq.phoenix.send.ReadInputFragmentDirections
import fr.acinq.phoenix.utils.BindingHelpers
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
  }

  override fun onStart() {
    super.onStart()
    mBinding.errorRestartButton.setOnClickListener {
      if (app.state.value is KitState.Error) {
        app.service?.shutdown()
      }
    }
    mBinding.errorSettingsButton.setOnClickListener {
      findNavController().navigate(R.id.global_action_any_to_settings)
    }
    // required because PIN/biometric dialogs are dismissed if user alt-tab (state observer will not fire anymore)
    app.state.value?.let { handleAppState(it) }
  }

  override fun onStop() {
    super.onStop()
    mPinDialog?.dismiss()
  }

  override fun handleAppState(state: KitState) {
    // check current nav in controller - there can be race conditions between handleKitState started from onStart and from the observer
    val currentNav = findNavController().currentDestination
    if (currentNav == null || currentNav.id != R.id.startup_fragment) return
    refreshUIVisibility(state)
    log.debug("nav=${currentNav.label} in state=${state.getName()} with intent=${app.currentURIIntent.value}")
    when {
      state is KitState.Started && app.currentURIIntent.value == null -> {
        log.info("kit [Started], redirect to main page")
        findNavController().navigate(R.id.action_startup_to_main)
      }
      state is KitState.Started && app.currentURIIntent.value != null -> {
        findNavController().navigate(ReadInputFragmentDirections.globalActionAnyToReadInput(app.currentURIIntent.value!!))
        app.currentURIIntent.value = null
      }
      state is KitState.Off -> {
        context?.let { ctx ->
          val encryptedSeed = SeedManager.getSeedFromDir(Wallet.getDatadir(ctx))
          if (encryptedSeed != null) {
            log.info("kit [OFF] with seed present, unlocking wallet and starting kit")
            unlockAndStart(ctx, encryptedSeed)
          } else {
            log.info("kit [OFF] with no seed found, redirect to wallet initialization")
            findNavController().navigate(R.id.global_action_any_to_init_wallet)
          }
        }
      }
      state is KitState.Error.Generic -> mBinding.errorMessage.text = getString(R.string.startup_error_generic, state.message)
      state is KitState.Error.InvalidElectrumAddress -> mBinding.errorMessage.text = getString(R.string.startup_error_electrum_address)
      state is KitState.Error.Tor -> mBinding.errorMessage.text = getString(R.string.startup_error_tor, state.message)
      state is KitState.Error.UnreadableData -> mBinding.errorMessage.text = getString(R.string.startup_error_unreadable)
      state is KitState.Error.NoConnectivity -> mBinding.errorMessage.text = getString(R.string.startup_error_network)
      state is KitState.Error.DeviceNotSecure -> mBinding.errorMessage.text = getString(R.string.startup_error_device_unsecure)
      state is KitState.Error.AuthenticationFailed -> {
        mBinding.errorMessage.text = getString(R.string.startup_error_auth_failed)
        Handler().postDelayed({ app.state.value = KitState.Off }, 2500)
      }
      state is KitState.Error.V1WrongPassword -> {
        mBinding.errorMessage.text = getString(R.string.startup_error_wrong_pwd)
        Handler().postDelayed({ app.state.value = KitState.Off }, 2500)
      }
      state is KitState.Error.V1InvalidBiometric -> {
        mBinding.errorMessage.text = getString(R.string.startup_error_biometrics)
        Handler().postDelayed({ app.state.value = KitState.Off }, 2500)
      }
      else -> {
        log.debug("kit [${state.getName()}] with context=$context, standing by...")
      }
    }
  }

  private fun refreshUIVisibility(state: KitState) {
    // show an icon when the app is not started
    BindingHelpers.show(mBinding.icon, state !is KitState.Started)
    // errors...
    BindingHelpers.show(mBinding.errorBox, state is KitState.Error)
    BindingHelpers.show(mBinding.errorSeparator, state !is KitState.Error.V1WrongPassword
      && state !is KitState.Error.V1InvalidBiometric
      && state !is KitState.Error.AuthenticationFailed)
    BindingHelpers.show(mBinding.errorRestartButton, state !is KitState.Error.V1WrongPassword
      && state !is KitState.Error.V1InvalidBiometric
      && state !is KitState.Error.AuthenticationFailed)
    BindingHelpers.show(mBinding.errorSettingsButton, state is KitState.Error.Generic || state is KitState.Error.UnreadableData
      || state is KitState.Error.Tor || state is KitState.Error.InvalidElectrumAddress)
    // wait while starting...
    BindingHelpers.show(mBinding.bindingService, state is KitState.Disconnected)
    BindingHelpers.show(mBinding.starting, state is KitState.Bootstrap.Init || state is KitState.Bootstrap.Node)
    BindingHelpers.show(mBinding.startingTor, state is KitState.Bootstrap.Tor)
    BindingHelpers.show(mBinding.startingTorAnimation, state is KitState.Bootstrap.Tor)
  }

  /** Decrypt seed and start the node. A authentication dialog may be displayed depending on the seed/prefs. */
  private fun unlockAndStart(context: Context, encryptedSeed: EncryptedSeed) {
    if (app.state.value !is KitState.Off) return
    // if the seed is version 1, it must be decrypted and migrated to [EncryptedSeed.V2]
    if (encryptedSeed is EncryptedSeed.V1) {
      handleV1Seed(context, encryptedSeed)
    } else if (encryptedSeed is EncryptedSeed.V2.NoAuth) {
      val onSuccess = {
        lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
          log.error("failed to decrypt ${encryptedSeed.javaClass.canonicalName}: ", e)
          app.state.postValue(KitState.Error.AuthenticationFailed)
        }) {
          val seed = EncryptedSeed.byteArray2ByteVector(encryptedSeed.decrypt())
          lifecycleScope.launch(Dispatchers.Main) { app.service?.startKit(seed) }
        }
      }
      if (Prefs.isScreenLocked(context) && AuthHelper.isDeviceSecure(context)) {
        AuthHelper.promptSoftAuth(this, { onSuccess() }, { _, _ -> app.state.value = KitState.Error.AuthenticationFailed })
      } else {
        onSuccess()
      }
    } else if (encryptedSeed is EncryptedSeed.V2.WithAuth) {
      AuthHelper.promptHardAuth(this,
        encryptedSeed.getDecryptionCipher(),
        onSuccess = { crypto ->
          lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to decrypt ${encryptedSeed.javaClass.canonicalName}: ", e)
            app.state.postValue(KitState.Error.AuthenticationFailed)
          }) {
            val seed = EncryptedSeed.byteArray2ByteVector(encryptedSeed.decrypt(crypto!!.cipher!!))
            lifecycleScope.launch(Dispatchers.Main) { app.service?.startKit(seed) }
          }
        },
        onFailure = { _, _ ->
          app.state.value = KitState.Error.AuthenticationFailed
        })
    }
  }

  /** Unlock a [EncryptedSeed.V1] seed, migrate it to a [EncryptedSeed.V2.NoAuth] seed and start the node. */
  private fun handleV1Seed(context: Context, encryptedSeed: EncryptedSeed.V1) {
    val migrationCallback: (pin: String) -> Unit = {
      lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        log.error("failed to handle v1 encrypted seed: ", e)
        app.state.postValue(KitState.Error.V1WrongPassword)
      }) {
        val seed = EncryptedSeed.migration_v1_v2(context, encryptedSeed, it).run {
          SeedManager.writeSeedToDisk(Wallet.getDatadir(context), this)
          EncryptedSeed.byteArray2ByteVector(this.decrypt())
        }
        log.info("completed v1->v2 encrypted seed migration")
        lifecycleScope.launch(Dispatchers.Main) { app.service?.startKit(seed) }
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
            app.state.value = KitState.Error.V1InvalidBiometric
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
