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

package fr.acinq.eclair.phoenix.main

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentMainBinding
import fr.acinq.eclair.phoenix.utils.Prefs

class MainFragment : BaseFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

  private lateinit var model: MainViewModel
  private lateinit var mBinding: FragmentMainBinding

  private lateinit var paymentsAdapter: PaymentsAdapter
  private lateinit var paymentsManager: RecyclerView.LayoutManager

  private lateinit var notificationsAdapter: NotificationsAdapter
  private lateinit var notificationsManager: RecyclerView.LayoutManager

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentMainBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this

    // init payment recycler view
    paymentsManager = LinearLayoutManager(context)
    paymentsAdapter = PaymentsAdapter(ArrayList())
    mBinding.paymentList.apply {
      setHasFixedSize(true)
      layoutManager = paymentsManager
      adapter = paymentsAdapter
    }

    // init notification recycler view
    notificationsManager = LinearLayoutManager(context)
    notificationsAdapter = NotificationsAdapter(HashSet())
    mBinding.notificationList.apply {
      setHasFixedSize(true)
      layoutManager = notificationsManager
      adapter = notificationsAdapter
    }

    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(MainViewModel::class.java)
    appKit.nodeData.observe(viewLifecycleOwner, Observer {
      mBinding.balance.setAmount(it.balance)
    })
    appKit.payments.observe(viewLifecycleOwner, Observer {
      paymentsAdapter.update(it, "usd", Prefs.getCoin(context!!), displayAmountAsFiat = false)
    })
    model.notifications.observe(viewLifecycleOwner, Observer {
      notificationsAdapter.update(it)
    })
  }

  override fun onStart() {
    super.onStart()
    appKit.refreshPaymentList()
    context?.let { model.updateNotifications(it) }

    mBinding.settingsButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_settings) }
    mBinding.receiveButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_receive) }
    mBinding.sendButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_init_send) }
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    context?.let { model.updateNotifications(it) }
  }

}
