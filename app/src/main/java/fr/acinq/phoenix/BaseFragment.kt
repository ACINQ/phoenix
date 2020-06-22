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

package fr.acinq.phoenix

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.security.PinDialog
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class BaseFragment : Fragment() {

  open val log: Logger = LoggerFactory.getLogger(BaseFragment::class.java)
  protected lateinit var app: AppViewModel

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.let {
      app = ViewModelProvider(it).get(AppViewModel::class.java)
    } ?: log.error("app model could not be set because of missing activity")
    app.state.observe(viewLifecycleOwner, Observer {
      handleKitState(it)
    })
  }

  /**
   * Checks up the app state (wallet init, app kit is started) and navigate to appropriate page if needed.
   */
  open fun handleKitState(state: KitState) {
    if (state !is KitState.Started) {
      if (context != null && !app.hasWalletBeenSetup(requireContext())) {
        log.info("wallet has not been initialized, moving to init")
        findNavController().navigate(R.id.global_action_any_to_init_wallet)
      } else {
        log.info("kit is not ready, moving to startup")
        findNavController().navigate(R.id.global_action_any_to_startup)
      }
    }
  }

  fun getBiometricAuth(titleResId: Int = R.string.biometricprompt_title, negativeResId: Int = R.string.biometricprompt_negative, descResId: Int? = null, negativeCallback: () -> Unit, successCallback: () -> Unit): BiometricPrompt {
    val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(getString(titleResId))
      .setDeviceCredentialAllowed(false)
      .setNegativeButtonText(getString(negativeResId))

    descResId?.let { biometricPromptInfo.setDescription(getString(descResId)) }

    val biometricPrompt = BiometricPrompt(this, { runnable -> Handler(Looper.getMainLooper()).post(runnable) }, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        log.info("biometric auth error ($errorCode): $errString")
        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
          negativeCallback()
        } else if (errorCode == BiometricConstants.ERROR_USER_CANCELED) {
          negativeCallback()
        }
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        try {
          successCallback()
        } catch (e: Exception) {
          log.error("could not handle successful biometric auth callback: ", e)
        }
      }
    })

    biometricPrompt.authenticate(biometricPromptInfo.build())
    return biometricPrompt
  }

  fun getPinDialog(callback: PinDialog.PinDialogCallback, cancelable: Boolean = true): PinDialog? {
    return activity?.let {
      val pinDialog = PinDialog((it as MainActivity).getActivityThis(), R.style.dialog_fullScreen, pinCallback = callback, cancelable = cancelable)
      pinDialog.setCanceledOnTouchOutside(false)
      pinDialog
    }
  }

  fun getPinDialog(titleResId: Int, callback: PinDialog.PinDialogCallback, cancelable: Boolean = true): PinDialog? {
    return activity?.let {
      val pinDialog = PinDialog((it as MainActivity).getActivityThis(), R.style.dialog_fullScreen, callback, titleResId, cancelable)
      pinDialog.setCanceledOnTouchOutside(false)
      pinDialog
    }
  }

}
