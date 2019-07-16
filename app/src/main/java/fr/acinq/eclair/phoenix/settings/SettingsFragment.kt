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
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSettingsBinding


class SettingsFragment : BaseFragment() {

  private lateinit var mBinding: FragmentSettingsBinding

  private lateinit var model: SettingsViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(SettingsViewModel::class.java)
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.closeChannelsButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_close_all_channels) }
    mBinding.listAllChannelsButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_list_channels) }
    mBinding.shutdownButton.setOnClickListener { appKit.shutdown() }
    mBinding.backButton.setOnClickListener { findNavController().popBackStack() }
  }
}
