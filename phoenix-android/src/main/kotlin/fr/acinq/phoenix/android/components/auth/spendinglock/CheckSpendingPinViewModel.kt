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

package fr.acinq.phoenix.android.components.auth.spendinglock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.components.auth.pincode.CheckPinViewModel
import fr.acinq.phoenix.android.security.EncryptedSpendingPin
import fr.acinq.phoenix.android.utils.datastore.UserPrefsRepository
import kotlinx.coroutines.flow.first

class CheckSpendingPinViewModel(private val userPrefsRepository: UserPrefsRepository) : CheckPinViewModel() {

    override suspend fun getPinCodeAttempt(): Int {
        return userPrefsRepository.getSpendingPinCodeAttempt.first()
    }

    override suspend fun savePinCodeSuccess() {
        userPrefsRepository.saveSpendingPinCodeSuccess()
    }

    override suspend fun savePinCodeFailure() {
        userPrefsRepository.saveSpendingPinCodeFailure()
    }

    override suspend fun getExpectedPin(context: Context): String? {
        return EncryptedSpendingPin.getSpendingPinFromDisk(context)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? PhoenixApplication)
                return CheckSpendingPinViewModel(application.userPrefs) as T
            }
        }
    }
}