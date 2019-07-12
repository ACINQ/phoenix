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

package fr.acinq.eclair.phoenix.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSendBinding
import fr.acinq.eclair.phoenix.utils.customviews.CoinView

class SendFragment : BaseFragment() {

  private lateinit var mBinding: FragmentSendBinding
  private val args: SendFragmentArgs by navArgs()

  private lateinit var model: SendViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSendBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(SendViewModel::class.java)
    mBinding.model = model

    model.paymentRequest.observe(this, Observer {
      if (it != null && it.amount().isDefined) {
        mBinding.amount.setAmount(it.amount().get())
      }
    })

    model.checkAndSetPaymentRequest(args.paymentRequest)

    mBinding.amount.setAmountWatcher(object : CoinView.CoinViewWatcher() {
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        mBinding.amount.checkAmountEmpty()
      }
    })
  }

  override fun onStart() {
    super.onStart()
    appKit.nodeData.value?.let {
      mBinding.balanceValue.setAmount(it.balance)
    } ?: log.warn("balance is not available yet")

    mBinding.sendButton.setOnClickListener {
      model.paymentRequest.value?.let {
        val amount_opt = mBinding.amount.getAmount()
        if (amount_opt.isDefined) {
          appKit.sendPaymentRequest(mBinding.amount.getAmount().get(), it)
          findNavController().navigate(R.id.action_send_to_main)
        } else {
          log.info("empty amount!")
          model.state.value = SendState.ERROR_IN_AMOUNT
        }
      }
    }

    mBinding.cancelButton.setOnClickListener {
      findNavController().navigate(R.id.action_send_to_main)
    }
  }
}
