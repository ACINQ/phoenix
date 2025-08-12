/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.components.auth.screenlock

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.LocalUserPrefs
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.buttons.Button
import fr.acinq.phoenix.android.components.ProgressView
import fr.acinq.phoenix.android.userPrefs
import fr.acinq.phoenix.android.utils.BiometricsHelper
import fr.acinq.phoenix.android.utils.extensions.findActivity
import fr.acinq.phoenix.android.utils.extensions.safeLet
import kotlinx.coroutines.launch

/**
 * Screen shown when authentication through biometrics or PIN is required, depending on the user's settings.
 */
@Composable
fun ScreenLockPrompt(
    promptScreenLockImmediately: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val userPrefs = LocalUserPrefs.current ?: return

    val isBiometricLockEnabledState by userPrefs.getIsScreenLockBiometricsEnabled.collectAsState(initial = null)
    val isCustomPinLockEnabledState by userPrefs.getIsScreenLockPinEnabled.collectAsState(initial = null)
    var showPinLockDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        safeLet(isBiometricLockEnabledState, isCustomPinLockEnabledState) { isBiometricLockEnabled, isCustomPinEnabled ->

            val promptBiometricLock = {
                val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                    setTitle(context.getString(R.string.lockprompt_title))
                    setAllowedAuthenticators(BiometricsHelper.authCreds)
                }.build()
                BiometricsHelper.getPrompt(
                    activity = context.findActivity(),
                    onSuccess = {
                        scope.launch { userPrefs.saveScreenLockPinCodeSuccess() }
                        onUnlock()
                    },
                    onFailure = { onLock() },
                    onCancel = { }
                ).authenticate(promptInfo)
            }

            if (promptScreenLockImmediately) {
                LaunchedEffect(key1 = true) {
                    if (isBiometricLockEnabled) {
                        promptBiometricLock()
                    } else if (isCustomPinEnabled) {
                        showPinLockDialog = true
                    } else {
                        onUnlock()
                    }
                }
            }

            if (showPinLockDialog) {
                CheckScreenLockPinFlow(
                    onCancel = { showPinLockDialog = false },
                    onPinValid = { onUnlock() }
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(id = R.drawable.ic_phoenix),
                contentDescription = "phoenix-icon",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(text = stringResource(id = R.string.lockprompt_title), textAlign = TextAlign.Center, modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.weight(1f))
            Column(modifier = Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isBiometricLockEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        text = stringResource(id = R.string.lockprompt_biometrics_button),
                        icon = R.drawable.ic_fingerprint,
                        onClick = promptBiometricLock,
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colors.surface,
                        shape = CircleShape,
                        padding = PaddingValues(16.dp),
                    )
                }
                if (isCustomPinEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        text = stringResource(id = R.string.lockprompt_pin_button),
                        icon = R.drawable.ic_pin,
                        onClick = { showPinLockDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colors.surface,
                        shape = CircleShape,
                        padding = PaddingValues(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        } ?: run {
            Spacer(modifier = Modifier.weight(1f))
            ProgressView(
                text = stringResource(id = R.string.utils_loading_prefs),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}