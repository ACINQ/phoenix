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

package fr.acinq.phoenix.android.components.auth.pincode

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.android.components.auth.pincode.PinDialog.PIN_LENGTH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

sealed class NewPinState {

    sealed class EnterNewPin : NewPinState() {
        data object Init : EnterNewPin()

        sealed class Frozen : EnterNewPin() {
            // transient state to add a short pause before moving to step 2, for better UX
            data object Validating: Frozen()
            data object InvalidPin: Frozen()
        }
    }

    sealed class ConfirmNewPin() : NewPinState() {
        abstract val expectedPin: String
        data class Init(override val expectedPin: String): ConfirmNewPin()
        sealed class Frozen : ConfirmNewPin() {
            data class Writing(override val expectedPin: String) : Frozen()

            data class PinsDoNoMatch(override val expectedPin: String, val confirmedPin: String): Frozen()
            data class CannotWriteToDisk(override val expectedPin: String, val confirmedPin: String, val cause: Throwable): Frozen()
        }
    }
}

abstract class NewPinViewModel: ViewModel() {

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    var state by mutableStateOf<NewPinState>(NewPinState.EnterNewPin.Init)
        private set

    abstract fun writePinToDisk(context: Context, pin: String)

    fun reset() {
        state = NewPinState.EnterNewPin.Init
    }

    fun moveToConfirmPin(newPin: String) {
        if (state is NewPinState.EnterNewPin.Frozen.Validating) return
        state = NewPinState.EnterNewPin.Frozen.Validating

        viewModelScope.launch {
            if (newPin.isNotBlank() && newPin.length == PIN_LENGTH) {
                delay(300)
                state = NewPinState.ConfirmNewPin.Init(newPin)
            } else {
                state = NewPinState.EnterNewPin.Frozen.InvalidPin
                delay(2_000)
                reset()
            }
        }
    }

    fun checkAndSavePin(context: Context, expectedPin: String, confirmedPin: String, onPinWritten: () -> Unit) {
        if (state is NewPinState.ConfirmNewPin.Frozen) return
        state = NewPinState.ConfirmNewPin.Frozen.Writing(confirmedPin)

        viewModelScope.launch(Dispatchers.IO) {
            if (confirmedPin != expectedPin || confirmedPin.length != PIN_LENGTH) {
                state = NewPinState.ConfirmNewPin.Frozen.PinsDoNoMatch(expectedPin = expectedPin, confirmedPin = confirmedPin)
                delay(2_000)
                state = NewPinState.ConfirmNewPin.Init(expectedPin)
            } else {
                try {
                    writePinToDisk(context, confirmedPin)
                    viewModelScope.launch(Dispatchers.Main) {
                        onPinWritten()
                    }
                } catch (e: Exception) {
                    log.error("failed to write pin to disk: ", e)
                    state = NewPinState.ConfirmNewPin.Frozen.CannotWriteToDisk(expectedPin, confirmedPin, e)
                    delay(2_000)
                    reset()
                }
            }
        }
    }
}