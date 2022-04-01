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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.background.KitState
import fr.acinq.phoenix.legacy.databinding.FragmentSettingsBinding
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class SettingsFragment : BaseFragment(stayIfNotStarted = true) {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSettingsBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSettingsBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun handleAppState(state: KitState) {
    super.handleAppState(state)
    val isStarted = state is KitState.Started
    mBinding.securityTitle.visibility = if (isStarted) View.VISIBLE else View.GONE
    mBinding.displaySeedButton.visibility = if (isStarted) View.VISIBLE else View.GONE
    mBinding.accessControlButton.visibility = if (isStarted) View.VISIBLE else View.GONE
    mBinding.listAllChannelsButton.visibility = if (isStarted) View.VISIBLE else View.GONE
    mBinding.mutualCloseButton.visibility = if (isStarted) View.VISIBLE else View.GONE
    mBinding.forceCloseButton.visibility = if (isStarted) View.VISIBLE else View.GONE
    mBinding.paymentSettingsButton.visibility = if (isStarted) View.VISIBLE else View.GONE
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
    mBinding.prefsDisplayButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_prefs_display) }
    mBinding.electrumButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_electrum) }
    mBinding.mutualCloseButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_mutual_close) }
    mBinding.forceCloseButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_force_close) }
    mBinding.displaySeedButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_display_seed) }
    mBinding.accessControlButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_access_control) }
    mBinding.listAllChannelsButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_list_channels) }
    mBinding.logsButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_logs) }
//    mBinding.feesButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_fees) }
    mBinding.torButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_tor) }
    mBinding.aboutButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_about) }
    mBinding.paymentSettingsButton.setOnClickListener { findNavController().navigate(R.id.action_settings_to_payment_settings) }
  }

}
