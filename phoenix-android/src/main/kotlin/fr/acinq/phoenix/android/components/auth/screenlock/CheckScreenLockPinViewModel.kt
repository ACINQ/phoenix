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

package fr.acinq.phoenix.android.components.auth.screenlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.auth.pincode.CheckPinState
import fr.acinq.phoenix.android.components.auth.pincode.CheckPinViewModel
import fr.acinq.phoenix.android.components.auth.pincode.PinDialog.PIN_LENGTH
import fr.acinq.phoenix.android.security.PinManager
import fr.acinq.phoenix.android.utils.datastore.DataStoreManager
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class CheckScreenLockPinViewModel(override val application: PhoenixApplication, val walletId: WalletId, val acceptHiddenPin: Boolean) : CheckPinViewModel() {

    val userPrefs: UserPrefs by lazy { DataStoreManager.loadUserPrefsForWallet(context = application.applicationContext, walletId) }

    init {
        viewModelScope.launch { monitorPinCodeAttempts() }
    }

    override suspend fun getPinCodeAttempt(): Flow<Int> {
        return userPrefs.getLockPinCodeAttempt
    }

    suspend fun savePinCodeSuccess() {
        userPrefs.saveLockPinCodeSuccess()
    }

    suspend fun savePinCodeSuccessForHidden() {
        application.globalPrefs.saveHiddenLockPinCodeSuccess()
    }

    suspend fun savePinCodeFailure() {
        userPrefs.saveLockPinCodeFailure()
    }

    fun getExpectedPin(): String? {
        return PinManager.getLockPinMapFromDisk(application.applicationContext)[walletId]
    }

    suspend fun resetPinPrefs() {
        userPrefs.saveIsScreenLockPinEnabled(false)
        userPrefs.saveLockPinCodeSuccess()
    }

    override fun checkPinAndSaveOutcome(pin: String, onPinValid: (WalletId) -> Unit) {
        if (state is CheckPinState.Checking || state is CheckPinState.Locked) return
        state = CheckPinState.Checking

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (pin.isBlank() || pin.length != PIN_LENGTH) {
                    log.debug("malformed pin")
                    state = CheckPinState.MalformedInput
                    delay(1300)
                    savePinCodeFailure()
                }

                val expected = getExpectedPin()
                when {
                    expected == null -> {
                        log.info("no expected pin for $walletId, aborting pin-check and reset pin settings")
                        resetPinPrefs()
                        viewModelScope.launch(Dispatchers.Main) {
                            onPinValid(walletId)
                        }
                    }
                    pin == expected -> {
                        log.debug("valid pin for {}", walletId)
                        delay(20)
                        savePinCodeSuccess()
                        pinInput = ""
                        state = CheckPinState.CanType
                        viewModelScope.launch(Dispatchers.Main) {
                            onPinValid(walletId)
                        }
                    }
                    else -> {
                        log.debug("incorrect pin for {}", walletId)
                        // in some screens (e.g. at startup), the user may be trying to unlock a hidden wallet
                        if (acceptHiddenPin) {
                            when (val firstHiddenMatch = findHiddenWallet(pin)) {
                                null -> {
                                    delay(80)
                                    state = CheckPinState.IncorrectPin
                                    delay(1300)
                                    pinInput = ""
                                    savePinCodeFailure()
                                }
                                else -> {
                                    log.debug("pin match with {}", firstHiddenMatch)
                                    delay(20)
                                    savePinCodeSuccessForHidden()
                                    DataStoreManager.loadUserPrefsForWallet(context = application.applicationContext, walletId = firstHiddenMatch).saveLockPinCodeSuccess()
                                    pinInput = ""
                                    state = CheckPinState.CanType
                                    viewModelScope.launch(Dispatchers.Main) {
                                        onPinValid(firstHiddenMatch)
                                    }
                                }
                            }
                        } else {
                            delay(80)
                            state = CheckPinState.IncorrectPin
                            delay(1300)
                            pinInput = ""
                            savePinCodeFailure()
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("error when checking pin code: ", e)
                state = CheckPinState.Error(e)
                delay(1300)
                savePinCodeFailure()
            }
        }
    }

    /**
     * Checks if the provided PIN matches a hidden wallet, and if a match is found, returns its walletId.
     */
    private suspend fun findHiddenWallet(pin: String): WalletId? {
        val pinMap = PinManager.getLockPinMapFromDisk(application.applicationContext)
        val metadata = application.globalPrefs.getAvailableWalletsMeta.first()
        return pinMap.filter { (walletId, walletPin) -> pin == walletPin && metadata[walletId]?.isHidden == true }.entries.firstOrNull()?.key
    }

    class Factory(val application: PhoenixApplication, val walletId: WalletId, val acceptHiddenPin: Boolean) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return CheckScreenLockPinViewModel(application, walletId, acceptHiddenPin) as T
        }
    }
}
