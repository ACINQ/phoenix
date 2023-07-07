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

package fr.acinq.phoenix.android.settings

import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import kotlinx.coroutines.launch


@Composable
fun AppLockView(
    onBackClick: () -> Unit,
) {
    val log = logger("AppLockView")
    val context = LocalContext.current
    val authStatus = BiometricsHelper.authStatus(context)
    val isScreenLockActive by UserPrefs.getIsScreenLockActive(context).collectAsState(null)

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.accessctrl_title))
        Card {
            when (authStatus) {
                BiometricManager.BIOMETRIC_SUCCESS -> AuthSwitch(isScreenLockActive = isScreenLockActive)
                else -> CanNotAuthenticate(status = authStatus)
            }
        }
    }
}

@Composable
private fun CanNotAuthenticate(
    status: Int,
) {
    val context = LocalContext.current
    SettingInteractive(
        title = stringResource(id = R.string.accessctrl_auth_error_header),
        icon = R.drawable.ic_alert_triangle,
        iconTint = negativeColor,
        description = { Text(text = BiometricsHelper.getAuthErrorMessage(context, code = status)) },
        onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
    )
}

@Composable
private fun AuthSwitch(
    isScreenLockActive: Boolean?,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf("") }

    if (isScreenLockActive == null) {
        Text(text = stringResource(id = R.string.accessctrl_loading))
    } else {
        SettingSwitch(
            title = stringResource(id = R.string.accessctrl_screen_lock_switch),
            description = stringResource(id = R.string.accessctrl_screen_lock_switch_desc),
            icon = R.drawable.ic_lock,
            enabled = true,
            isChecked = isScreenLockActive,
            onCheckChangeAttempt = {
                errorMessage = ""
                scope.launch {
                    if (it) {
                        BiometricsHelper
                        // if user wants to enable screen lock, we don't need to check authentication
                        UserPrefs.saveIsScreenLockActive(context, true)
                    } else {
                        // if user wants to disable screen lock, we must first check his credentials
                        val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                            setTitle(context.getString(R.string.authprompt_title))
                            setAllowedAuthenticators(BiometricsHelper.authCreds)
                        }.build()
                        BiometricsHelper.getPrompt(
                            activity = activity,
                            onSuccess = {
                                scope.launch { UserPrefs.saveIsScreenLockActive(context, false) }
                            },
                            onFailure = { errorCode ->
                                errorMessage = errorCode?.let { BiometricsHelper.getAuthErrorMessage(context, code = it) } ?: ""
                            },
                            onCancel = { }
                        ).authenticate(promptInfo)
                    }
                }
            }
        )
        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                modifier = Modifier.padding(start = 46.dp, top = 0.dp, bottom = 16.dp, end = 16.dp),
                color = negativeColor,
            )
        }
    }

}