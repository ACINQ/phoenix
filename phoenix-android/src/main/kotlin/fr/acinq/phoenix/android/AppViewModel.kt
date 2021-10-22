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
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.*
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.KeyState
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.service.NodeService
import fr.acinq.phoenix.android.service.WalletState
import org.slf4j.LoggerFactory

@SuppressLint("StaticFieldLeak")
class AppViewModel(private val applicationContext: Context) : ViewModel() {
    val log = LoggerFactory.getLogger(MainActivity::class.java)
    var keyState: KeyState by mutableStateOf(KeyState.Unknown)
        private set

    /** Watch service - null if the service is disconnected. */
    private val _service = MutableLiveData<NodeService?>(null)
    /** Nullable accessor for the service. */
    val service: NodeService? get() = _service.value

    /** Connection to the NodeService. */
    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(component: ComponentName, bind: IBinder) {
            log.info("connected to NodeService")
            _service.value = (bind as NodeService.NodeBinder).getService()
        }

        override fun onServiceDisconnected(component: ComponentName) {
            log.info("disconnected from NodeService")
            _service.postValue(null)
        }
    }

    /** Mirrors the node state using a MediatorLiveData. A LiveData object is used because this object can be used outside of compose. */
    val walletState = StateLiveData(_service)

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
            log.info("seed has been written to disk")
        } catch (e: Exception) {
            log.error("failed to create new wallet: ", e)
        }
    }

    fun decryptSeed(): ByteArray? {
        return try {
            when (val seed = SeedManager.loadSeedFromDisk(applicationContext)) {
                is EncryptedSeed.V2.NoAuth -> seed.decrypt()
                else -> throw RuntimeException("no seed sorry")
            }
        } catch (e: Exception) {
            log.error("could not decrypt seed", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        service?.shutdown()
        log.info("AppViewModel has been cleared")
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(context) as T
        }
    }
}

class StateLiveData(service: MutableLiveData<NodeService?>): MediatorLiveData<WalletState>() {
    private val log = LoggerFactory.getLogger(this::class.java)
    private var serviceState: LiveData<WalletState>? = null

    init {
        value = WalletState.Disconnected
        addSource(service) { s ->
            if (s == null) {
                log.info("lost service, force state to Disconnected and remove source")
                serviceState?.let { removeSource(it) }
                serviceState = null
                value = WalletState.Disconnected
            } else {
                log.info("service connected, now mirroring service's internal state")
                addSource(s.state) {
                    value = it
                }
            }
        }
    }
}