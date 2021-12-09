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

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.service.WalletState
import fr.acinq.phoenix.android.utils.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun StartupView(
    appVM: AppViewModel,
    onKeyAbsent: () -> Unit,
    onBusinessStarted: () -> Unit,
) {
    val log = logger("StartupView")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val walletState = appVM.walletState.observeAsState()

    when (val state = walletState.value) {
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
                            Text(stringResource(id = R.string.startup_starting))
                            LaunchedEffect(key1 = encryptedSeed) {
                                scope.launch(Dispatchers.IO) {
                                    log.debug { "decrypting seed..." }
                                    val seed = encryptedSeed.decrypt()
                                    log.debug { "seed has been decrypted" }
                                    appVM.service?.startBusiness(seed)
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
        is WalletState.Error.Generic -> Text(stringResource(id = R.string.startup_error_generic, state.message))
        is WalletState.Started -> LaunchedEffect(true) { onBusinessStarted() }
    }
}
