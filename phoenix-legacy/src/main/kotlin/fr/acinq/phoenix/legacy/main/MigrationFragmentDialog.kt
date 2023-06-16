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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.scala.ByteVector32
import fr.acinq.eclair.channel.*
import fr.acinq.phoenix.legacy.AppViewModel
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.background.EclairNodeService
import fr.acinq.phoenix.legacy.background.KitState.Bootstrap.Init.getKmpSwapInAddress
import fr.acinq.phoenix.legacy.databinding.FragmentMigrationBinding
import fr.acinq.phoenix.legacy.utils.LegacyAppStatus
import fr.acinq.phoenix.legacy.utils.MigrationResult
import fr.acinq.phoenix.legacy.utils.PrefsDatastore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bouncycastle.util.encoders.Hex
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.util.Either
import scala.util.Left
import scodec.bits.ByteVector

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

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    model = ViewModelProvider(this)[MigrationDialogViewModel::class.java]
    activity?.let { activity ->
      app = ViewModelProvider(activity)[AppViewModel::class.java]
    } ?: dismiss()

    isCancelable = false

    app.networkInfo.observe(viewLifecycleOwner) {
      model.isConnected.value = it != null && it.lightningConnected && it.electrumServer != null
    }

    app.pendingSwapIns.observe(viewLifecycleOwner) {
      log.info("(migration) ${it.size} pending swap-ins...")
      model.hasPendingSwapIn.value = it.isNotEmpty()
    }

    model.state.observe(viewLifecycleOwner) {
      val migrationState = model.state.value
      val context = requireContext()
      when (migrationState) {
        is MigrationScreenState.Processing.RequestingKmpSwapInAddress -> {
          mBinding.processingDetails.text = context.getString(R.string.legacy_migration_swap_address)
        }
        is MigrationScreenState.Processing.ListingChannelsToClose -> {
          mBinding.processingDetails.text = context.getString(R.string.legacy_migration_listing_channels)
        }
        is MigrationScreenState.Processing.ClosingChannels -> {
          mBinding.processingDetails.text = context.getString(R.string.legacy_migration_closing_channels, migrationState.channelsClosed.size, migrationState.allChannels.size)
        }
        is MigrationScreenState.Processing.MonitoringChannelsPublication -> {
          mBinding.processingDetails.text = context.getString(R.string.legacy_migration_monitoring_channels_publish, migrationState.channelsPublished.size, migrationState.channels.size)
        }
        else -> {}
      }
      log.info("(migration) state=$it")
    }

    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.pausedButton.setOnClickListener { dismiss() }
    mBinding.failureDismissButton.setOnClickListener { dismiss() }
    mBinding.dismissButton.setOnClickListener { dismiss() }
    mBinding.upgradeButton.setOnClickListener {
      if (model.isConnected.value == true) {
        model.startMigration(
          context = requireContext(),
          service = app.requireService,
          legacyNodeId = app.state.value?.getNodeId()?.toString()!!,
          kmpNodeId = app.state.value?.getKmpNodeId()?.toString()!!,
        )
      }
    }
  }
}

sealed class MigrationScreenState {
  object Ready : MigrationScreenState()

  sealed class Processing : MigrationScreenState() {
    object RequestingKmpSwapInAddress: Processing()
    data class ListingChannelsToClose(val address: String): Processing()
    data class ClosingChannels(val address: String, val allChannels: Set<ByteVector32>, val channelsClosed: Set<ByteVector32>) : Processing()
    data class MonitoringChannelsPublication(val address: String, val channels: Set<ByteVector32>, val channelsPublished: Set<ByteVector32>) : Processing()
  }

  data class Complete(val address: String, val channels: Set<ByteVector32>) : MigrationScreenState()

  sealed class Paused : MigrationScreenState() {
    object PendingSwapIn: Paused()
    object Disconnected: Paused()
    object ChannelsInForceClose: Paused()
    object ChannelsBeingCreated: Paused()
  }

  sealed class Failure : MigrationScreenState() {
    object GenericError: Failure()
    data class ClosingError(val channelsRemainingCount: Int) : Failure()
    object CannotGetSwapInAddress : Failure()
  }
}

class MigrationDialogViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<MigrationScreenState>(MigrationScreenState.Ready)

  val isConnected = MutableLiveData(false)
  val hasPendingSwapIn = MutableLiveData(false)

  private inner class MigrationException(val channelsRemainingCount: Int) : RuntimeException("$channelsRemainingCount channels remaining")

  fun startMigration(
    context: Context,
    service: EclairNodeService,
    legacyNodeId: String,
    kmpNodeId: String,
  ) {
    val migrationState = state.value
    if (migrationState !is MigrationScreenState.Ready) {
      log.info("(migration) ignore start migration request in state=$migrationState")
    } else {
      state.value = MigrationScreenState.Processing.RequestingKmpSwapInAddress
      viewModelScope.launch(CoroutineExceptionHandler { _, exception ->
        log.error("(migration) migration failed: ", exception)
        state.value = when (exception) {
          is MigrationException -> MigrationScreenState.Failure.ClosingError(exception.channelsRemainingCount)
          else -> MigrationScreenState.Failure.GenericError
        }
      }) {

        if (hasPendingSwapIn.value == true) {
          state.value = MigrationScreenState.Paused.PendingSwapIn
          return@launch
        }

        // compute the multi-sig swap-in address used by the new application
        val swapInAddress = service.state.value?.kit()?.getKmpSwapInAddress()
        if (swapInAddress == null) {
          state.value = MigrationScreenState.Failure.CannotGetSwapInAddress
        } else {
          state.value = MigrationScreenState.Processing.ListingChannelsToClose(swapInAddress)
          delay(500)
          log.info("(migration) closing channels to $swapInAddress")

          // list all the channels and check their state
          val allChannels = service.getChannels().toList()
          if (hasChannelsBeingCreated(allChannels)) {
            state.value = MigrationScreenState.Paused.ChannelsBeingCreated
            return@launch
          } else if (hasChannelsInForceClose(allChannels)) {
            state.value = MigrationScreenState.Paused.ChannelsInForceClose
            return@launch
          }

          // only close the channels in normal state
          val channels = allChannels.filter {
            it.state() is `NORMAL$`
          }.map {
            it.channelId()
          }.toSet()

          state.value = MigrationScreenState.Processing.ClosingChannels(swapInAddress, channels, emptySet())

          // loop closing the channels one by one to prevent herd effect
          val channelsClosingStatusMap = channels.associateWith { false }.toMutableMap()
          channels.forEachIndexed { index, channelId ->
            val res = service.migrateChannels(swapInAddress, listOf(channelId))
             val successfullyMigrated = res.filter { (_, result) -> result.isRight }
            val failedToMigrate = res.filter { (_, result) -> result.isLeft }
            if (failedToMigrate.isNotEmpty()) {
              log.info("(migration) failed to close channel=$channelId: ${res.values}")
              throw MigrationException(channelsRemainingCount = channelsClosingStatusMap.filter { !it.value }.size)
            } else {
              log.info("(migration) successfully closed channel=$channelId")
              successfullyMigrated.forEach {
                channelsClosingStatusMap[it.key.left().get()] = true
                state.value = MigrationScreenState.Processing.ClosingChannels(swapInAddress, channels, channelsClosingStatusMap.filter { it.value }.keys)
              }
            }
            delay(200)
          }

          // check that all channels were correctly closed
          val channelsClosed = channelsClosingStatusMap.filter { it.value }.keys
          if (channelsClosed.size != channels.size) {
            val remainingNotClosed = channelsClosingStatusMap.filter { !it.value }
            throw MigrationException(channelsRemainingCount = remainingNotClosed.size)
          } else {
            state.value = MigrationScreenState.Processing.MonitoringChannelsPublication(swapInAddress, channelsClosed, emptySet())
          }

          // wait for the closing transaction to be published for each channel
          // the verification is done every 3 seconds
          val channelsPublicationStatusMap = channelsClosed.associateWith { false }.toMutableMap()
          while (channelsPublicationStatusMap.any { !it.value }) {
            val notClosedChannels = channelsPublicationStatusMap.filter { !it.value }.keys
            log.info("(migration) checking mutual-close-publish status for ${notClosedChannels.size} channels...")
            notClosedChannels.forEach {
              val channelDetails = service.getChannel(it)
              val data = channelDetails?.data()
              when {
                data == null -> {
                  log.debug("(migration) could not get publication status for channel=$it")
                }
                data is DATA_CLOSING && !data.mutualClosePublished().isEmpty -> {
                  log.info("(migration) mutual-close published for channel=$it")
                  channelsPublicationStatusMap[it] = true
                  state.value = MigrationScreenState.Processing.MonitoringChannelsPublication(swapInAddress, channelsClosed, channelsPublicationStatusMap.filter { it.value }.keys)
                }
                else -> {
                  log.debug("(migration) mutual-close NOT published yet for channel=$it in state=${channelDetails.state()}")
                }
              }
            }
            log.info("(migration) ${channelsPublicationStatusMap.filter { it.value }.size}/${channelsPublicationStatusMap.keys.size} closing published")
            delay(3000)
          }

          // migration is successful, update state
          log.info("(migration) ${channelsPublicationStatusMap.size} channels have been closed to $swapInAddress")
          state.value = MigrationScreenState.Complete(swapInAddress, channelsPublicationStatusMap.keys)

          // pause then update preferences to switch to the new app
          delay(3_000)
          PrefsDatastore.saveDataMigrationExpected(context, true)
          PrefsDatastore.saveMigrationResult(
            context, MigrationResult(
              legacyNodeId = legacyNodeId,
              newNodeId = kmpNodeId,
              address = swapInAddress
            )
          )
          PrefsDatastore.saveStartLegacyApp(context, LegacyAppStatus.NotRequired)
        }
      }
    }
  }

  private fun hasChannelsInForceClose(channels: List<RES_GETINFO>): Boolean {
    return channels.any {
      when (val data = it.data()) {
        is DATA_CLOSING -> {
          data.remoteCommitPublished().isDefined
            || !data.revokedCommitPublished().isEmpty
            || !data.customRemoteCommitPublished().isEmpty
            || data.futureRemoteCommitPublished().isDefined
            || data.localCommitPublished().isDefined
            || data.nextRemoteCommitPublished().isDefined
        }
        else -> false
      }
    }
  }

  private fun hasChannelsBeingCreated(channels: List<RES_GETINFO>): Boolean {
    return channels.any {
      when (it.state()) {
        is `WAIT_FOR_ACCEPT_CHANNEL$`, is `WAIT_FOR_FUNDING_CONFIRMED$`,
        is `WAIT_FOR_FUNDING_CREATED$`,  is `WAIT_FOR_FUNDING_INTERNAL$`,
        is `WAIT_FOR_FUNDING_LOCKED$`, is `WAIT_FOR_FUNDING_SIGNED$`,
        is `WAIT_FOR_INIT_INTERNAL$`, is `WAIT_FOR_OPEN_CHANNEL$`,
        is `WAIT_FOR_REMOTE_PUBLISH_FUTURE_COMMITMENT$` -> true
        else -> false
      }
    }
  }
}
