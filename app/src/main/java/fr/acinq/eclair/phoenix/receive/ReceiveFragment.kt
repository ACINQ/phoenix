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
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.bitcoin.MilliSatoshi
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentReceiveBinding
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.customviews.CoinView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import scala.Option
import java.util.*


class ReceiveFragment : BaseFragment() {

  private lateinit var mBinding: FragmentReceiveBinding

  private lateinit var model: ReceiveViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val callback = requireActivity().onBackPressedDispatcher.addCallback(this) {
      findNavController().navigate(R.id.action_receive_to_main)
    }
  }


  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentReceiveBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(ReceiveViewModel::class.java)
    mBinding.model = model

    mBinding.amountValue.setAmountWatcher(object : CoinView.CoinViewWatcher() {
      private var throttle = Timer()
      private val DELAY: Long = 350

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        mBinding.amountValue.handleEmptyAmountIfEditable()
        model.amountInputState.value = AmountTypingState.TYPING
        throttle.cancel()
        throttle = Timer()
        throttle.schedule(object : TimerTask() {
          override fun run() {
            context?.let {
              try {
                generatePaymentRequest(Converter.string2Msat_opt(s.toString(), it))
              } catch (e: Exception) {
                log.error("amount could not be converted to msat: ${e.message}")
              } finally {
                model.amountInputState.postValue(AmountTypingState.DONE)
              }
            }
          }
        }, DELAY)
      }
    })

    model.paymentRequest.observe(viewLifecycleOwner, Observer {
      if (it != null) {
        model.state.value = PaymentGenerationState.BUILDING_BITMAP
        model.generateQrCodeBitmap()
      }
    })
    model.bitmap.observe(viewLifecycleOwner, Observer { bitmap ->
      if (bitmap != null) {
        mBinding.qrImage.setImageBitmap(bitmap)
        model.state.value = PaymentGenerationState.DONE
      }
    })

    generatePaymentRequest(Option.apply(null))
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }
    mBinding.qrImage.setOnClickListener {
      try {
        val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText("Payment request", PaymentRequest.write(model.paymentRequest.value))
        Toast.makeText(activity!!.applicationContext, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        log.error("failed to copy: ${e.localizedMessage}")
      }
    }
    mBinding.shareButton.setOnClickListener {
      model.paymentRequest.value?.let {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.receive_share_subject))
        shareIntent.putExtra(Intent.EXTRA_TEXT, "lightning:${PaymentRequest.write(it)}")
        startActivity(Intent.createChooser(shareIntent, getString(R.string.receive_share_title)))
      }
    }
    mBinding.backButton.setOnClickListener { findNavController().navigate(R.id.action_receive_to_main) }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  // payment request is generated in appkit view model and updates the receive view model
  private fun generatePaymentRequest(amount_opt: Option<MilliSatoshi>) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when generating payment request: ", exception)
      model.state.value = PaymentGenerationState.ERROR
    }) {
      model.state.value = PaymentGenerationState.IN_PROGRESS
      model.paymentRequest.value = appKit.generatePaymentRequest("Phoenix payment", amount_opt)
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: fr.acinq.eclair.phoenix.events.PaymentEvent) {
    model.paymentRequest.value?.let {
      if (event.paymentHash == it.paymentHash()) {
        findNavController().navigate(R.id.action_receive_to_main)
      }
    }
  }
}
