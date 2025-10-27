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
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.auth.pincode.NewPinViewModel
import fr.acinq.phoenix.android.security.PinManager

class NewScreenLockPinViewModel(override val application: PhoenixApplication, override val walletId: WalletId): NewPinViewModel() {

    override suspend fun writePinToDisk(pin: String) {
        val newPinMap = PinManager.getLockPinMapFromDisk(application.applicationContext) + (walletId to pin)
        PinManager.writeLockPinMapToDisk(application.applicationContext, newPinMap)
    }

    class Factory(val application: PhoenixApplication, val walletId: WalletId) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return NewScreenLockPinViewModel(application, walletId) as T
        }
    }
}

