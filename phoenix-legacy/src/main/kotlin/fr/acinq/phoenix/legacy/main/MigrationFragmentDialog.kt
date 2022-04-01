/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.legacy.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import fr.acinq.bitcoin.scala.Crypto
import fr.acinq.eclair.wire.PhoenixAndroidLegacyMigrateResponse
import fr.acinq.eclair.wire.SwapInResponse
import fr.acinq.phoenix.legacy.AppViewModel
import fr.acinq.phoenix.legacy.databinding.FragmentMigrationBinding
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.MigrationResult
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MigrationFragmentDialog : DialogFragment() {
  val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentMigrationBinding
  private lateinit var app: AppViewModel
  private lateinit var model: MigrationDialogViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentMigrationBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(MigrationDialogViewModel::class.java)
    activity?.let { activity ->
      app = ViewModelProvider(activity).get(AppViewModel::class.java)
    } ?: dismiss()



    model.state.observe(viewLifecycleOwner) {
      log.info("migration state=>$it")
      when (it) {
        is MigrationScreenState.Acked -> {
          lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
            log.error("migration failed, error when requesting swap-in address: ", exception)
            model.state.value = MigrationScreenState.Failure.SwapInError
          }) {
            delay(500)
            val state = model.state.value
            if (state is MigrationScreenState.Acked) {
              model.state.value = MigrationScreenState.RequestingSwapInAddress(state.newNodeId)
              delay(500)
              app.requireService.requestSwapIn()
            }
          }
        }
        is MigrationScreenState.ReceivedSwapInAddress -> {
          lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
            log.error("migration failed, error in mutal close: ", exception)
            model.state.value = MigrationScreenState.Failure.ClosingError
          }) {
            delay(500)
            val context = requireContext()
            val state = model.state.value
            if (state is MigrationScreenState.ReceivedSwapInAddress) {
              val newNodeId = state.newNodeId
              model.state.value = MigrationScreenState.RequestingChannelsClosing(state.newNodeId, it.address)
              delay(500)
              log.info("closing channels to ${it.address} for migration to new wallet")
              app.requireService.mutualCloseAllChannels(it.address)
              log.info("channels closing completed!")
              model.state.value = MigrationScreenState.CompletedChannelsClosing(newNodeId, it.address)
              PrefsDatastore.saveMigrationResult(context, MigrationResult(
                legacyNodeId = app.state.value?.getNodeId()?.toString()!!,
                newNodeId = newNodeId.toString(),
                address = it.address
              ))
              PrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.NotRequired)
            }
          }
        }
        is MigrationScreenState.RequestingChannelsClosing -> {
          log.info("closing...")
        }
        is MigrationScreenState.CompletedChannelsClosing -> {
          log.info("closed!")
        }
        else -> {}
      }
    }
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
    mBinding.upgradeButton.setOnClickListener {
      if (model.state.value is MigrationScreenState.Ready) {
        model.state.value = MigrationScreenState.Notifying
        app.service?.sendLegacyMigrationSignal()
      }
    }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: PhoenixAndroidLegacyMigrateResponse) {
    log.info("peer acked migration request to nodeid=${event.newNodeId()}")
    model.state.value = MigrationScreenState.Acked(event.newNodeId())
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: SwapInResponse) {
    log.info("received swap-in response with address=${event.bitcoinAddress()}")
    val state = model.state.value
    if (state is MigrationScreenState.RequestingSwapInAddress) {
      model.state.value = MigrationScreenState.ReceivedSwapInAddress(state.newNodeId, event.bitcoinAddress())
    }
  }

}

sealed class MigrationScreenState {
  object Ready: MigrationScreenState()
  object Notifying: MigrationScreenState()
  data class Acked(val newNodeId: Crypto.PublicKey): MigrationScreenState()
  data class RequestingSwapInAddress(val newNodeId: Crypto.PublicKey): MigrationScreenState()
  data class ReceivedSwapInAddress(val newNodeId: Crypto.PublicKey, val address: String): MigrationScreenState()
  data class RequestingChannelsClosing(val newNodeId: Crypto.PublicKey, val address: String): MigrationScreenState()
  data class CompletedChannelsClosing(val newNodeId: Crypto.PublicKey, val address: String): MigrationScreenState()
  sealed class Failure: MigrationScreenState() {
    object ClosingError: Failure()
    object SwapInError: Failure()
  }
}

class MigrationDialogViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<MigrationScreenState>(MigrationScreenState.Ready)

  fun closeChannels(address: String) {
    viewModelScope.launch {

    }
  }
}
