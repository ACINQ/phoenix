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

package fr.acinq.eclair.phoenix.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MilliSatoshi
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.databinding.FragmentReceiveBinding
import fr.acinq.eclair.phoenix.utils.Wallet
import fr.acinq.eclair.wire.`PayToOpenRequest$`
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

class ReceiveFragment : BaseFragment() {

  private lateinit var mBinding: FragmentReceiveBinding

  private lateinit var model: ReceiveViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentReceiveBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(ReceiveViewModel::class.java)
    generatePaymentRequest()
    mBinding.model = model
    model.paymentRequest.observe(viewLifecycleOwner, Observer {
      log.info("a new payment_request=$it has been generated")
      if (it != null) {
        model.generateQrCode()
        log.info("fired and forgot QR bitmap update")
      }
    })
    model.bitmap.observe(viewLifecycleOwner, Observer { bitmap ->
      if (bitmap != null) {
        mBinding.qrImage.setImageBitmap(bitmap)
//        val payToOpen = `PayToOpenRequest$`.`MODULE$`.apply(Wallet.getChainHash(), 2000, 1500, 500, ByteVector32.Zeroes())
//        EventBus.getDefault().post(payToOpen)
      }
    })
  }

  override fun onStart() {
    super.onStart()
    mBinding.qrImage.setOnClickListener {
      try {
        val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText("Payment request", PaymentRequest.write(model.paymentRequest.value))
        Toast.makeText(activity!!.applicationContext, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        log.error("failed to copy: ${e.localizedMessage}")
      }
    }
  }

  private fun generatePaymentRequest() {
    lifecycleScope.launch(CoroutineExceptionHandler{ _, exception ->
      log.error("error when generating payment request: $exception")
    }) {
      log.info("thread ${Thread.currentThread().name}")
      model.paymentRequest.value = appKit.generatePaymentRequest("toto", MilliSatoshi(110000 * 1000))
    }
  }

}
