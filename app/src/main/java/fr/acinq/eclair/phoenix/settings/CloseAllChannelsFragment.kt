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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
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

    model.state.observe(this, Observer {
      when (it) {
        ClosingChannelsState.READY -> mBinding.message.text = getString(R.string.closeall_message_ready)
        ClosingChannelsState.IN_PROGRESS -> mBinding.message.text = getString(R.string.closeall_message_in_progress)
        ClosingChannelsState.ERROR -> mBinding.message.text = getString(R.string.closeall_message_error)
        ClosingChannelsState.DONE -> mBinding.message.text = getString(R.string.closeall_message_done)
      }
    })
  }

  override fun onStart() {
    super.onStart()
    mBinding.confirmButton.setOnClickListener { }
  }

  private fun closeAllChannels() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when closing all channels: ", exception)
      model.state.value = ClosingChannelsState.ERROR
    }) {
      model.state.value = ClosingChannelsState.IN_PROGRESS
      appKit.closeAllChannels("whatever")
      model.state.value = ClosingChannelsState.DONE
    }
  }
}
