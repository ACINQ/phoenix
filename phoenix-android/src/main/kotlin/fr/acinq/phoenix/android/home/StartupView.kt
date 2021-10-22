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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.acinq.phoenix.android.*
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.utils.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun StartupView(appVM: AppViewModel) {
    val log = logger()
    val nc = navController
    val ks = keyState
    val context = LocalContext.current
    val _business = business

    when (ks) {
        is KeyState.Unknown -> Text(stringResource(id = R.string.startup_wait))
        is KeyState.Absent -> nc.navigate(Screen.InitWallet)
        is KeyState.Error -> Text(stringResource(id = R.string.startup_error_unreadable))
        is KeyState.Present -> {
            when (val encryptedSeed = ks.encryptedSeed) {
                is EncryptedSeed.V2.NoAuth -> {
                    Text(stringResource(id = R.string.startup_starting))
                    LaunchedEffect(key1 = encryptedSeed) {
                        launch(Dispatchers.Main) {
                            appVM.service?.startBusiness(encryptedSeed.decrypt())
                            log.info { "navigating to home screen..." }
                            nc.navigate(Screen.Home)
                        }
                    }
                }
                else -> {
                    Text("seed=${ks.encryptedSeed} version is not handled yet")
                }
            }
        }
    }
}
