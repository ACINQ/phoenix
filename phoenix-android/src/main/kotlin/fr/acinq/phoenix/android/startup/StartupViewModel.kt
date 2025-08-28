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
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.StartBusinessResult
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeystoreHelper
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.services.ChannelsWatcher
import fr.acinq.phoenix.android.services.ContactsPhotoCleaner
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.nodeIdHash
import fr.acinq.phoenix.utils.extensions.phoenixName
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory


sealed class StartupViewState {
    data object Init : StartupViewState()
    data class StartingBusiness(val nodeId: String) : StartupViewState()
    data class BusinessActive(val nodeId: String): StartupViewState()

    sealed class Error: StartupViewState() {
        abstract val nodeId: String
        data class Generic(override val nodeId: String, val cause: Throwable?): Error()
    }

    sealed class SeedRecovery : StartupViewState() {
        data object Init: SeedRecovery()
        data object CheckingSeed: SeedRecovery()
        sealed class Success: SeedRecovery() {
            data object MatchingData: Success()
        }
        sealed class Error: SeedRecovery() {
            data class Other(val cause: Throwable): Error()
            data object SeedDoesNotMatch: Error()
            data class KeyStoreFailure(val cause: Throwable): Error()
        }
    }
}

class StartupViewModel(
    val application: PhoenixApplication,
) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<StartupViewState>(StartupViewState.Init)

    fun startupNode(nodeId: String, words: List<String>, onStartupSuccess: (PhoenixBusiness) -> Unit) {
        if (state.value !is StartupViewState.Init) {
            return
        }

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when initialising startup-view: ", e)
            state.value = StartupViewState.Error.Generic(nodeId = nodeId, cause = e)
        }) {
            state.value = StartupViewState.StartingBusiness(nodeId)
            val startResult = withContext(Dispatchers.Default) {
                BusinessManager.startNewBusiness(words, isHeadless = false)
            }

            ChannelsWatcher.schedule(application.applicationContext)
            ContactsPhotoCleaner.schedule(application.applicationContext)

            when (startResult) {
                is StartBusinessResult.Success -> {
                    state.value = StartupViewState.BusinessActive(nodeId)
                    launch(Dispatchers.Main) {
                        onStartupSuccess(startResult.business)
                    }
                }
                is StartBusinessResult.Failure.Generic -> state.value = StartupViewState.Error.Generic(nodeId = nodeId, cause = startResult.cause)
                is StartBusinessResult.Failure.LoadWalletError -> state.value = StartupViewState.Error.Generic(nodeId = nodeId, cause = null)
            }
        }
    }

    /**
     * This method checks if the provided mnemonics matches a channel file in the local files.
     * If so, the key in the keystore is updated and the seed is written to disk.
     */
    fun recoverSeed(context: Context, words: List<String>) {
        if (state.value is StartupViewState.SeedRecovery.CheckingSeed) return
        state.value = StartupViewState.SeedRecovery.CheckingSeed

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when checking seed fallback against existing data: ", e)
            state.value = StartupViewState.SeedRecovery.Error.Other(e)
        }) {
            val seed = MnemonicCode.toSeed(mnemonics = words.joinToString(" "), passphrase = "").byteVector()
            val localKeyManager = LocalKeyManager(seed = seed, chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)
            val nodeId = localKeyManager.nodeKeys.nodeKey.publicKey.toHex()
            val nodeIdHash = localKeyManager.nodeIdHash()

            val channelsDbFile = context.getDatabasePath("channels-${NodeParamsManager.chain.phoenixName}-$nodeIdHash.sqlite")
            if (channelsDbFile.exists()) {
                state.value = StartupViewState.SeedRecovery.Success.MatchingData
                try {
                    KeystoreHelper.checkEncryptionCipherOrReset(KeystoreHelper.KEY_NO_AUTH)
                } catch (e: Exception) {
                    state.value = StartupViewState.SeedRecovery.Error.SeedDoesNotMatch
                    return@launch
                }
                val encrypted = EncryptedSeed.V2.MultipleSeed.encrypt(mapOf(nodeId to words))
                SeedManager.writeSeedToDisk(context, encrypted, overwrite = true)
                delay(1000)
                state.value = StartupViewState.Init
                TODO("start recovered seed")
            } else {
                state.value = StartupViewState.SeedRecovery.Error.SeedDoesNotMatch
            }
        }
    }

    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StartupViewModel(application) as T
        }
    }
}