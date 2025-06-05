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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


/** View model tracking the state of the PIN dialog UI. */
sealed class CheckPinState {
    data object Init : CheckPinState()
    data class Locked(val timeToWait: Duration): CheckPinState()
    data object CanType : CheckPinState()
    data object Checking : CheckPinState()
    data object MalformedInput: CheckPinState()
    data object IncorrectPin: CheckPinState()
    data class Error(val cause: Throwable) : CheckPinState()
}

abstract class CheckPinViewModel : ViewModel() {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    var state by mutableStateOf<CheckPinState>(CheckPinState.Init)
        private set

    var pinInput by mutableStateOf("")

    abstract suspend fun getPinCodeAttempt(): Int
    abstract suspend fun savePinCodeSuccess()
    abstract suspend fun savePinCodeFailure()
    abstract suspend fun getExpectedPin(context: Context): String?

    suspend fun evaluateLockState() {
        val currentPinCodeAttempt = getPinCodeAttempt()
        val timeToWait = when (currentPinCodeAttempt) {
            0, 1, 2 -> Duration.ZERO
            3 -> 10.seconds
            4 -> 30.seconds
            5 -> 1.minutes
            6 -> 2.minutes
            7 -> 5.minutes
            8 -> 10.minutes
            else -> 30.minutes
        }

        if (timeToWait > Duration.ZERO) {
            state = CheckPinState.Locked(timeToWait)
            val countdownJob = viewModelScope.launch {
                val countdownFlow = flow {
                    while (true) {
                        delay(1_000)
                        emit(Unit)
                    }
                }
                countdownFlow.collect {
                    val s = state
                    if (s is CheckPinState.Locked) {
                        state = CheckPinState.Locked((s.timeToWait.minus(1.seconds)).coerceAtLeast(Duration.ZERO))
                    }
                }
            }
            delay(timeToWait)
            countdownJob.cancelAndJoin()
            state = CheckPinState.CanType
        } else {
            state = CheckPinState.CanType
        }
    }

    fun checkPinAndSaveOutcome(context: Context, pin: String, onPinValid: () -> Unit) {
        if (state is CheckPinState.Checking || state is CheckPinState.Locked) return
        state = CheckPinState.Checking

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (pin.isBlank() || pin.length != PIN_LENGTH) {
                    log.debug("malformed pin")
                    state = CheckPinState.MalformedInput
                    delay(1300)
                    if (state is CheckPinState.MalformedInput) {
                        evaluateLockState()
                    }
                }

                val expected = getExpectedPin(context)
                if (pin == expected) {
                    log.debug("valid pin")
                    delay(20)
                    savePinCodeSuccess()
                    pinInput = ""
                    state = CheckPinState.CanType
                    viewModelScope.launch(Dispatchers.Main) {
                        onPinValid()
                    }
                } else {
                    log.debug("incorrect pin")
                    delay(80)
                    savePinCodeFailure()
                    state = CheckPinState.IncorrectPin
                    delay(1300)
                    pinInput = ""
                    evaluateLockState()
                }
            } catch (e: Exception) {
                log.error("error when checking pin code: ", e)
                state = CheckPinState.Error(e)
                delay(1300)
                pinInput = ""
                evaluateLockState()
            }
        }
    }
}