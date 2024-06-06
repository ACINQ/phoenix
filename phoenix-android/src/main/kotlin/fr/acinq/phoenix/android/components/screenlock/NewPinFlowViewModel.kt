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

package fr.acinq.phoenix.android.components.screenlock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.components.screenlock.PinDialog.PIN_LENGTH
import fr.acinq.phoenix.android.security.EncryptedPin
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class NewPinFlowState {

    sealed class EnterNewPin : NewPinFlowState() {
        data object Init : EnterNewPin()

        sealed class Frozen : EnterNewPin() {
            // transient state to add a short pause before moving to step 2, for better UX
            data object Validating: Frozen()
            data object InvalidPin: Frozen()
        }
    }

    sealed class ConfirmNewPin() : NewPinFlowState() {
        abstract val expectedPin: String
        data class Init(override val expectedPin: String): ConfirmNewPin()
        sealed class Frozen : ConfirmNewPin() {
            data class Writing(override val expectedPin: String) : Frozen()

            data class PinsDoNoMatch(override val expectedPin: String, val confirmedPin: String): Frozen()
            data class CannotWriteToDisk(override val expectedPin: String, val confirmedPin: String, val cause: Throwable): Frozen()
        }
    }
}

class NewPinFlowViewModel(val userPrefsRepository: UserPrefsRepository): ViewModel() {

    val log = LoggerFactory.getLogger(this::class.java)

    var state by mutableStateOf<NewPinFlowState>(NewPinFlowState.EnterNewPin.Init)
        private set

    fun reset() {
        state = NewPinFlowState.EnterNewPin.Init
    }

    fun moveToConfirmPin(newPin: String) {
        if (state is NewPinFlowState.EnterNewPin.Frozen.Validating) return
        state = NewPinFlowState.EnterNewPin.Frozen.Validating

        viewModelScope.launch {
            if (newPin.isNotBlank() && newPin.length == PIN_LENGTH) {
                delay(300)
                state = NewPinFlowState.ConfirmNewPin.Init(newPin)
            } else {
                state = NewPinFlowState.EnterNewPin.Frozen.InvalidPin
                delay(2_000)
                reset()
            }
        }
    }

    fun checkAndSavePin(context: Context, expectedPin: String, confirmedPin: String, onPinWritten: () -> Unit) {
        if (state is NewPinFlowState.ConfirmNewPin.Frozen) return
        state = NewPinFlowState.ConfirmNewPin.Frozen.Writing(confirmedPin)

        viewModelScope.launch(Dispatchers.IO) {
            if (confirmedPin != expectedPin || confirmedPin.length != PIN_LENGTH) {
                state = NewPinFlowState.ConfirmNewPin.Frozen.PinsDoNoMatch(expectedPin = expectedPin, confirmedPin = confirmedPin)
                delay(2_000)
                state = NewPinFlowState.ConfirmNewPin.Init(expectedPin)
            } else {
                try {
                    EncryptedPin.writePinToDisk(context, confirmedPin)
                    viewModelScope.launch(Dispatchers.Main) {
                        onPinWritten()
                    }
                } catch (e: Exception) {
                    log.error("failed to write pin to disk: ", e)
                    state = NewPinFlowState.ConfirmNewPin.Frozen.CannotWriteToDisk(expectedPin, confirmedPin, e)
                    delay(2_000)
                    reset()
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
                return NewPinFlowViewModel(application.userPrefs) as T
            }
        }
    }
}