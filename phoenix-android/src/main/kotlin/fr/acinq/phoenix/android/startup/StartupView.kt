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

package fr.acinq.phoenix.android.startup

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.LockState
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.service.WalletState
import fr.acinq.phoenix.android.utils.*
import fr.acinq.phoenix.android.utils.datastore.InternalData
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.LegacyPrefsDatastore
import fr.acinq.phoenix.legacy.utils.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun StartupView(
    appVM: AppViewModel,
    onShowIntro: () -> Unit,
    onKeyAbsent: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val context = LocalContext.current
    val walletState by appVM.walletState.observeAsState()
    val showIntro by InternalData.getShowIntro(context).collectAsState(initial = null)
    val isLockActiveState by UserPrefs.getIsScreenLockActive(context).collectAsState(initial = null)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.weight(.55f), verticalArrangement = Arrangement.Bottom) {
            Image(
                painter = painterResource(id = R.drawable.ic_phoenix),
                contentDescription = "phoenix-icon",
            )
        }
        Column(modifier = Modifier.weight(.45f), verticalArrangement = Arrangement.Top) {
            val isLockActive = isLockActiveState
            if (isLockActive == null || showIntro == null) {
                // wait for preferences to load
            } else if (showIntro!!) {
                LaunchedEffect(key1 = Unit) { onShowIntro() }
            } else {
                LoadOrUnlock(
                    isLockActive = isLockActive,
                    lockState = appVM.lockState,
                    walletState = walletState,
                    onStartBusiness = { seed, checkLegacyChannels -> appVM.service?.startBusiness(seed, checkLegacyChannels) },
                    onUnlockSuccess = { appVM.lockState = LockState.Unlocked },
                    onUnlockFailure = { appVM.lockState = LockState.Locked.WithError(it) },
                    onKeyAbsent = onKeyAbsent,
                    onBusinessStarted = onBusinessStarted,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.LoadOrUnlock(
    isLockActive: Boolean,
    lockState: LockState,
    walletState: WalletState?,
    onStartBusiness: (ByteArray, Boolean) -> Unit,
    onUnlockSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit,
    onUnlockFailure: (Int?) -> Unit,
    onKeyAbsent: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val log = logger("StartupView")
    val context = LocalContext.current
    val activity = context.findActivity()
    if (isLockActive && BiometricsHelper.canAuthenticate(context)) {
        when (lockState) {
            is LockState.Locked -> {
                val promptScreenLock = {
                    val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                        setTitle(context.getString(R.string.authprompt_title))
                        setAllowedAuthenticators(BiometricsHelper.authCreds)
                    }.build()
                    BiometricsHelper.getPrompt(
                        activity = activity,
                        onSuccess = onUnlockSuccess,
                        onFailure = onUnlockFailure,
                        onCancel = { log.debug { "cancelled auth prompt" } }
                    ).authenticate(promptInfo)
                }
                LaunchedEffect(key1 = true) {
                    promptScreenLock()
                }
                BorderButton(
                    text = stringResource(id = R.string.startup_manual_unlock_button),
                    icon = R.drawable.ic_shield,
                    onClick = promptScreenLock
                )
            }
            is LockState.Unlocked -> {
                LoadingView(
                    context = context,
                    walletState = walletState,
                    onStartBusiness = onStartBusiness,
                    onKeyAbsent = onKeyAbsent,
                    onBusinessStarted = onBusinessStarted
                )
            }
        }
    } else {
        LoadingView(
            context = context,
            walletState = walletState,
            onStartBusiness = onStartBusiness,
            onKeyAbsent = onKeyAbsent,
            onBusinessStarted = onBusinessStarted
        )
    }
}

@Composable
private fun LoadingView(
    context: Context,
    walletState: WalletState?,
    onStartBusiness: (ByteArray, Boolean) -> Unit,
    onKeyAbsent: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val log = logger("StartupView")
    val scope = rememberCoroutineScope()
    val legacyAppStatus = LegacyPrefsDatastore.getLegacyAppStatus(context).collectAsState(null).value
    when (walletState) {
        is WalletState.Off -> {
            val keyState = produceState<KeyState>(initialValue = KeyState.Unknown, true) {
                value = SeedManager.getSeedState(context)
            }.value

            when (keyState) {
                is KeyState.Unknown -> Text(stringResource(id = R.string.startup_wait))
                is KeyState.Absent -> LaunchedEffect(true) { onKeyAbsent() }
                is KeyState.Error.Unreadable -> Text(stringResource(id = R.string.startup_error_generic, keyState.message ?: ""))
                is KeyState.Error.UnhandledSeedType -> Text(stringResource(id = R.string.startup_error_generic, "Unhandled seed type"))
                is KeyState.Present -> {
                    log.debug { "wallet ready to start with legacyAppStatus=$legacyAppStatus" }
                    when (legacyAppStatus) {
                        LegacyAppStatus.Unknown -> {
                            if (Wallet.getEclairDBFile(context).exists()) {
                                Text(stringResource(id = R.string.startup_wait_legacy_check))
                                log.debug { "found legacy database file while in unknown legacy status; switching to legacy app" }
                                LaunchedEffect(true) {
                                    LegacyPrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.Required.Expected)
                                }
                            } else {
                                Text(stringResource(id = R.string.startup_checking_seed))
                                LaunchedEffect(keyState.encryptedSeed) {
                                    decryptSeedAndStartBusiness(scope, keyState.encryptedSeed, doStartBusiness = { onStartBusiness(it, true) })
                                }
                            }
                        }
                        LegacyAppStatus.NotRequired -> {
                            Text(stringResource(id = R.string.startup_checking_seed))
                            LaunchedEffect(keyState.encryptedSeed) {
                                decryptSeedAndStartBusiness(scope, keyState.encryptedSeed, doStartBusiness = { onStartBusiness(it, false) })
                            }
                        }
                        else -> Text(stringResource(id = R.string.startup_wait))
                    }
                }
            }
        }
        null, is WalletState.Disconnected -> Text(stringResource(id = R.string.startup_binding_service))
        is WalletState.Bootstrap -> Text(stringResource(id = R.string.startup_starting))
        is WalletState.Error.Generic -> Text(stringResource(id = R.string.startup_error_generic, walletState.message))
        is WalletState.Started -> {
            when (legacyAppStatus) {
                LegacyAppStatus.Unknown -> {
                    Text(stringResource(id = R.string.startup_wait_legacy_check))
                }
                LegacyAppStatus.NotRequired -> {
                    LaunchedEffect(true) { onBusinessStarted() }
                }
                else -> {
                    Text(stringResource(id = R.string.startup_starting))
                }
            }
        }
    }
}

private fun decryptSeedAndStartBusiness(
    scope: CoroutineScope,
    encryptedSeed: EncryptedSeed.V2,
    doStartBusiness: (ByteArray) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        when (encryptedSeed) {
            is EncryptedSeed.V2.NoAuth -> {
                val seed = encryptedSeed.decrypt()
                doStartBusiness(seed)
            }
            is EncryptedSeed.V2.WithAuth -> {
                TODO("unsupported auth=$encryptedSeed")
            }
        }
    }
}
