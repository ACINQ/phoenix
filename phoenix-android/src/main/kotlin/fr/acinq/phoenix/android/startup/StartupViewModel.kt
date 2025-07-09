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
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.Connection
import fr.acinq.phoenix.android.BusinessRepo
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.StartBusinessResult
import fr.acinq.phoenix.android.security.DecryptSeedResult
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeystoreHelper
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.services.ChannelsWatcher
import fr.acinq.phoenix.android.services.ContactsPhotoCleaner
import fr.acinq.phoenix.managers.AppConnectionsDaemon
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

    data object LoadingSeed: StartupViewState()
    data object SeedNotFound: StartupViewState()
    data object StartingBusiness : StartupViewState()

    data object BusinessActive: StartupViewState()

    sealed class Error: StartupViewState() {
        data class Generic(val cause: Throwable?): Error()

        sealed class DecryptionError : Error() {
            data class GeneralException(val cause: Throwable): DecryptionError()
            data class KeystoreFailure(val cause: Throwable): DecryptionError()
        }
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

    init {
        initView()
    }

    private fun initView() {

        if (state.value !is StartupViewState.Init) {
            return
        }

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when initialising startup-view: ", e)
            state.value = StartupViewState.Error.Generic(e)
        }) {

            val activeBusiness = BusinessRepo.activeBusiness.value
            if (activeBusiness != null) {
                val (nodeId, business) = activeBusiness
                log.info("business already available with id=$nodeId")
                if (business.connectionsManager.connections.value.peer !is Connection.ESTABLISHED) {
                    business.appConnectionsDaemon?.forceReconnect(AppConnectionsDaemon.ControlTarget.Peer)
                }
                state.value = StartupViewState.BusinessActive
                return@launch
            }

            state.value = StartupViewState.LoadingSeed
            val words = when (val result = SeedManager.loadAndDecryptSeed(context = application.applicationContext, expectedNodeId = null)) {
                is DecryptSeedResult.Failure.DecryptionError -> {
                    log.error("cannot decrypt seed file: ", result.cause)
                    state.value = StartupViewState.Error.DecryptionError.GeneralException(result.cause)
                    return@launch
                }
                is DecryptSeedResult.Failure.KeyStoreFailure -> {
                    log.error("key store failure: ", result.cause)
                    state.value = StartupViewState.Error.DecryptionError.KeystoreFailure(result.cause)
                    return@launch
                }
                is DecryptSeedResult.Failure.SeedFileUnreadable -> {
                    log.error("aborting, unreadable seed file")
                    state.value = StartupViewState.Error.Generic(null)
                    return@launch
                }
                is DecryptSeedResult.Failure.SeedInvalid -> {
                    log.error("aborting, seed is invalid")
                    state.value = StartupViewState.Error.Generic(null)
                    return@launch
                }

                is DecryptSeedResult.Failure.SeedNotFound -> {
                    state.value = StartupViewState.SeedNotFound
                    return@launch
                }

                is DecryptSeedResult.Success -> {
                    result.mnemonics
                }
            }

            state.value = StartupViewState.StartingBusiness
            val startResult = withContext(Dispatchers.Default) {
                BusinessRepo.startNewBusiness(words, isHeadless = false)
            }

            ChannelsWatcher.schedule(application.applicationContext)
            ContactsPhotoCleaner.schedule(application.applicationContext)

            when (startResult) {
                is StartBusinessResult.Success -> state.value = StartupViewState.BusinessActive
                is StartBusinessResult.Failure.Generic -> state.value = StartupViewState.Error.Generic(startResult.cause)
                is StartBusinessResult.Failure.LoadWalletError -> state.value = StartupViewState.Error.Generic(null)
            }
        }
    }

    private suspend fun startBusiness(words: List<String>) {

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
            val nodeIdHash = localKeyManager.nodeIdHash()

            val channelsDbFile = context.getDatabasePath("channels-${NodeParamsManager.chain.phoenixName}-$nodeIdHash.sqlite")
            if (channelsDbFile.exists()) {
                state.value = StartupViewState.SeedRecovery.Success.MatchingData
                val encodedSeed = EncryptedSeed.fromMnemonics(words)
                try {
                    KeystoreHelper.checkEncryptionCipherOrReset(KeystoreHelper.KEY_NO_AUTH)
                } catch (e: Exception) {
                    state.value = StartupViewState.SeedRecovery.Error.SeedDoesNotMatch
                    return@launch
                }
                val encrypted = EncryptedSeed.V2.SingleSeed.encrypt(encodedSeed)
                SeedManager.writeSeedToDisk(context, encrypted, overwrite = true)
                delay(1000)
                state.value = StartupViewState.Init
                initView()
            } else {
                state.value = StartupViewState.SeedRecovery.Error.SeedDoesNotMatch
            }
        }
    }

    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
            @Suppress("UNCHECKED_CAST")
            return StartupViewModel(application) as T
        }
    }
}