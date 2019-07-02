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

package fr.acinq.eclair.phoenix.settings

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import fr.acinq.eclair.channel.`NORMAL$`
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsCloseAllChannelsBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch


class CloseAllChannelsFragment : BaseFragment() {

  private lateinit var mBinding: FragmentSettingsCloseAllChannelsBinding

  private lateinit var model: CloseAllChannelsViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsCloseAllChannelsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(CloseAllChannelsViewModel::class.java)
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    getChannels()
    mBinding.confirmButton.setOnClickListener { closeAllChannels() }
  }

  private fun getChannels() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when retrieving list of channels: ", exception)
      model.state.value = ClosingChannelsState.NO_CHANNELS
    }) {
      model.state.value = ClosingChannelsState.CHECKING_CHANNELS
      when (appKit.getChannels(`NORMAL$`.`MODULE$`).count()) {
        0 -> model.state.value = ClosingChannelsState.READY
        else -> model.state.value = ClosingChannelsState.READY
      }
    }
  }

  private fun closeAllChannels() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when closing all channels: ", exception)
      model.state.value = ClosingChannelsState.ERROR
      Handler().postDelayed({ model.state.value = ClosingChannelsState.READY }, 2000)
    }) {
      model.state.value = ClosingChannelsState.IN_PROGRESS
      appKit.closeAllChannels(mBinding.addressInput.text.toString())
      model.state.value = ClosingChannelsState.DONE
    }
  }
}
