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


import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.phoenix.android.security.SeedManager
import fr.acinq.phoenix.android.service.NodeService
import fr.acinq.phoenix.android.service.WalletState
import org.slf4j.LoggerFactory

class AppViewModel() : ViewModel() {
    val log = LoggerFactory.getLogger(AppViewModel::class.java)

    /** Monitoring the state of the service - null if the service is disconnected. */
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
    val walletState = WalletStateLiveData(_service)

    fun writeSeed(context: Context, mnemonics: List<String>) {
        try {
            val existing = SeedManager.loadSeedFromDisk(context)
            if (existing == null) {
                val encrypted = EncryptedSeed.V2.NoAuth.encrypt(EncryptedSeed.fromMnemonics(mnemonics))
                SeedManager.writeSeedToDisk(context, encrypted)
                log.info("seed has been written to disk")
            } else {
                log.warn("cannot overwrite existing seed=${existing.name()}")
            }
        } catch (e: Exception) {
            log.error("failed to create new wallet: ", e)
        }
    }

    fun decryptSeed(context: Context): ByteArray? {
        return try {
            when (val seed = SeedManager.loadSeedFromDisk(context)) {
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
}

class WalletStateLiveData(service: MutableLiveData<NodeService?>) : MediatorLiveData<WalletState>() {
    private val log = LoggerFactory.getLogger(this::class.java)
    private var serviceState: LiveData<WalletState>? = null

    init {
        value = service.value?.state?.value ?: WalletState.Disconnected
        addSource(service) { s ->
            if (s == null) {
                log.info("lost service, force state to Disconnected and remove source")
                serviceState?.let { removeSource(it) }
                serviceState = null
                value = WalletState.Disconnected
            } else {
                log.info("service connected, now mirroring service's internal state")
                serviceState = s.state
                addSource(s.state) {
                    value = it
                }
            }
        }
    }
}