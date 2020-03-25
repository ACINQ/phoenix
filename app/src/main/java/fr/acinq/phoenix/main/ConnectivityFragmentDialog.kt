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

package fr.acinq.phoenix.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.AppViewModel
import fr.acinq.phoenix.NetworkInfo
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentConnectivityBinding
import fr.acinq.phoenix.utils.Prefs
import fr.acinq.phoenix.utils.ThemeHelper
import fr.acinq.phoenix.utils.tor.TorConnectionStatus

class ConnectivityFragmentDialog : DialogFragment() {

  private lateinit var mBinding: FragmentConnectivityBinding
  private lateinit var app: AppViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentConnectivityBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.let {
      app = ViewModelProvider(it).get(AppViewModel::class.java)
    } ?: dismiss()
    app.networkInfo.observe(viewLifecycleOwner, Observer {
      context?.let { ctx ->
        val isNetworkOk = handleNetworkConnection(ctx, it).and(handleElectrumConnection(ctx, it)).and(handleTorConnection(ctx, it))
        mBinding.summary.visibility = if (isNetworkOk) View.GONE else View.VISIBLE
      }
    })
  }

  override fun onStart() {
    super.onStart()
    mBinding.networkConnLabel.setOnClickListener { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
    mBinding.electrumConnLabel.setOnClickListener { findNavController().navigate(R.id.global_action_any_to_electrum) }
    mBinding.torConnLabel.setOnClickListener { findNavController().navigate(R.id.global_action_any_to_tor) }
    mBinding.close.setOnClickListener { findNavController().popBackStack() }
  }

  private fun handleNetworkConnection(context: Context, ni: NetworkInfo): Boolean {
    mBinding.networkConnState.text = context.getString(if (ni.networkConnected) R.string.conndialog_ok else R.string.conndialog_not_ok)
    mBinding.networkConnLabel.setIconColor(ThemeHelper.color(context, if (ni.networkConnected) R.attr.positiveColor else R.attr.negativeColor))
    return ni.networkConnected
  }

  private fun handleElectrumConnection(context: Context, ni: NetworkInfo): Boolean {
    mBinding.electrumConnState.text = context.getString(if (ni.electrumServer != null) R.string.conndialog_ok else R.string.conndialog_not_ok)
    mBinding.electrumConnLabel.setIconColor(ThemeHelper.color(context, if (ni.electrumServer != null) R.attr.positiveColor else R.attr.negativeColor))
    return ni.electrumServer != null
  }

  private fun handleTorConnection(context: Context, ni: NetworkInfo): Boolean {
    return if (Prefs.isTorEnabled(context)) {
      mBinding.torConnLabel.visibility = View.VISIBLE
      mBinding.torConnState.visibility = View.VISIBLE
      mBinding.torConnSep.visibility = View.VISIBLE
      if (ni.torConnections.isNullOrEmpty() || !ni.torConnections.values.contains(TorConnectionStatus.CONNECTED)) {
        mBinding.torConnState.text = context.getString(R.string.conndialog_not_ok)
        mBinding.torConnLabel.setIconColor(ThemeHelper.color(context, R.attr.negativeColor))
        false
      } else {
        mBinding.torConnState.text = context.getString(R.string.conndialog_ok)
        mBinding.torConnLabel.setIconColor(ThemeHelper.color(context, R.attr.positiveColor))
        true
      }
    } else {
      mBinding.torConnLabel.visibility = View.GONE
      mBinding.torConnState.visibility = View.GONE
      mBinding.torConnSep.visibility = View.GONE
      true
    }
  }
}
