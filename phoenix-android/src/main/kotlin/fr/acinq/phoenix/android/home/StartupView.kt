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

package fr.acinq.phoenix.android.home

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.service.NodeService
import fr.acinq.phoenix.android.service.WalletState
import fr.acinq.phoenix.android.utils.BiometricsHelper
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import fr.acinq.phoenix.legacy.utils.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.kodein.log.Logger


@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun StartupView(
    mainActivity: MainActivity,
    appVM: AppViewModel,
    onKeyAbsent: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val log = logger("StartupView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val walletState = appVM.walletState.observeAsState()
    val isLockActive by UserPrefs.getIsScreenLockActive(context).collectAsState(initial = null)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (isLockActive) {
            null -> Text(stringResource(id = R.string.startup_check_lock))
            true -> when (appVM.lockState) {
                is LockState.Locked -> {
                    LaunchedEffect(key1 = true) {
                        val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
                            setTitle(context.getString(R.string.authprompt_title))
                            setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        }.build()
                        BiometricsHelper.getPrompt(
                            activity = mainActivity,
                            onSuccess = { appVM.lockState = LockState.Unlocked },
                            onFailure = { appVM.lockState = LockState.Locked.WithError(it) },
                            onCancel = { log.debug { "cancelled auth prompt" } }
                        ).authenticate(promptInfo)
                    }
                }
                is LockState.Unlocked -> {
                    AttemptStart(
                        context = context,
                        scope = scope,
                        log = log,
                        appVM = appVM,
                        walletState = walletState.value,
                        onKeyAbsent = onKeyAbsent,
                        onBusinessStarted = onBusinessStarted
                    )
                }
            }
            false -> {
                AttemptStart(
                    context = context,
                    scope = scope,
                    log = log,
                    appVM = appVM,
                    walletState = walletState.value,
                    onKeyAbsent = onKeyAbsent,
                    onBusinessStarted = onBusinessStarted
                )
            }
        }
    }
}

@Composable
private fun AttemptStart(
    context: Context,
    scope: CoroutineScope,
    log: Logger,
    appVM: AppViewModel,
    walletState: WalletState?,
    onKeyAbsent: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val legacyAppStatus = PrefsDatastore.getLegacyAppStatus(context).collectAsState(null).value
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
                    log.info { "wallet ready to start with legacyAppStatus=$legacyAppStatus" }
                    when (legacyAppStatus) {
                        LegacyAppStatus.Unknown -> {
                            Text(stringResource(id = R.string.startup_wait_legacy_check))
                            if (Wallet.getEclairDBFile(context).exists()) {
                                log.info { "found legacy database file while in unknown legacy status; switching to legacy app" }
                                LaunchedEffect(true) {
                                    PrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.Required.Expected)
                                }
                            } else {
                                StartKMP(scope = scope, service = appVM.service, encryptedSeed = keyState.encryptedSeed, checkLegacyChannel = true)
                            }
                        }
                        LegacyAppStatus.NotRequired -> {
                            StartKMP(scope = scope, service = appVM.service, encryptedSeed = keyState.encryptedSeed, checkLegacyChannel = false)
                        }
                        else -> Text(stringResource(id = R.string.startup_wait))
                    }
                }
            }
        }
        is WalletState.Disconnected -> Text(stringResource(id = R.string.startup_binding_service))
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

@Composable
private fun StartKMP(scope: CoroutineScope, service: NodeService?, encryptedSeed: EncryptedSeed.V2, checkLegacyChannel: Boolean) {
    val log = logger("StartupView")
    when (encryptedSeed) {
        is EncryptedSeed.V2.NoAuth -> {
            Text(stringResource(id = R.string.startup_checking_seed))
            LaunchedEffect(encryptedSeed) {
                scope.launch(Dispatchers.IO) {
                    log.debug { "decrypting seed..." }
                    val seed = encryptedSeed.decrypt()
                    log.debug { "seed has been decrypted" }
                    service?.startBusiness(seed, checkLegacyChannel)
                }
            }
        }
        is EncryptedSeed.V2.WithAuth -> {
            Text("seed=$encryptedSeed version is not handled yet")
        }
    }
}
