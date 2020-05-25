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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.KitState
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentStartupBinding
import fr.acinq.phoenix.security.PinDialog
import fr.acinq.phoenix.send.ReadInputFragmentDirections
import fr.acinq.phoenix.utils.KeystoreHelper
import fr.acinq.phoenix.utils.Prefs
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
    mBinding.appModel = app
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
    if (mPinDialog == null) {
      mPinDialog = getPinDialog(object : PinDialog.PinDialogCallback {
        override fun onPinConfirm(dialog: PinDialog, pinCode: String) {
          if (context != null) {
            app.startKit(context!!, pinCode)
          }
          dialog.dismiss()
        }

        override fun onPinCancel(dialog: PinDialog) {}
      }, cancelable = false)
    }
    mBinding.errorRestartButton.setOnClickListener {
      if (app.state.value is KitState.Error) app.state.value = KitState.Off
    }
    mBinding.errorSettingsButton.setOnClickListener {
      findNavController().navigate(R.id.global_action_any_to_settings)
    }
    // required because PIN/biometric dialogs are dismissed if user alt-tab (state observer will not fire anymore)
    app.state.value?.let { handleKitState(it) }
  }

  override fun onStop() {
    super.onStop()
    mPinDialog?.dismiss()
  }

  override fun handleKitState(state: KitState) {
    // check current nav in controller - there can be race conditions between handleKitState started from onStart and from the observer
    val currentNav = findNavController().currentDestination
    if (currentNav != null && currentNav.id == R.id.startup_fragment) {
      log.debug("nav=${currentNav.label} in state=${state.javaClass.canonicalName} with intent=${app.currentURIIntent.value}")
      when {
        state is KitState.Started && app.currentURIIntent.value == null -> {
          log.info("kit [Started], redirect to main page")
          findNavController().navigate(R.id.action_startup_to_main)
        }
        state is KitState.Started && app.currentURIIntent.value != null -> {
          findNavController().navigate(ReadInputFragmentDirections.globalActionAnyToReadInput(app.currentURIIntent.value!!))
          app.currentURIIntent.value = null
        }
        state is KitState.Off && context != null -> {
          if (app.hasWalletBeenSetup(context!!)) {
            log.info("kit [OFF] with ready wallet, starting kit")
            unlockAndStart(context!!)
          } else {
            log.info("kit [OFF] with empty wallet, redirect to wallet initialization")
            findNavController().navigate(R.id.global_action_any_to_init_wallet)
          }
        }
        state is KitState.Error.Generic -> context?.run { mBinding.errorMessage.text = getString(R.string.startup_error_generic, state.message) }
        state is KitState.Error.InvalidElectrumAddress -> context?.run { mBinding.errorMessage.text = getString(R.string.startup_error_electrum_address) }
        state is KitState.Error.Tor -> context?.run { mBinding.errorMessage.text = getString(R.string.startup_error_tor, state.message) }
        state is KitState.Error.UnreadableData -> context?.run { mBinding.errorMessage.text = getString(R.string.startup_error_unreadable) }
        state is KitState.Error.NoConnectivity -> context?.run { mBinding.errorMessage.text = getString(R.string.startup_error_network) }
        state is KitState.Error.WrongPassword -> {
          context?.run { mBinding.errorMessage.text = getString(R.string.startup_error_wrong_pwd) }
          Handler().postDelayed({ app.state.value = KitState.Off }, 2500)
        }
        state is KitState.Error.InvalidBiometric -> {
          context?.run { mBinding.errorMessage.text = getString(R.string.startup_error_biometrics) }
          Handler().postDelayed({ app.state.value = KitState.Off }, 2500)
        }
        else -> {
          log.debug("kit [$state] with context=$context, standing by...")
        }
      }
    }
  }

  private fun unlockAndStart(context: Context) {
    if (app.state.value is KitState.Off) {
      when {
        // wallet is encrypted and we can use biometrics
        Prefs.useBiometrics(context) && BiometricManager.from(context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS ->
          getBiometricAuth(negativeCallback = {
            mPinDialog?.reset()
            mPinDialog?.show()
          }, successCallback = {
            try {
              val pin = KeystoreHelper.decryptPin(context)?.toString(Charsets.UTF_8)
              app.startKit(context, pin!!)
            } catch (e: Exception) {
              log.error("could not decrypt pin: ", e)
              app.state.value = KitState.Error.InvalidBiometric
            }
          })
        // wallet is encrypted and we don't use biometrics
        Prefs.isSeedEncrypted(context) -> {
          mPinDialog?.reset()
          mPinDialog?.show()
        }
        // wallet is not encrypted
        else -> app.startKit(context, null)
      }
    }
  }
}
