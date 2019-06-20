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
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.navArgs
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseDialogFragment
import fr.acinq.eclair.phoenix.databinding.FragmentSendBinding

class SendFragment : BaseDialogFragment() {

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
    log.info("hello send fragment")
    model = ViewModelProviders.of(this).get(SendViewModel::class.java)
    mBinding.model = model
    val paymentRequest = PaymentRequest.read(args.paymentRequest)
    model.paymentRequest.value = paymentRequest
    if (paymentRequest.amount().isDefined) {
      mBinding.amount.setAmount(paymentRequest.amount().get())
    }
  }

  override fun onStart() {
    super.onStart()
    mBinding.sendButton.setOnClickListener {
      val pr: PaymentRequest? = model.paymentRequest.value
      pr?.let {
        appKit.sendPaymentRequest(pr)
      }
    }
  }
}
