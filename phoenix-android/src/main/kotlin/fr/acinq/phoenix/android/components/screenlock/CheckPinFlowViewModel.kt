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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks the state of the PIN dialog UI. For the state of the
 * PIN lock, see [UserPrefsRepository.PREFS_CUSTOM_PIN_STATE].
 */
sealed class CheckPinFlowState {

    data object Init : CheckPinFlowState()

    data class Locked(val timeToWait: Duration): CheckPinFlowState()
    data object CanType : CheckPinFlowState()
    data object Checking : CheckPinFlowState()
    data object MalformedInput: CheckPinFlowState()
    data object IncorrectPin: CheckPinFlowState()
    data class Error(val cause: Throwable) : CheckPinFlowState()
}

class CheckPinFlowViewModel(private val userPrefsRepository: UserPrefsRepository) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)
    var state by mutableStateOf<CheckPinFlowState>(CheckPinFlowState.Init)
        private set

    var pinInput by mutableStateOf("")

    init {
        viewModelScope.launch { evaluateLockState() }
    }

    private suspend fun evaluateLockState() {
        val currentPinCodeAttempt = userPrefsRepository.getPinCodeAttempt.first()
        val timeToWait = when (currentPinCodeAttempt) {
            0, 1, 2 -> Duration.ZERO
            3 -> 10.seconds
            4 -> 1.minutes
            5 -> 2.minutes
            6 -> 5.minutes
            7 -> 10.minutes
            else -> 30.minutes
        }
        if (timeToWait > Duration.ZERO) {
            state = CheckPinFlowState.Locked(timeToWait)
            val countdownJob = viewModelScope.launch {
                val countdownFlow = flow {
                    while (true) {
                        delay(1_000)
                        emit(Unit)
                    }
                }
                countdownFlow.collect {
                    val s = state
                    if (s is CheckPinFlowState.Locked) {
                        state = CheckPinFlowState.Locked((s.timeToWait.minus(1.seconds)).coerceAtLeast(Duration.ZERO))
                    }
                }
            }
            delay(timeToWait)
            countdownJob.cancelAndJoin()
            state = CheckPinFlowState.CanType
        } else {
            state = CheckPinFlowState.CanType
        }
    }

    fun checkPinAndSaveOutcome(context: Context, pin: String, onPinValid: () -> Unit) {
        if (state is CheckPinFlowState.Checking || state is CheckPinFlowState.Locked) return
        state = CheckPinFlowState.Checking

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (pin.isBlank() || pin.length != PIN_LENGTH) {
                    log.debug("malformed pin")
                    state = CheckPinFlowState.MalformedInput
                    delay(1300)
                    if (state is CheckPinFlowState.MalformedInput) {
                        evaluateLockState()
                    }
                }

                val expected = EncryptedPin.getPinFromDisk(context)
                if (pin == expected) {
                    log.debug("valid pin")
                    delay(100)
                    userPrefsRepository.savePinCodeSuccess()
                    pinInput = ""
                    state = CheckPinFlowState.CanType
                    viewModelScope.launch(Dispatchers.Main) {
                        onPinValid()
                    }
                } else {
                    log.debug("incorrect pin")
                    delay(200)
                    userPrefsRepository.savePinCodeFailure()
                    state = CheckPinFlowState.IncorrectPin
                    delay(1300)
                    pinInput = ""
                    evaluateLockState()
                }
            } catch (e: Exception) {
                log.error("error when checking pin code: ", e)
                state = CheckPinFlowState.Error(e)
                delay(1300)
                pinInput = ""
                evaluateLockState()
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
                return CheckPinFlowViewModel(application.userPrefs) as T
            }
        }
    }
}