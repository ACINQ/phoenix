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

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import fr.acinq.phoenix.android.MainActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.LoggerFactory

object BiometricsHelper {
    private val log = LoggerFactory.getLogger(BiometricsHelper::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPrompt(
        activity: FragmentActivity,
        onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit,
        onFailure: (errorCode: Int?) -> Unit,
        onCancel: (() -> Unit)? = null
    ): BiometricPrompt {
        return BiometricPrompt(
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
            })
    }
}