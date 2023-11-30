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
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.services.NodeService
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.nodeIdHash
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.security.GeneralSecurityException
import java.security.KeyStoreException


sealed class StartupDecryptionState {
    object Init : StartupDecryptionState()
    object DecryptingSeed : StartupDecryptionState()
    object DecryptionSuccess : StartupDecryptionState()
    sealed class DecryptionError : StartupDecryptionState() {
        data class Other(val cause: Throwable): DecryptionError()
        data class KeystoreFailure(val cause: Throwable): DecryptionError()
        data class UnhandledVersion(val name: String): DecryptionError()
    }
    sealed class SeedInputFallback : StartupDecryptionState() {
        object Init: SeedInputFallback()
        object CheckingSeed: SeedInputFallback()
        sealed class Success: SeedInputFallback() {
            object MatchingData: Success()
            object WrittenToDisk: Success()
        }
        sealed class Error: SeedInputFallback() {
            data class Other(val cause: Throwable): Error()
            object SeedDoesNotMatch: Error()
        }
    }
}

class StartupViewModel : ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)
    val decryptionState = mutableStateOf<StartupDecryptionState>(StartupDecryptionState.Init)

    fun decryptSeedAndStart(encryptedSeed: EncryptedSeed, service: NodeService, checkLegacyChannels: Boolean) {
        if (decryptionState.value is StartupDecryptionState.DecryptingSeed) return
        decryptionState.value = StartupDecryptionState.DecryptingSeed
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when decrypting seed: ", e)
            decryptionState.value = when (e) {
                is KeyStoreException, is GeneralSecurityException -> StartupDecryptionState.DecryptionError.KeystoreFailure(e)
                else -> StartupDecryptionState.DecryptionError.Other(e)
            }

        }) {
            when (encryptedSeed) {
                is EncryptedSeed.V2.NoAuth -> {
                    log.debug("decrypting seed...")
                    delay(200)
                    val seed = encryptedSeed.decrypt()
                    log.debug("seed decrypted!")
                    decryptionState.value = StartupDecryptionState.DecryptionSuccess
                    service.startBusiness(seed, checkLegacyChannels)
                }
                is EncryptedSeed.V2.WithAuth -> {
                    log.error("decryption failed, unsupported type=${encryptedSeed.name()}")
                    decryptionState.value = StartupDecryptionState.DecryptionError.UnhandledVersion(encryptedSeed.name())
                }
            }
        }
    }

    fun checkSeedFallback(context: Context, words: List<String>, onSuccess: suspend (ByteArray) -> Unit) {
        if (decryptionState.value is StartupDecryptionState.SeedInputFallback.CheckingSeed) return
        decryptionState.value = StartupDecryptionState.SeedInputFallback.CheckingSeed

        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("error when checking seed fallback against existing data: ", e)
            decryptionState.value = StartupDecryptionState.SeedInputFallback.Error.Other(e)
        }) {
            val seed = MnemonicCode.toSeed(mnemonics = words.joinToString(" "), passphrase = "").byteVector()
            val localKeyManager = LocalKeyManager(seed = seed, chain = NodeParamsManager.chain, remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub)
            val nodeIdHash = localKeyManager.nodeIdHash()
            val channelsDbFile = context.getDatabasePath("channels-${NodeParamsManager.chain.name.lowercase()}-$nodeIdHash.sqlite")
            if (channelsDbFile.exists()) {
                decryptionState.value = StartupDecryptionState.SeedInputFallback.Success.MatchingData
                val encodedSeed = EncryptedSeed.fromMnemonics(words)
                val encrypted = EncryptedSeed.V2.NoAuth.encrypt(encodedSeed)
                SeedManager.writeSeedToDisk(context, encrypted, overwrite = true)
                delay(1000)
                decryptionState.value = StartupDecryptionState.SeedInputFallback.Success.WrittenToDisk
                onSuccess(encodedSeed)
            } else {
                decryptionState.value = StartupDecryptionState.SeedInputFallback.Error.SeedDoesNotMatch
            }
        }
    }
}