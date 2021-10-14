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

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.utils.Prefs
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.FiatCurrency
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@SuppressLint("StaticFieldLeak")
class AppViewModel(private val applicationContext: Context) : ViewModel() {
    val log: Logger = newLogger(LoggerFactory.default)
    var keyState: KeyState by mutableStateOf(KeyState.Unknown)
        private set

    init {
        refreshSeed()
    }

    private fun refreshSeed() {
        keyState = try {
            when (val seed = SeedManager.loadSeedFromDisk(applicationContext)) {
                null -> KeyState.Absent
                is EncryptedSeed.V2.NoAuth -> KeyState.Present(seed)
                else -> KeyState.Error.UnhandledSeedType
            }
        } catch (e: Exception) {
            KeyState.Error.Unreadable
        }
    }

    fun writeSeed(context: Context, mnemonics: List<String>) {
        try {
            val encrypted = EncryptedSeed.V2.NoAuth.encrypt(EncryptedSeed.fromMnemonics(mnemonics))
            SeedManager.writeSeedToDisk(context, encrypted)
            refreshSeed()
            log.info { "seed has been written to disk" }
        } catch (e: Exception) {
            log.error(e) { "failed to create new wallet: " }
        }
    }

    fun decryptSeed(): ByteArray? {
        return try {
            when (val seed = SeedManager.loadSeedFromDisk(applicationContext)) {
                is EncryptedSeed.V2.NoAuth -> seed.decrypt()
                else -> throw RuntimeException("no seed sorry")
            }
        } catch (e: Exception) {
            log.error(e) { "could not decrypt seed" }
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        log.info { "AppViewModel has been cleared" }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(context) as T
        }
    }
}