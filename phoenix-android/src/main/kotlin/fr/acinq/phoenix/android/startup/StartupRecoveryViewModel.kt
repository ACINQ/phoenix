/*
 * Copyright 2023 ACINQ SAS
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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeystoreHelper
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.nodeIdHash
import fr.acinq.phoenix.utils.extensions.phoenixName
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class StartupRecoveryState {
    data object Init: StartupRecoveryState()
    data object CheckingSeed: StartupRecoveryState()
    sealed class Success: StartupRecoveryState() {
        data object MatchingData: Success()
    }
    sealed class Error: StartupRecoveryState() {
        data class Other(val cause: Throwable): Error()
        data object SeedDoesNotMatch: Error()
        data class KeyStoreFailure(val cause: Throwable): Error()
    }
}

class StartupRecoveryViewModel(
    val application: PhoenixApplication,
) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<StartupRecoveryState>(StartupRecoveryState.Init)

    /**
     * This method checks if the provided mnemonics matches a channel file in the local files.
     * If so, the key in the keystore is updated and the seed is written to disk.
     */
    fun recoverSeed(words: List<String>, onRecoveryDone: () -> Unit) {
        if (state.value is StartupRecoveryState.CheckingSeed) return
        state.value = StartupRecoveryState.CheckingSeed

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when checking seed fallback against existing data: ", e)
            state.value = StartupRecoveryState.Error.Other(e)
        }) {
            val seed = MnemonicCode.toSeed(mnemonics = words.joinToString(" "), passphrase = "").byteVector()
            val localKeyManager = LocalKeyManager(seed = seed, chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)
            val nodeId = localKeyManager.nodeKeys.nodeKey.publicKey.toHex()
            val nodeIdHash = localKeyManager.nodeIdHash()

            val channelsDbFile = application.applicationContext.getDatabasePath("channels-${NodeParamsManager.chain.phoenixName}-$nodeIdHash.sqlite")
            if (channelsDbFile.exists()) {
                state.value = StartupRecoveryState.Success.MatchingData
                try {
                    KeystoreHelper.checkEncryptionCipherOrReset(KeystoreHelper.KEY_NO_AUTH)
                } catch (e: Exception) {
                    state.value = StartupRecoveryState.Error.SeedDoesNotMatch
                    return@launch
                }
                val encrypted = EncryptedSeed.V2.MultipleSeed.encrypt(mapOf(nodeId to words))
                SeedManager.writeSeedToDisk(application.applicationContext, encrypted, overwrite = true)
                delay(1000)
                viewModelScope.launch(Dispatchers.Main) {
                    onRecoveryDone()
                }
            } else {
                state.value = StartupRecoveryState.Error.SeedDoesNotMatch
            }
        }
    }

    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StartupRecoveryViewModel(application) as T
        }
    }
}