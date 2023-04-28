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

package fr.acinq.phoenix.legacy.settings

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
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.FragmentSettingsForceCloseBinding
import fr.acinq.phoenix.legacy.utils.AlertHelper
import fr.acinq.phoenix.legacy.utils.Converter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class ForceCloseFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentSettingsForceCloseBinding
  private lateinit var model: ForceCloseViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsForceCloseBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(ForceCloseViewModel::class.java)
    mBinding.model = model
    mBinding.instructions.text = Converter.html(getString(R.string.legacy_closechannels_force_instructions))
    mBinding.destinationValue.text = app.state.value?.getFinalAddress() ?: getString(R.string.legacy_utils_unknown)
  }

  override fun onStart() {
    super.onStart()
    getChannels()
    mBinding.forceConfirmButton.setOnClickListener {
      AlertHelper.build(layoutInflater, null, R.string.legacy_closechannels_confirm_dialog_message)
        .setPositiveButton(R.string.legacy_btn_confirm) { _, _ -> doForceClose() }
        .setNegativeButton(R.string.legacy_btn_cancel, null)
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
      val channels = app.requireService.getChannels()
      if (channels.count() == 0) {
        model.state.value = PreChannelsCloseState.NO_CHANNELS
      } else {
        model.state.value = PreChannelsCloseState.READY
      }
    }
  }

  private fun doForceClose() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error in force close: ", exception)
      model.state.value = ForceCloseState.ERROR
      Handler().postDelayed({ model.state.value = PreChannelsCloseState.READY }, 2000)
    }) {
      model.state.value = ForceCloseState.IN_PROGRESS
      app.requireService.forceCloseAllChannels()
      model.state.value = ForceCloseState.DONE
    }
  }
}

enum class ForceCloseState : ChannelsCloseBaseState {
  IN_PROGRESS, DONE, ERROR
}

class ForceCloseViewModel : ViewModel() {

  val state = MutableLiveData<ChannelsCloseBaseState>()

  init {
    state.value = PreChannelsCloseState.CHECKING_CHANNELS
  }

}
