/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android


import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration

class AppViewModel(
    private val application: PhoenixApplication
) : ViewModel() {

    private val log = LoggerFactory.getLogger(AppViewModel::class.java)

    val isScreenLocked = mutableStateOf(true)
    val promptScreenLockImmediately = mutableStateOf(true)

    private val autoLockHandler = Handler(Looper.getMainLooper())
    private val autoLockRunnable: Runnable = Runnable { lockScreen() }

    init {
        monitorUserLockPrefs()
        scheduleAutoLock()
    }

    fun scheduleAutoLock() {
        viewModelScope.launch {
            val autoLockDelay = application.userPrefs.getAutoLockDelay.first()
            autoLockHandler.removeCallbacksAndMessages(null)
            if (autoLockDelay != Duration.INFINITE) {
                autoLockHandler.postDelayed(autoLockRunnable, autoLockDelay.inWholeMilliseconds)
            }
        }
    }

    private fun monitorUserLockPrefs() {
        viewModelScope.launch {
            combine(application.userPrefs.getIsScreenLockBiometricsEnabled, application.userPrefs.getIsScreenLockPinEnabled) { isBiometricEnabled, isCustomPinEnabled ->
                isBiometricEnabled to isCustomPinEnabled
            }.collect { (isBiometricEnabled, isCustomPinEnabled) ->
                if (!isBiometricEnabled && !isCustomPinEnabled) {
                    unlockScreen()
                }
            }
        }
    }

    fun unlockScreen() {
        isScreenLocked.value = false
        scheduleAutoLock()
    }

    fun lockScreen() {
        isScreenLocked.value = true
    }

    override fun onCleared() {
        super.onCleared()
        log.info("AppViewModel cleared")
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY] as? PhoenixApplication)
                return AppViewModel(application) as T
            }
        }
    }
}