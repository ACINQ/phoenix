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

package fr.acinq.phoenix.settings

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.channel.`NORMAL$`
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSettingsMutualCloseBinding
import fr.acinq.phoenix.utils.AlertHelper
import fr.acinq.phoenix.utils.Converter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class MutualCloseFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentSettingsMutualCloseBinding
  private lateinit var model: MutualCloseViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsMutualCloseBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(MutualCloseViewModel::class.java)
    mBinding.model = model
    mBinding.actionBar.setSubtitle(Converter.html(getString(R.string.closechannels_mutual_instructions)))
  }

  override fun onStart() {
    super.onStart()
    getChannels()
    mBinding.mutualConfirmButton.setOnClickListener {
      AlertHelper.build(layoutInflater, null, R.string.closechannels_confirm_dialog_message)
        .setPositiveButton(R.string.btn_confirm) { _, _ -> doMutualClose() }
        .setNegativeButton(R.string.btn_cancel, null)
        .show()
    }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
  }

  private fun getChannels() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving list of channels: ", exception)
      model.state.value = PreChannelsCloseState.NO_CHANNELS
    }) {
      model.state.value = PreChannelsCloseState.CHECKING_CHANNELS
      val channels = appKit.getChannels(`NORMAL$`.`MODULE$`)
      if (channels.count() == 0) {
        model.state.value = PreChannelsCloseState.NO_CHANNELS
      } else {
        context?.let {
          val balance = appKit.nodeData.value?.balance ?: MilliSatoshi(0)
          mBinding.channelsState.text = Converter.html(getString(R.string.closechannels_channels_recap, channels.count(), Converter.printAmountPretty(balance, it, withUnit = true)))
        }
        model.state.value = PreChannelsCloseState.READY
      }
    }
  }

  private fun doMutualClose() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error in mutal close: ", exception)
      model.state.value = MutualCloseState.ERROR
      Handler().postDelayed({ model.state.value = PreChannelsCloseState.READY }, 2000)
    }) {
      model.state.value = MutualCloseState.IN_PROGRESS
      appKit.mutualCloseAllChannels(mBinding.mutualCloseAddressInput.text.toString())
      model.state.value = MutualCloseState.DONE
    }
  }
}

interface ChannelsCloseBaseState

enum class PreChannelsCloseState : ChannelsCloseBaseState {
  CHECKING_CHANNELS, NO_CHANNELS, READY
}

enum class MutualCloseState : ChannelsCloseBaseState {
  IN_PROGRESS, DONE, ERROR
}

class MutualCloseViewModel : ViewModel() {

  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<ChannelsCloseBaseState>()

  init {
    state.value = PreChannelsCloseState.CHECKING_CHANNELS
  }

}
