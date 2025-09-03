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

package fr.acinq.phoenix.android.initwallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.android.BuildConfig
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.managers.NodeParamsManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

sealed class WritingSeedState {
    data object Init : WritingSeedState()
    data class Writing(val mnemonics: List<String>) : WritingSeedState()
    data class WrittenToDisk(val encryptedSeed: EncryptedSeed) : WritingSeedState()
    sealed class Error : WritingSeedState() {
        data class Generic(val cause: Throwable) : Error()
        data object CannotLoadSeedMap: Error()
        data object SeedAlreadyExists: Error()
    }
}

abstract class InitViewModel : ViewModel() {

    abstract val application: PhoenixApplication

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    /** Monitors the writing of a seed on disk ; used by the restore view and the create view thru [writeSeed]. */
    var writingState by mutableStateOf<WritingSeedState>(WritingSeedState.Init)
        private set

    /**
     * Attempts to write a seed on disk and updates the view model state. If a seed already
     * exists on disk, this method will put the [writingState] in error.
     */
    fun writeSeed(
        mnemonics: List<String>,
        isNewWallet: Boolean,
        onSeedWritten: (WalletId) -> Unit
    ) {
        if (writingState !is WritingSeedState.Init) return
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to write mnemonics to disk: ${e.message}")
            writingState = WritingSeedState.Error.Generic(e)
        }) {
            log.debug("writing mnemonics to disk...")
            writingState = WritingSeedState.Writing(mnemonics)
            val existingSeeds = SeedManager.loadAndDecryptOrNull(application.applicationContext)?.map {
                it.key to it.value.words
            }?.toMap()

            val seed = MnemonicCode.toSeed(mnemonics, "").toByteVector()
            val keyManager = LocalKeyManager(seed, NodeParamsManager.chain, NodeParamsManager.remoteSwapInXpub)
            val newWalletId = WalletId(keyManager.nodeKeys.nodeKey.publicKey)

            when {
                existingSeeds == null -> {
                    log.error("could not load the existing seed map, aborting...")
                    writingState = WritingSeedState.Error.CannotLoadSeedMap
                    return@launch
                }
                existingSeeds.containsKey(newWalletId) -> {
                    log.info("attempting to import a seed that already exists, aborting...")
                    writingState = WritingSeedState.Error.SeedAlreadyExists
                    return@launch
                }
                else -> {
                    val newSeedMap = existingSeeds + (newWalletId to mnemonics)
                    val encrypted = EncryptedSeed.V2.MultipleSeed.encrypt(newSeedMap)
                    SeedManager.writeSeedToDisk(application.applicationContext, encrypted, overwrite = true)
                    writingState = WritingSeedState.WrittenToDisk(encrypted)
                    if (isNewWallet) {
                        log.info("wallet successfully created and written to disk")
                    } else {
                        log.info("wallet successfully restored and written to disk")
                    }
                }
            }

            viewModelScope.launch(Dispatchers.Main) {
                application.globalPrefs.saveLastUsedAppCode(BuildConfig.VERSION_CODE)
                delay(1000)
                onSeedWritten(newWalletId)
            }
        }
    }
}
