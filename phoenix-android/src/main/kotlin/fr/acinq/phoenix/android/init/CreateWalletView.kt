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

package fr.acinq.phoenix.android.init

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.Lightning
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.controllerFactory
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.init.Initialization


@Composable
fun CreateWalletView(
    onSeedWritten: () -> Unit
) {
    val log = logger("CreateWallet")
    val context = LocalContext.current

    val vm: InitViewModel = viewModel(factory = InitViewModel.Factory(controllerFactory, CF::initialization))

    val keyState = produceState<KeyState>(initialValue = KeyState.Unknown, true) {
        value = SeedManager.getSeedState(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (keyState.value) {
            is KeyState.Absent -> {
                Text(stringResource(id = R.string.autocreate_generating))
                MVIView(CF::initialization) { model, postIntent ->
                    when (model) {
                        is Initialization.Model.Ready -> {
                            val entropy = remember { Lightning.randomBytes(16) }
                            LaunchedEffect(key1 = entropy) {
                                log.info { "generating wallet..." }
                                postIntent(Initialization.Intent.GenerateWallet(entropy))
                            }
                        }
                        is Initialization.Model.GeneratedWallet -> {
                            val writingState = vm.writingState
                            if (writingState is WritingSeedState.Error) {
                                Text(stringResource(id = R.string.autocreate_error, writingState.e.localizedMessage ?: writingState.e::class.java.simpleName))
                            }
                            LaunchedEffect(true) {
                                vm.writeSeed(context, model.mnemonics, true, onSeedWritten)
                            }
                        }
                    }
                }
            }
            KeyState.Unknown -> {
                Text(stringResource(id = R.string.startup_wait))
            }
            else -> {
                // we should not be here
                Text(stringResource(id = R.string.startup_wait))
                LaunchedEffect(true) {
                    onSeedWritten()
                }
            }
        }
    }
}