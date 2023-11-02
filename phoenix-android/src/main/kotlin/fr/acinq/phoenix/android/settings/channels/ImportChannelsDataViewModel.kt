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

package fr.acinq.phoenix.android.settings.channels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.import.ChannelsImportHelper
import fr.acinq.phoenix.utils.import.ChannelsImportResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class ImportChannelsDataState {
    object Init : ImportChannelsDataState()
    object Importing : ImportChannelsDataState()
    data class Done(val result: ChannelsImportResult): ImportChannelsDataState()
}

class ImportChannelsDataViewModel(val peerManager: PeerManager, val nodeParamsManager: NodeParamsManager) : ViewModel() {

    private val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<ImportChannelsDataState>(ImportChannelsDataState.Init)

    fun importData(data: String, business: PhoenixBusiness) {
        if (state.value == ImportChannelsDataState.Importing) return
        viewModelScope.launch(
            Dispatchers.Default + CoroutineExceptionHandler { _, e ->
                log.error("failed to import channels data: ", e)
                state.value = ImportChannelsDataState.Done(ChannelsImportResult.Failure.Generic(e))
            }
        ) {
            state.value = ImportChannelsDataState.Importing
            delay(300)
            val result = ChannelsImportHelper.doImportChannels(
                data = data,
                biz = business
            )
            state.value = ImportChannelsDataState.Done(result)
        }
    }

    class Factory(
        private val peerManager: PeerManager,
        private val nodeParamsManager: NodeParamsManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ImportChannelsDataViewModel(peerManager, nodeParamsManager) as T
        }
    }
}