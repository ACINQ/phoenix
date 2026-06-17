/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.settings.walletinfo

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.swapin.LegacySwapInSignerResult
import fr.acinq.phoenix.utils.swapin.SwapInSignerHelper
import fr.acinq.phoenix.utils.swapin.TaprootSwapInSignerResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


enum class SwapInOptions { LEGACY, TAPROOT }
sealed class SwapInSignerState

sealed class LegacySwapInSignerState : SwapInSignerState() {
    data object Init : LegacySwapInSignerState()
    data object Signing : LegacySwapInSignerState()
    data class Signed(val result: LegacySwapInSignerResult.Success) : LegacySwapInSignerState()
    data class Failed(val result: LegacySwapInSignerResult.Failure) : LegacySwapInSignerState()
}

sealed class TaprootSwapInSignerState : SwapInSignerState() {
    data object Init : TaprootSwapInSignerState()
    data object Signing : TaprootSwapInSignerState()
    data class Signed(val result: TaprootSwapInSignerResult.Success) : TaprootSwapInSignerState()
    data class Failed(val result: TaprootSwapInSignerResult.Failure) : TaprootSwapInSignerState()
}

class SwapInSignerViewModel(
    private val business: PhoenixBusiness,
) : ViewModel() {

    private val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<SwapInSignerState>(LegacySwapInSignerState.Init)

    fun signLegacy(
        unsignedTx: String,
    ) {
        if (state.value == LegacySwapInSignerState.Signing) return
        state.value = LegacySwapInSignerState.Signing

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("(legacy) failed to sign tx=$unsignedTx: ", e)
            state.value = LegacySwapInSignerState.Failed(LegacySwapInSignerResult.Failure.SigningError)
        }) {
            log.debug("(legacy) signing tx=$unsignedTx")
            val result = SwapInSignerHelper.signPartialTxLegacy(business = business, unsignedTx = unsignedTx)
            state.value = when (result) {
                is LegacySwapInSignerResult.Success -> LegacySwapInSignerState.Signed(result)
                is LegacySwapInSignerResult.Failure -> LegacySwapInSignerState.Failed(result)
            }
        }
    }

    fun signTaproot(
        unsignedTx: String,
        serverNonce: String,
    ) {
        if (state.value == TaprootSwapInSignerState.Signing) return
        state.value = TaprootSwapInSignerState.Signing

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("(taproot) failed to sign tx=$unsignedTx: ", e)
            state.value = TaprootSwapInSignerState.Failed(TaprootSwapInSignerResult.Failure.SigningError)
        }) {
            log.debug("(legacy) signing tx=$unsignedTx nonce=$serverNonce")
            val result = SwapInSignerHelper.signPartialTxTaproot(business = business, unsignedTx = unsignedTx, serverNonce = serverNonce)
            state.value = when (result) {
                is TaprootSwapInSignerResult.Success -> TaprootSwapInSignerState.Signed(result)
                is TaprootSwapInSignerResult.Failure -> TaprootSwapInSignerState.Failed(result)
            }
        }
    }

    class Factory(
        private val business: PhoenixBusiness
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SwapInSignerViewModel(business) as T
        }
    }
}