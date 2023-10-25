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

package fr.acinq.phoenix.android.settings.displayseed

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedFileState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class DisplaySeedViewModel : ViewModel() {

    sealed class ReadingSeedState() {
        object Init : ReadingSeedState()
        object ReadingSeed : ReadingSeedState()
        data class Decrypted(val words: List<String>) : ReadingSeedState()
        data class Error(val message: String) : ReadingSeedState()
    }

    val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<ReadingSeedState>(ReadingSeedState.Init)

    fun readSeed(seedFileState: SeedFileState) {
        if (state.value == ReadingSeedState.ReadingSeed) return
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to read seed: ${e.message}")
            state.value = ReadingSeedState.Error(e.localizedMessage ?: "n/a")
        }) {
            state.value = ReadingSeedState.ReadingSeed
            when {
                seedFileState is SeedFileState.Present && seedFileState.encryptedSeed is EncryptedSeed.V2.NoAuth -> {
                    val words = EncryptedSeed.toMnemonics(seedFileState.encryptedSeed.decrypt())
                    delay(300)
                    state.value = ReadingSeedState.Decrypted(words)
                }
                seedFileState is SeedFileState.Error.Unreadable -> {
                    state.value = ReadingSeedState.Error(seedFileState.message ?: "n/a")
                }
                else -> {
                    log.warn("unable to read seed in state=$seedFileState")
                    state.value = ReadingSeedState.Error("unhandled state=${seedFileState::class.simpleName}")
                }
            }
        }
    }
}