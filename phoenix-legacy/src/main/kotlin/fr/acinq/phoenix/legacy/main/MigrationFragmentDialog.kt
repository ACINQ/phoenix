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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import fr.acinq.phoenix.legacy.AppViewModel
import fr.acinq.phoenix.legacy.databinding.FragmentMigrationBinding
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.MigrationResult
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
      app = ViewModelProvider(activity)[AppViewModel::class.java]
    } ?: dismiss()

    app.pendingSwapIns.observe(viewLifecycleOwner) {
      val migrationState = model.state.value
      if (it.isNotEmpty() && migrationState == MigrationScreenState.Ready) {
        log.info("there are ${it.size} pending swap-ins, disabling migration...")
        model.state.value = MigrationScreenState.Paused.PendingSwapIn
      }
    }

    val context = requireContext()
    isCancelable = false

    model.state.observe(viewLifecycleOwner) {
      when (it) {
        is MigrationScreenState.ReadyToClose -> {
          lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
            log.error("migration failed, error in mutal close: ", exception)
            model.state.value = MigrationScreenState.Failure.ClosingError
          }) {
            delay(500)
            val state = model.state.value
            if (state is MigrationScreenState.ReadyToClose) {
              model.state.value = MigrationScreenState.ClosingChannels(state.address)
              delay(500)
              log.info("(migration) closing channels to ${state.address}")
              app.requireService.mutualCloseAllChannels(it.address)
              log.info("(migration) channels successfully closed to ${state.address}")
              model.state.value = MigrationScreenState.ClosingChannels(state.address)
              delay(1000)
              PrefsDatastore.saveDataMigrationExpected(context, true)
              PrefsDatastore.saveMigrationResult(
                context, MigrationResult(
                  legacyNodeId = app.state.value?.getNodeId()?.toString()!!,
                  newNodeId = app.state.value?.getKmpNodeId().toString(),
                  address = state.address
                )
              )
              PrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.NotRequired)
            }
          }
        }
        else -> {
          log.info("migration state=$it")
        }
      }
    }
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.pausedButton.setOnClickListener { dismiss() }
    mBinding.dismissButton.setOnClickListener { dismiss() }
    mBinding.upgradeButton.setOnClickListener {
      if (model.state.value is MigrationScreenState.Ready) {
        model.state.value = MigrationScreenState.RequestingKmpSwapInAddress
        val swapInAddress = app.service?.state?.value?.getKmpSwapInAddress()
        if (swapInAddress == null) {
          model.state.value = MigrationScreenState.Failure.SwapInError
        } else {
          model.state.value = MigrationScreenState.ReadyToClose(swapInAddress)
        }
      }
    }
  }
}

sealed class MigrationScreenState {
  object Ready : MigrationScreenState()
  sealed class Paused : MigrationScreenState() {
    object PendingSwapIn: Paused()
  }
  object RequestingKmpSwapInAddress: MigrationScreenState()
  data class ReadyToClose(val address: String): MigrationScreenState()
  data class ClosingChannels(val address: String) : MigrationScreenState()
  data class ChannelsClosed(val address: String) : MigrationScreenState()
  sealed class Failure : MigrationScreenState() {
    object ClosingError : Failure()
    object SwapInError : Failure()
  }
}

class MigrationDialogViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<MigrationScreenState>(MigrationScreenState.Ready)
}
