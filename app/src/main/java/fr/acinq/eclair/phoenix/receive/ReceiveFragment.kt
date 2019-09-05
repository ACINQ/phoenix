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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.addCallback
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.`BtcUnit$`
import fr.acinq.eclair.`MBtcUnit$`
import fr.acinq.eclair.`SatUnit$`
import fr.acinq.eclair.db.`IncomingPayment$`
import fr.acinq.eclair.db.`PaymentDirection$`
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.NavGraphMainDirections
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentReceiveBinding
import fr.acinq.eclair.phoenix.events.PaymentComplete
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.Prefs
import fr.acinq.eclair.phoenix.utils.Wallet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.Option

class ReceiveFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

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
    model = ViewModelProvider(this).get(ReceiveViewModel::class.java)
    mBinding.model = model

    context?.let {
      ArrayAdapter(it,
        android.R.layout.simple_spinner_item,
        listOf(`SatUnit$`.`MODULE$`.code(), `MBtcUnit$`.`MODULE$`.code(), `BtcUnit$`.`MODULE$`.code(), Prefs.getFiatCurrency(it))).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.amountUnit.adapter = adapter
      }
    }

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
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }

    mBinding.amountValue.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) {
      }

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
      }

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        refreshConversionDisplay()
      }
    })
    mBinding.amountUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) {
      }

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        refreshConversionDisplay()
      }
    }
    mBinding.generateButton.setOnClickListener {
      generatePaymentRequest()
    }
    mBinding.copyButton.setOnClickListener {
      copyInvoice()
    }
    mBinding.qrImage.setOnClickListener {
      copyInvoice()
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
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().navigate(R.id.action_receive_to_main) })
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  private fun copyInvoice() {
    try {
      val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.primaryClip = ClipData.newPlainText("Payment request", PaymentRequest.write(model.paymentRequest.value))
      Toast.makeText(activity!!.applicationContext, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      log.error("failed to copy: ${e.localizedMessage}")
    }
  }

  // payment request is generated in appkit view model and updates the receive view model
  private fun generatePaymentRequest() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when generating payment request: ", exception)
      model.state.value = PaymentGenerationState.ERROR
    }) {
      Wallet.hideKeyboard(context, mBinding.amountValue)
      model.state.value = PaymentGenerationState.IN_PROGRESS
      val desc = mBinding.descValue.text.toString()
      model.paymentRequest.value = appKit.generatePaymentRequest(if (desc.isBlank()) getString(R.string.receive_default_desc) else desc, extractAmount())
    }
  }

  private fun refreshConversionDisplay() {
    context?.let {
      try {
        val amount = extractAmount()
        val unit = mBinding.amountUnit.selectedItem.toString()
        if (unit == Prefs.getFiatCurrency(it)) {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printAmountPretty(amount.get(), it, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(it, amount.get(), withUnit = true))
        }
      } catch (e: Exception) {
        log.info("could not extract amount: ${e.message}")
        mBinding.amountConverted.text = ""
      }
    }
  }

  private fun extractAmount(): Option<MilliSatoshi> {
    val unit = mBinding.amountUnit.selectedItem.toString()
    val amount = mBinding.amountValue.text.toString()
    return if (unit == Prefs.getFiatCurrency(context!!)) {
      Option.apply(Converter.convertFiatToMsat(context!!, amount))
    } else {
      Converter.string2Msat_opt(amount, unit)
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: fr.acinq.eclair.phoenix.events.PaymentEvent) {
    model.paymentRequest.value?.let {
      if (event is PaymentComplete) {
        if (event.direction == `PaymentDirection$`.`MODULE$`.INCOMING() && event.identifier == it.paymentHash().toString()) {
          val action = NavGraphMainDirections.globalActionAnyToPaymentDetails(event.direction.toString(), event.identifier, fromEvent = true)
          findNavController().navigate(action)
        }
      }
    }
  }
}
