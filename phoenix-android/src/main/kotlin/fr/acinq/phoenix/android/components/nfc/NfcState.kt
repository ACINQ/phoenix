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

package fr.acinq.phoenix.android.components.nfc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class NfcState {
    data object Inactive: NfcState()
    sealed class Busy : NfcState()
    data object ShowReader: Busy()
    data class EmulatingTag(val paymentRequest: String): Busy()
}

object NfcStateRepository {
    private val _state = MutableStateFlow<NfcState?>(null)
    val state = _state.asStateFlow()

    fun isReading() = state.value is NfcState.ShowReader
    fun isEmulating() = state.value is NfcState.EmulatingTag

    fun updateState(s: NfcState) {
        _state.value = s
    }
}
