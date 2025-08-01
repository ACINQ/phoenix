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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.security.DecryptSeedResult
import fr.acinq.phoenix.android.security.SeedManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class DisplaySeedViewModel(val application: PhoenixApplication) : ViewModel() {

    sealed class ReadingSeedState() {
        data object Init : ReadingSeedState()
        data object ReadingSeed : ReadingSeedState()
        data class Decrypted(val words: List<String>) : ReadingSeedState()
        sealed class Error : ReadingSeedState() {
            data object CouldNotReadSeedFile: Error()
            data object CouldNotFindMatch: Error()
        }
    }

    private val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<ReadingSeedState>(ReadingSeedState.Init)

    fun readActiveSeed(nodeId: String) {
        if (state.value == ReadingSeedState.ReadingSeed) return
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to read seed: ${e.message}")
            state.value = ReadingSeedState.Error.CouldNotReadSeedFile
        }) {
            state.value = ReadingSeedState.ReadingSeed
            when (val result = SeedManager.loadAndDecrypt(application.applicationContext)) {
                is DecryptSeedResult.Success -> {
                    delay(300)
                    val match = result.mnemonicsMap[nodeId]
                    if (!match.isNullOrEmpty()) {
                        state.value = ReadingSeedState.Decrypted(match)
                    } else {
                        log.error("could not find mnemonics for node_id=$nodeId")
                        state.value = ReadingSeedState.Error.CouldNotFindMatch
                    }
                }
                is DecryptSeedResult.Failure -> {
                    log.error("unable to get active seed: {}", result)
                    state.value = ReadingSeedState.Error.CouldNotReadSeedFile
                }
            }
        }
    }

    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return DisplaySeedViewModel(application) as T
        }
    }
}