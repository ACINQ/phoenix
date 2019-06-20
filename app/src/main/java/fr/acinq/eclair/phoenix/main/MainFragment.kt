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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import fr.acinq.bitcoin.MilliSatoshi
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentMainBinding
import fr.acinq.eclair.phoenix.utils.Prefs

class MainFragment : BaseFragment() {

  private lateinit var viewModel: MainViewModel
  private lateinit var mBinding: FragmentMainBinding
  private lateinit var paymentsAdapter: PaymentsAdapter
  private lateinit var paymentsManager: RecyclerView.LayoutManager

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentMainBinding.inflate(inflater, container, false)
    paymentsManager = LinearLayoutManager(context)
    paymentsAdapter = PaymentsAdapter(ArrayList())
    mBinding.paymentList.apply {
      setHasFixedSize(true)
      layoutManager = paymentsManager
      adapter = paymentsAdapter
    }

    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
  }

  override fun onStart() {
    super.onStart()
    mBinding.receiveButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_receive) }
    mBinding.sendButton.setOnClickListener { findNavController().navigate(R.id.action_main_to_init_send) }

    mBinding.balance.setAmount(MilliSatoshi(14354357))
    val payments = ArrayList<Payment>()
    payments.add(Payment(MilliSatoshi(321), "Lorem ipsum dolor sit amet"))
    payments.add(Payment(MilliSatoshi(65431321), "efficitur nulla a, placera"))
    payments.add(Payment(MilliSatoshi(9852), "Orci varius natoque penatibus"))
    payments.add(Payment(MilliSatoshi(54357), "Mauris eget arcu vel mi imperdiet"))
    payments.add(Payment(MilliSatoshi(9852), "Phasellus et nisl quis ligula"))
    payments.add(Payment(MilliSatoshi(132168735437), "Mauris arcu lorem"))
    payments.add(Payment(MilliSatoshi(2131), "Nunc nec ex vel orci"))
    payments.add(Payment(MilliSatoshi(68354357), "Suspendisse potenti"))
    payments.add(Payment(MilliSatoshi(357315321), "Proin pulvinar malesuada efficitur"))
    payments.add(Payment(MilliSatoshi(6873541), "Donec id ante mauris"))
    context?.let {
      paymentsAdapter.update(payments, "usd", Prefs.prefCoin(context!!), displayAmountAsFiat = false)
    }
  }

}
