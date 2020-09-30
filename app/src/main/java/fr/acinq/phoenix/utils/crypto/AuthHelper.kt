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

import android.content.Context
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.crypto.Cipher


object AuthHelper {
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  fun canUseSoftAuth(context: Context?) = context?.let { BiometricManager.from(it).canAuthenticate(softAuthCreds) == BiometricManager.BIOMETRIC_SUCCESS } ?: false
  fun canUseHardAuth(context: Context?) = context?.let { BiometricManager.from(it).canAuthenticate(hardAuthCreds) == BiometricManager.BIOMETRIC_SUCCESS } ?: false
  fun authState(context: Context?) = context?.let { BiometricManager.from(it).canAuthenticate(hardAuthCreds) } ?: BiometricManager.BIOMETRIC_STATUS_UNKNOWN

  fun translateAuthState(context: Context, code: Int?): String? = when (code) {
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> context.getString(R.string.accessctrl_auth_none_enrolled)
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> context.getString(R.string.accessctrl_auth_hw_unavailable)
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> context.getString(R.string.accessctrl_auth_no_hw)
    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> context.getString(R.string.accessctrl_auth_update_required)
    BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> context.getString(R.string.accessctrl_auth_hw_unavailable)
    BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> context.getString(R.string.accessctrl_auth_unsupported)
    BiometricConstants.ERROR_LOCKOUT, BiometricConstants.ERROR_LOCKOUT_PERMANENT -> context.getString(R.string.accessctrl_auth_lockout)
    else -> null
  }

  val softAuthCreds = BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK
  val hardAuthCreds = BiometricManager.Authenticators.BIOMETRIC_STRONG

  /** Prompt authentication dialog that is not using a crypto object. */
  fun promptSoftAuth(
    fragment: BaseFragment,
    onSuccess: () -> Unit,
    onFailure: (errorCode: Int?) -> Unit,
    onCancel: (() -> Unit)? = null
  ) {
    if (canUseSoftAuth(fragment.context)) {
      getAuthPrompt(fragment, { onSuccess() }, onFailure, onCancel).authenticate(
        BiometricPrompt.PromptInfo.Builder().apply {
          setTitle(fragment.getString(R.string.authprompt_title))
          setAllowedAuthenticators(softAuthCreds)
        }.build())
    } else {
      authState(fragment.context).let {
        log.warn("cannot do soft authentication with state=$it")
        onFailure(it)
      }
    }
  }

  /** Prompt authentication dialog using a crypto object for stronger security in the success callback. */
  fun promptHardAuth(
    fragment: BaseFragment,
    cipher: Cipher,
    onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit,
    onFailure: (errorCode: Int?) -> Unit,
    onCancel: (() -> Unit)? = null
  ) {
    if (canUseHardAuth(fragment.context)) {
      getAuthPrompt(fragment, onSuccess, onFailure, onCancel).authenticate(
        BiometricPrompt.PromptInfo.Builder().apply {
          setTitle(fragment.getString(R.string.authprompt_title))
          setNegativeButtonText(fragment.getString(R.string.authprompt_hard_negative))
          setAllowedAuthenticators(hardAuthCreds)
        }.build(),
        BiometricPrompt.CryptoObject(cipher))
    } else {
      authState(fragment.context).let {
        log.warn("cannot do hard authentication with state=$it")
        onFailure(it)
      }
    }
  }

  private fun getAuthPrompt(
    fragment: Fragment,
    onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit,
    onFailure: (errorCode: Int?) -> Unit,
    onCancel: (() -> Unit)? = null
  ): BiometricPrompt {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        log.error("authentication error: ($errorCode): $errString")
        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricConstants.ERROR_USER_CANCELED) {
          onFailure(errorCode)
        } else {
          onCancel?.invoke()
        }
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        log.info("failed authentication")
        onFailure(null)
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
