/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.initwallet.create

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.lightning.Lightning
import fr.acinq.phoenix.android.CF
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.feedback.ErrorMessage
import fr.acinq.phoenix.android.components.mvi.MVIView
import fr.acinq.phoenix.android.initwallet.InitViewModel
import fr.acinq.phoenix.android.initwallet.WritingSeedState
import fr.acinq.phoenix.android.security.SeedFileState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.controllers.init.Initialization
import fr.acinq.phoenix.utils.MnemonicLanguage


@Composable
fun CreateWalletView(
    onSeedWritten: () -> Unit
) {
    val log = logger("CreateWallet")
    val context = LocalContext.current

    val vm = viewModel<CreateWalletViewModel>(factory = CreateWalletViewModel.Factory())

    val seedFileState = produceState<SeedFileState>(initialValue = SeedFileState.Unknown, true) {
        value = SeedManager.getSeedState(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = seedFileState.value) {
            is SeedFileState.Absent -> {
                Text(stringResource(id = R.string.autocreate_generating))
                MVIView(CF::initialization) { model, postIntent ->
                    when (model) {
                        is Initialization.Model.Ready -> {
                            val entropy = remember { Lightning.randomBytes(16) }
                            LaunchedEffect(key1 = entropy) {
                                log.debug("generating new wallet...")
                                postIntent(Initialization.Intent.GenerateWallet(entropy, MnemonicLanguage.English))
                            }
                        }
                        is Initialization.Model.GeneratedWallet -> {
                            val writingState = vm.writingState
                            if (writingState is WritingSeedState.Error) {
                                ErrorMessage(
                                    header = stringResource(id = R.string.autocreate_error),
                                    details = when (writingState) {
                                        is WritingSeedState.Error.Generic -> writingState.cause.localizedMessage ?: writingState.cause::class.java.simpleName
                                        is WritingSeedState.Error.SeedAlreadyExist -> stringResource(R.string.autocreate_error_cannot_overwrite)
                                    },
                                    alignment = Alignment.CenterHorizontally,
                                )
                            }
                            LaunchedEffect(true) {
                                vm.writeSeed(context, model.mnemonics, isNewWallet = true, onSeedWritten)
                            }
                        }
                    }
                }
            }
            SeedFileState.Unknown -> {
                Text(stringResource(id = R.string.startup_wait))
            }
            else -> {
                // we should not be here
                LaunchedEffect(true) {
                    onSeedWritten()
                }
            }
        }
    }
}

class CreateWalletViewModel(override val internalDataRepository: InternalDataRepository) : InitViewModel() {

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
            @Suppress("UNCHECKED_CAST")
            return CreateWalletViewModel(application.internalDataRepository) as T
        }
    }
}
