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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.android.BaseWalletId
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.UnknownWalletId
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.auth.pincode.PinDialog.PIN_LENGTH
import fr.acinq.phoenix.android.security.PinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

abstract class CheckPinViewModel() : ViewModel() {

    abstract val application: PhoenixApplication

    val log: Logger = LoggerFactory.getLogger(this::class.java)
    var state by mutableStateOf<CheckPinState>(CheckPinState.Init)

    var pinInput by mutableStateOf("")

    abstract suspend fun getPinCodeAttempt(): Flow<Int>

    suspend fun monitorPinCodeAttempts() {
        var countdownJob: Job? = null
        getPinCodeAttempt().collect { attemptCount ->
            val timeToWait = when (attemptCount) {
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
                countdownJob?.cancelAndJoin()
                countdownJob = viewModelScope.launch {
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
    }

    /**
     * Tests the provided [pin] against the expected pin, and saves the attempt outcome in the relevant preference.
     *
     * @param pin a pin code, should be [PIN_LENGTH] long
     * @param onPinValid the parameter is a wallet id because the unlocked wallet may be different from the expected one (hidden wallet).
     */
    abstract fun checkPinAndSaveOutcome(pin: String, onPinValid: (WalletId) -> Unit)
}