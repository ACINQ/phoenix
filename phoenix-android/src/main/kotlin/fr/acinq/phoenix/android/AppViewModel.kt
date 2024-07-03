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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import fr.acinq.phoenix.android.services.NodeService
import fr.acinq.phoenix.android.services.NodeServiceState
import fr.acinq.phoenix.android.utils.datastore.InternalDataRepository
import org.slf4j.LoggerFactory

class AppViewModel(
    private val internalDataRepository: InternalDataRepository
) : ViewModel() {
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
    val serviceState = ServiceStateLiveData(_service)

    val isScreenLocked = mutableStateOf(true)

    fun saveIsScreenLocked(isLocked: Boolean) {
        isScreenLocked.value = isLocked
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
                return AppViewModel(application.internalDataRepository) as T
            }
        }
    }
}

class ServiceStateLiveData(service: MutableLiveData<NodeService?>) : MediatorLiveData<NodeServiceState>() {
    private val log = LoggerFactory.getLogger(this::class.java)
    private var serviceState: LiveData<NodeServiceState>? = null

    init {
        value = service.value?.state?.value ?: NodeServiceState.Disconnected
        addSource(service) { s ->
            if (s == null) {
                log.debug("lost service, force state to Disconnected and remove source")
                serviceState?.let { removeSource(it) }
                serviceState = null
                value = NodeServiceState.Disconnected
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