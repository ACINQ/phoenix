/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.android.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import fr.acinq.phoenix.android.R
import org.slf4j.LoggerFactory

object BiometricsHelper {
    private val log = LoggerFactory.getLogger(BiometricsHelper::class.java)

    val authCreds = BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** Return a Int code representing the authentication status. See [BiometricManager.BIOMETRIC_SUCCESS] & al. */
    fun authStatus(context: Context): Int = BiometricManager.from(context).canAuthenticate(authCreds)

    /** True if the user can authenticate using [authCreds]. */
    fun canAuthenticate(context: Context) = authStatus(context) == BiometricManager.BIOMETRIC_SUCCESS

    fun getAuthErrorMessage(context: Context, code: Int): String {
        return when (code) {
            // issues with sensor
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> context.getString(R.string.accessctrl_auth_error_too_old)
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> context.getString(R.string.accessctrl_auth_error_unsupported)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> context.getString(R.string.accessctrl_auth_error_hw_unavailable)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> context.getString(R.string.accessctrl_auth_error_none_enrolled)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> context.getString(R.string.accessctrl_auth_error_no_hw)
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> context.getString(R.string.accessctrl_auth_error_update_required)
            // prompt errors
            BiometricPrompt.ERROR_VENDOR -> context.getString(R.string.accessctrl_auth_error_vendor)
            BiometricPrompt.ERROR_TIMEOUT -> context.getString(R.string.accessctrl_auth_error_timeout)
            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> context.getString(R.string.accessctrl_auth_error_cancelled)
            BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> context.getString(R.string.accessctrl_auth_error_lockout)
            else -> context.getString(R.string.accessctrl_auth_error_unhandled, code)
        }
    }

    fun getPrompt(
        activity: FragmentActivity,
        onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit,
        onFailure: (errorCode: Int?) -> Unit,
        onCancel: (() -> Unit)? = null
    ): BiometricPrompt = BiometricPrompt(
        activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
                super.onAuthenticationError(errorCode, errorString)
                log.info("authentication error: ($errorCode) $errorString")
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    onFailure(errorCode)
                } else {
                    onCancel?.invoke()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                log.info("authentication failure")
                onFailure(null)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                log.info("authentication success")
                onSuccess(result.cryptoObject)
            }
        }
    )
}