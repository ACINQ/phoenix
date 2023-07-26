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
import fr.acinq.lightning.NodeParams
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.migrations.ChannelsConsolidationHelper
import fr.acinq.phoenix.utils.migrations.ChannelsConsolidationResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class ChannelsConsolidationState {
    object Init : ChannelsConsolidationState()
    object InProgress : ChannelsConsolidationState()
    data class Done(val result: ChannelsConsolidationResult) : ChannelsConsolidationState()
}

class ChannelsConsolidationViewModel(
    val loggerFactory: org.kodein.log.LoggerFactory,
    val chain: NodeParams.Chain,
    val peerManager: PeerManager
) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<ChannelsConsolidationState>(ChannelsConsolidationState.Init)

    fun consolidate(ignoreDust: Boolean) {
        if (state.value is ChannelsConsolidationState.InProgress) return
        state.value = ChannelsConsolidationState.InProgress
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("consolidation has failed: ", e)
            state.value = ChannelsConsolidationState.Done(ChannelsConsolidationResult.Failure.Generic(e))
        }) {
            val res = ChannelsConsolidationHelper.consolidateChannels(loggerFactory, chain, peerManager, ignoreDust = ignoreDust)
            log.error("consolidation finished with result=$res")
            state.value = ChannelsConsolidationState.Done(res)
        }
    }

    class Factory(
        private val loggerFactory: org.kodein.log.LoggerFactory,
        private val chain: NodeParams.Chain,
        private val peerManager: PeerManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChannelsConsolidationViewModel(loggerFactory, chain, peerManager) as T
        }
    }
}