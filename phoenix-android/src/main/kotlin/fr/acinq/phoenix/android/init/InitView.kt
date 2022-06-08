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

package fr.acinq.phoenix.android.init

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.BorderButton
import fr.acinq.phoenix.android.components.FilledButton
import fr.acinq.phoenix.android.components.mvi.MVIControllerViewModel
import fr.acinq.phoenix.android.payments.ReceiveViewState
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.InitializationController
import fr.acinq.phoenix.controllers.init.Initialization
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun InitWallet(
    onCreateWalletClick: () -> Unit,
    onRestoreWalletClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledButton(
            text = R.string.initwallet_create,
            icon = R.drawable.ic_fire,
            onClick = onCreateWalletClick
        )
        Spacer(modifier = Modifier.height(16.dp))
        BorderButton(
            text = R.string.initwallet_restore,
            icon = R.drawable.ic_restore,
            onClick = onRestoreWalletClick
        )
    }
}


sealed class WritingSeedState {
    object Init : WritingSeedState()
    data class Writing(val mnemonics: List<String>) : WritingSeedState()
    data class WrittenToDisk(val encryptedSeed: EncryptedSeed) : WritingSeedState()
    data class Error(val e: Throwable) : WritingSeedState()
}

internal class InitViewModel(controller: InitializationController) : MVIControllerViewModel<Initialization.Model, Initialization.Intent>(controller) {

    /** State of the view */
    var writingState by mutableStateOf<WritingSeedState>(WritingSeedState.Init)
        private set

    /** State of the view */
    var restoreWalletState by mutableStateOf<RestoreWalletViewState>(RestoreWalletViewState.Disclaimer)

    private val _selectedWords = mutableStateListOf<String>()
    val selectedWords: List<String> = _selectedWords

    fun addWord(word:String) {
        _selectedWords.add(word)
    }

    fun removeRangeWords(from:Int, to:Int) {
        _selectedWords.removeRange(from, to)
    }

    suspend fun writeSeed(
        context: Context,
        mnemonics: List<String>,
        isNewWallet: Boolean,
        onSeedWritten: () -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        if (writingState == WritingSeedState.Init) {
            log.info("a new wallet has been generated, writing mnemonics to disk...")
            try {
                writingState = WritingSeedState.Writing(mnemonics)
                val existing = SeedManager.loadSeedFromDisk(context)
                if (existing == null) {
                    val encrypted = EncryptedSeed.V2.NoAuth.encrypt(EncryptedSeed.fromMnemonics(mnemonics))
                    SeedManager.writeSeedToDisk(context, encrypted)
                    writingState = WritingSeedState.WrittenToDisk(encrypted)
                    PrefsDatastore.saveStartLegacyApp(context, if (isNewWallet) LegacyAppStatus.NotRequired else LegacyAppStatus.Unknown)
                    log.info("mnemonics has been written to disk")
                } else {
                    log.warn("cannot overwrite existing seed=${existing.name()}")
                    writingState = WritingSeedState.WrittenToDisk(existing)
                }
                viewModelScope.launch(Dispatchers.Main) {
                    delay(1000)
                    onSeedWritten()
                }
            } catch (e: Exception) {
                log.error("failed to write mnemonics to disk: ", e)
                writingState = WritingSeedState.Error(e)
            }
        }
    }

    class Factory(
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> InitializationController
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return InitViewModel(controllerFactory.getController()) as T
        }
    }
}