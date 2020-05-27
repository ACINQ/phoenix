/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.phoenix

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.*
import fr.acinq.eclair.db.PlainPayment
import fr.acinq.eclair.io.PayToOpenRequestEvent
import fr.acinq.eclair.payment.PaymentFailed
import fr.acinq.eclair.payment.PaymentReceived
import fr.acinq.eclair.payment.PaymentSent
import fr.acinq.eclair.wire.SwapInPending
import fr.acinq.phoenix.background.KitState
import fr.acinq.phoenix.background.EclairNodeService
import fr.acinq.phoenix.events.RemovePendingSwapIn
import fr.acinq.phoenix.utils.ServiceDisconnected
import fr.acinq.phoenix.utils.SingleLiveEvent
import fr.acinq.phoenix.utils.Wallet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory
import java.util.*

class AppViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(AppViewModel::class.java)

  /** Watch service - null if the service is disconnected. */
  private val _service = MutableLiveData<EclairNodeService?>(null)
  /** Nullable accessor for the service. */
  val service: EclairNodeService? get() = _service.value
  /** Accessor for the service, throw [ServiceDisconnected] if the service is disconnected. */
  val requireService: EclairNodeService get() = _service.value ?: throw ServiceDisconnected
  val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(component: ComponentName, bind: IBinder) {
      log.info("eclair service connected")
      _service.value = (bind as EclairNodeService.NodeBinder).getService()
    }

    override fun onServiceDisconnected(component: ComponentName) {
      log.info("eclair service disconnected")
      _service.postValue(null)
    }
  }

  /** Mirrors the node state using a MediatorLiveData. */
  val state = StateLiveData(_service)

  /** Tracks the current URI contained the intent that started the app. */
  val currentURIIntent = MutableLiveData<String>()

  /** Id of the fragment the app currently displays. */
  val currentNav = MutableLiveData<Int>()

  /** Contains an object that will trigger a navigation change. Fire once! */
  val navigationEvent = SingleLiveEvent<Any>()

  /** List of payments. */
  val payments = MutableLiveData<List<PlainPayment>>()

  /** List of swap-ins waiting for confirmation. */
  val pendingSwapIns = MutableLiveData(HashMap<String, SwapInPending>())

  init {
    currentNav.value = R.id.startup_fragment
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
  }

  override fun onCleared() {
    EventBus.getDefault().unregister(this)
    service?.shutdown() // we don't want the service to run if the UI is killed.
    super.onCleared()
    log.debug("appkit has been cleared")
  }

  fun hasWalletBeenSetup(context: Context): Boolean {
    return Wallet.getSeedFile(context).exists()
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: SwapInPending) {
    pendingSwapIns.value?.run {
      put(event.bitcoinAddress(), event)
      pendingSwapIns.postValue(this)
    }
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: RemovePendingSwapIn) {
    pendingSwapIns.value?.remove(event.address)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PaymentSent) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PaymentFailed) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PaymentReceived) {
    navigationEvent.value = event
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PayToOpenRequestEvent) {
    navigationEvent.value = event
  }

  fun refreshPayments() {
    viewModelScope.launch(CoroutineExceptionHandler { _, e ->
      log.error("failed to retrieve payments: ", e)
    }) {
      service?.getPayments()?.also { payments.postValue(it) }
    }
  }
}

class StateLiveData(service: MutableLiveData<EclairNodeService?>): MediatorLiveData<KitState>() {
  private val log = LoggerFactory.getLogger(AppViewModel::class.java)
  private var serviceState: LiveData<KitState>? = null

  init {
    value = KitState.Disconnected
    addSource(service) { s ->
      if (s == null) {
        log.info("lost service, force state to Disconnected and remove source")
        serviceState?.let { removeSource(it) }
        serviceState = null
        value = KitState.Disconnected
      } else {
        log.info("service connected, now mirroring service's internal state")
        addSource(s.state) {
          value = it
        }
      }
    }
  }
}
