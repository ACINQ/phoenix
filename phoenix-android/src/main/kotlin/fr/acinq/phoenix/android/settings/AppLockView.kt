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

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.SettingSwitch
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import kotlinx.coroutines.launch


@Composable
fun AppLockView() {
    val log = logger("AppLockView")
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val isScreenLockActive = UserPrefs.getIsScreenLockActive(context).collectAsState(initial = false)
    val nc = navController

    DefaultScreenLayout {
        DefaultScreenHeader(onBackClick = { nc.popBackStack() }, title = stringResource(id = R.string.accessctrl_title))
        Card {
            SettingSwitch(
                title = stringResource(id = R.string.accessctrl_screen_lock_switch),
                description = stringResource(id = R.string.accessctrl_screen_lock_switch_desc),
                icon = R.drawable.ic_lock,
                enabled = true,
                isChecked = isScreenLockActive.value,
                onCheckChangeAttempt = {
                    scope.launch {
                        if (it) {
                            // if user wants to enable screen lock, we don't need to check authentication
                            UserPrefs.saveIsScreenLockActive(context, true)
                        } else {
                            // if user wants to disable screen lock, we must first check his credentials
                            val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                                setTitle(context.getString(R.string.authprompt_title))
                                setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            }.build()
                            BiometricsHelper.getPrompt(
                                activity = activity,
                                onSuccess = {
                                    scope.launch { UserPrefs.saveIsScreenLockActive(context, false) }
                                },
                                onFailure = {
                                    // TODO display some message
                                },
                                onCancel = { log.debug { "cancelled auth prompt" } }
                            ).authenticate(promptInfo)
                        }
                    }
                }
            )
        }
    }

}