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
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
            log.debug("connected to NodeService")
            _service.value = (bind as NodeService.NodeBinder).getService()
        }

        override fun onServiceDisconnected(component: ComponentName) {
            log.debug("disconnected from NodeService")
            _service.postValue(null)
        }
    }

    /** Mirrors the node state using a MediatorLiveData. A LiveData object is used because this object can be used outside of compose. */
    val walletState = WalletStateLiveData(_service)

    /** Tells if the UI is locked with biometrics. */
    var lockState by mutableStateOf<LockState>(LockState.Locked.Default)

    override fun onCleared() {
        super.onCleared()
        service?.shutdown()
        log.debug("AppViewModel has been cleared")
    }
}

class WalletStateLiveData(service: MutableLiveData<NodeService?>) : MediatorLiveData<WalletState>() {
    private val log = LoggerFactory.getLogger(this::class.java)
    private var serviceState: LiveData<WalletState>? = null

    init {
        value = service.value?.state?.value ?: WalletState.Disconnected
        addSource(service) { s ->
            if (s == null) {
                log.debug("lost service, force state to Disconnected and remove source")
                serviceState?.let { removeSource(it) }
                serviceState = null
                value = WalletState.Disconnected
            } else {
                log.debug("service connected, now mirroring service's internal state")
                serviceState = s.state
                addSource(s.state) {
                    value = it
                }
            }
        }
    }
}