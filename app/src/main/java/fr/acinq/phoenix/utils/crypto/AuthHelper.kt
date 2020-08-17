/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.utils.crypto

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import fr.acinq.phoenix.R
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.crypto.Cipher


object AuthHelper {
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  fun isDeviceSecure(context: Context?) = context?.run {
    getSystemService(KeyguardManager::class.java).isDeviceSecure && BiometricManager.from(this).canAuthenticate() != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
  } ?: false

  /** Prompt authentication dialog that is not using a crypto object. */
  fun promptSoftAuth(
    fragment: Fragment,
    onSuccess: () -> Unit,
    onFailure: (errorCode: Int?, errString: CharSequence?) -> Unit,
    onCancel: (() -> Unit)? = null
  ) {
    val canAuth = fragment.context?.let { BiometricManager.from(it).canAuthenticate() }
    if (isDeviceSecure(fragment.context) && canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
      getAuthPrompt(fragment, { onSuccess() }, onFailure, onCancel).also {
        it.authenticate(
          BiometricPrompt.PromptInfo.Builder().run {
            setTitle(fragment.getString(R.string.authprompt_title))
            setDeviceCredentialAllowed(true)
            build()
          })
      }
    } else {
      log.warn("cannot authenticate with state=$canAuth")
      onFailure(null, fragment.getString(R.string.accessctrl_error_unsecure, canAuth ?: -2))
    }
  }

  /** Prompt authentication dialog using a crypto object for stronger security in the success callback. */
  fun promptHardAuth(
    fragment: Fragment,
    cipher: Cipher,
    onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit,
    onFailure: (errorCode: Int?, errString: CharSequence?) -> Unit,
    onCancel: (() -> Unit)? = null
  ) {
    val canAuth = fragment.context?.let { BiometricManager.from(it).canAuthenticate() }
    if (isDeviceSecure(fragment.context) && canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
      getAuthPrompt(fragment, onSuccess, onFailure, onCancel).also {
        it.authenticate(
          BiometricPrompt.PromptInfo.Builder().run {
            setTitle(fragment.getString(R.string.authprompt_title))
            setNegativeButtonText(fragment.getString(R.string.authprompt_hard_negative))
            setDeviceCredentialAllowed(false)
            build()
          }, BiometricPrompt.CryptoObject(cipher))
      }
    } else {
      log.warn("cannot authenticate with state=$canAuth")
      onFailure(null, fragment.getString(R.string.accessctrl_error_unsecure, canAuth ?: -2))
    }
  }

  private fun getAuthPrompt(
    fragment: Fragment,
    onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit,
    onFailure: (errorCode: Int?, errString: CharSequence?) -> Unit,
    onCancel: (() -> Unit)? = null
  ): BiometricPrompt {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        log.error("authentication error: ($errorCode): $errString")
        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricConstants.ERROR_USER_CANCELED) {
          onFailure(errorCode, errString)
        } else {
          onCancel?.invoke()
        }
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        log.info("failed authentication")
        onFailure(null, null)
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        log.info("successful authentication")
        onSuccess(result.cryptoObject)
      }
    }
    return BiometricPrompt(fragment, ContextCompat.getMainExecutor(fragment.context), callback)
  }
}
