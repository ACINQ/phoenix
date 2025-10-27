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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CheckHiddenLockPinViewModel(override val application: PhoenixApplication) : CheckPinViewModel() {

    init {
        viewModelScope.launch { monitorPinCodeAttempts() }
    }

    override suspend fun getPinCodeAttempt(): Flow<Int> {
        return application.globalPrefs.getHiddenLockPinCodeAttempt
    }

    suspend fun savePinCodeSuccess(walletId: WalletId) {
        application.globalPrefs.saveHiddenLockPinCodeSuccess()
        DataStoreManager.loadUserPrefsForWallet(context = application.applicationContext, walletId = walletId).saveLockPinCodeSuccess()
    }

    suspend fun savePinCodeFailure() {
        application.globalPrefs.saveHiddenLockPinCodeFailure()
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

                val pinMap = PinManager.getLockPinMapFromDisk(application.applicationContext)
                val metadata = application.globalPrefs.getAvailableWalletsMeta.first()
                // we only consider hidden wallets!
                val firstMatch = pinMap.filter { (walletId, walletPin) -> pin == walletPin && metadata[walletId]?.isHidden == true }.entries.firstOrNull()

                when (firstMatch) {
                    null -> {
                        delay(80)
                        state = CheckPinState.IncorrectPin
                        delay(1300)
                        pinInput = ""
                        savePinCodeFailure()
                    }
                    else -> {
                        log.debug("found pin for {}", firstMatch.key)
                        delay(20)
                        savePinCodeSuccess(firstMatch.key)
                        pinInput = ""
                        state = CheckPinState.CanType
                        viewModelScope.launch(Dispatchers.Main) {
                            onPinValid(firstMatch.key)
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

    class Factory(val application: PhoenixApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return CheckHiddenLockPinViewModel(application) as T
        }
    }
}
