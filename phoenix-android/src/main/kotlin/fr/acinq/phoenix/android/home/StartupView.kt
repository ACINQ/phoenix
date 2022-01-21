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
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.service.WalletState
import fr.acinq.phoenix.android.utils.BiometricsHelper
import fr.acinq.phoenix.android.utils.LegacyHelper
import fr.acinq.phoenix.android.utils.Prefs
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
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
    val isLockActive by Prefs.getIsScreenLockActive(context).collectAsState(initial = null)

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
    val skipLegacyCheck by PrefsDatastore.getSkipLegacyCheck(context).collectAsState(false)
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
                    when (val encryptedSeed = keyState.encryptedSeed) {
                        is EncryptedSeed.V2.NoAuth -> {
                            Text(stringResource(id = R.string.startup_checking_seed))
                            LaunchedEffect(encryptedSeed, skipLegacyCheck) {
                                if (!skipLegacyCheck && LegacyHelper.hasLegacyChannels(context)) {
                                    log.info { "found legacy channels database file" }
                                    context.startActivity(Intent(context, fr.acinq.phoenix.legacy.MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) })
                                } else {
                                    log.info { "no legacy database, proceed to modern engine launch" }
                                    scope.launch(Dispatchers.IO) {
                                        log.debug { "decrypting seed..." }
                                        val seed = encryptedSeed.decrypt()
                                        log.debug { "seed has been decrypted" }
                                        appVM.service?.startBusiness(seed)
                                    }
                                }
                            }
                        }
                        is EncryptedSeed.V2.WithAuth -> {
                            Text("seed=${keyState.encryptedSeed} version is not handled yet")
                        }
                    }
                }
            }
        }
        is WalletState.Disconnected -> Text(stringResource(id = R.string.startup_binding_service))
        is WalletState.Bootstrap -> Text(stringResource(id = R.string.startup_starting))
        is WalletState.Error.Generic -> Text(stringResource(id = R.string.startup_error_generic, walletState.message))
        is WalletState.Started -> LaunchedEffect(true) { onBusinessStarted() }
    }
}
