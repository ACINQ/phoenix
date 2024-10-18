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
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.screenlock.CheckPinFlow
import fr.acinq.phoenix.android.components.screenlock.NewPinFlow
import fr.acinq.phoenix.android.components.settings.ListPreferenceButton
import fr.acinq.phoenix.android.components.settings.PreferenceItem
import fr.acinq.phoenix.android.components.settings.Setting
import fr.acinq.phoenix.android.components.settings.SettingSwitch
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.extensions.findActivity
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


@Composable
fun AppAccessSettings(
    onBackClick: () -> Unit,
    appViewModel: AppViewModel,
) {
    val context = LocalContext.current
    val biometricAuthStatus = BiometricsHelper.authStatus(context)
    val userPrefs = userPrefs
    val isBiometricLockEnabled by userPrefs.getIsBiometricLockEnabled.collectAsState(null)
    val isCustomPinLockEnabled by userPrefs.getIsCustomPinLockEnabled.collectAsState(null)
    val autoLockDelay by userPrefs.getAutoLockDelay.collectAsState(null)

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = onBackClick, title = stringResource(id = R.string.accessctrl_title))

        Card {
            isBiometricLockEnabled?.let {
                if (biometricAuthStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                    BiometricScreenLockView(
                        isBiometricLockEnabled = it,
                        onBiometricLockChange = { userPrefs.saveIsBiometricLockEnabled(it) }
                    )
                } else {
                    CannotUseBiometrics(status = biometricAuthStatus)
                }
            } ?: ProgressView(text = stringResource(id = R.string.utils_loading_prefs))
        }

        Card {
            isCustomPinLockEnabled?.let { pinEnabled ->
                CustomPinLockView(
                    isCustomPinLockEnabled = pinEnabled,
                )
            } ?: ProgressView(text = stringResource(id = R.string.utils_loading_prefs))
        }

        if (isBiometricLockEnabled == true || isCustomPinLockEnabled == true) {
            autoLockDelay?.let {
                val scope = rememberCoroutineScope()
                Card {
                    AutoLockDelayPicker(it, onUpdateDelay = { newDelay ->
                        scope.launch {
                            userPrefs.saveAutoLockDelay(newDelay)
                            appViewModel.scheduleAutoLock()
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun CannotUseBiometrics(
    status: Int,
) {
    val context = LocalContext.current
    Setting(
        title = stringResource(id = R.string.accessctrl_auth_error_header),
        subtitle = { Text(text = BiometricsHelper.getAuthErrorMessage(context, code = status)) },
        leadingIcon = { PhoenixIcon(resourceId = R.drawable.ic_fingerprint, tint = MaterialTheme.colors.onSurface) },
        onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
    )
}

@Composable
private fun BiometricScreenLockView(
    isBiometricLockEnabled: Boolean,
    onBiometricLockChange: suspend (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf("") }

    SettingSwitch(
        title = stringResource(id = R.string.accessctrl_biometric_lock_switch_label),
        description = stringResource(id = R.string.accessctrl_biometric_lock_switch_desc),
        icon = R.drawable.ic_fingerprint,
        enabled = true,
        isChecked = isBiometricLockEnabled,
        onCheckChangeAttempt = {
            errorMessage = ""
            scope.launch {
                if (it) {
                    BiometricsHelper
                    // if user wants to enable screen lock, we don't need to check authentication
                    onBiometricLockChange(true)
                } else {
                    // if user wants to disable screen lock, we must first check his credentials
                    val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                        setTitle(context.getString(R.string.lockprompt_title))
                        setAllowedAuthenticators(BiometricsHelper.authCreds)
                    }.build()
                    BiometricsHelper.getPrompt(
                        activity = activity,
                        onSuccess = {
                            scope.launch { onBiometricLockChange(false) }
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


@Composable
private fun CustomPinLockView(
    isCustomPinLockEnabled: Boolean,
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val userPrefs = userPrefs

    var isInNewPinFlow by rememberSaveable { mutableStateOf(false) }
    var isInDisablingCustomPinFlow by rememberSaveable { mutableStateOf(false) }

    SettingSwitch(
        title = stringResource(id = R.string.accessctrl_pin_lock_switch_label),
        description = stringResource(id = R.string.accessctrl_pin_lock_switch_desc),
        icon = R.drawable.ic_pin,
        enabled = !isInDisablingCustomPinFlow && !isInNewPinFlow,
        isChecked = isCustomPinLockEnabled,
        onCheckChangeAttempt = { isChecked ->
            errorMessage = ""
            if (isChecked) {
                // user is enabling custom PIN
                isInNewPinFlow = true
            } else {
                isInDisablingCustomPinFlow = true
            }
        }
    )

    if (isInNewPinFlow) {
        NewPinFlow(
            onCancel = { isInNewPinFlow = false },
            onDone = {
                scope.launch {
                    userPrefs.saveIsCustomPinLockEnabled(true)
                    isInNewPinFlow = false
                }
                Toast.makeText(context, "Pin code saved!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (isInDisablingCustomPinFlow) {
        CheckPinFlow(
            onCancel = { isInDisablingCustomPinFlow = false },
            onPinValid = {
                scope.launch {
                    userPrefs.saveIsCustomPinLockEnabled(false)
                    isInDisablingCustomPinFlow = false
                }
                Toast.makeText(context, "Pin disabled", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (errorMessage.isNotBlank()) {
        Text(
            text = errorMessage,
            modifier = Modifier.padding(start = 46.dp, top = 0.dp, bottom = 16.dp, end = 16.dp),
            color = negativeColor,
        )
    }
}

@Composable
private fun AutoLockDelayPicker(
    currentDelay: Duration,
    onUpdateDelay: (Duration) -> Unit,
) {
    val preferences = listOf(
        PreferenceItem(item = 1.minutes, title = "1 minute"),
        PreferenceItem(item = 10.minutes, title = "10 minutes"),
        PreferenceItem(item = Duration.INFINITE, title = "Never"),
    )
    ListPreferenceButton(
        title = stringResource(id = R.string.accessctrl_autolock_title),
        subtitle = {
            if (currentDelay == Duration.INFINITE) {
                Text(text = stringResource(id = R.string.accessctrl_autolock_desc_never))
            } else {
                Text(text = stringResource(id = R.string.accessctrl_autolock_desc, currentDelay.inWholeMinutes))
            }
        },
        leadingIcon = { PhoenixIcon(resourceId = R.drawable.ic_lock)},
        selectedItem = currentDelay,
        preferences = preferences,
        onPreferenceSubmit = {
            if (it.item != currentDelay) onUpdateDelay(it.item)
        },
        enabled = true,
    )
}