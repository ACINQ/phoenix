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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.components.auth.pincode.CheckPinViewModel
import fr.acinq.phoenix.android.security.EncryptedPin
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import kotlinx.coroutines.flow.first

class CheckScreenLockPinViewModel(private val userPrefsRepository: UserPrefsRepository) : CheckPinViewModel() {

    override suspend fun getPinCodeAttempt(): Int {
        return userPrefsRepository.getScreenLockPinCodeAttempt.first()
    }

    override suspend fun savePinCodeSuccess() {
        userPrefsRepository.saveScreenLockPinCodeSuccess()
    }

    override suspend fun savePinCodeFailure() {
        userPrefsRepository.saveScreenLockPinCodeFailure()
    }

    override suspend fun getExpectedPin(context: Context): String? {
        return EncryptedPin.getPinFromDisk(context)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
                return CheckScreenLockPinViewModel(application.userPrefs) as T
            }
        }
    }
}
