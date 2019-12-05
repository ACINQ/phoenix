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

package fr.acinq.phoenix.receive

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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.`BtcUnit$`
import fr.acinq.eclair.`MBtcUnit$`
import fr.acinq.eclair.`SatUnit$`
import fr.acinq.eclair.payment.PaymentReceived
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.NavGraphMainDirections
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentReceiveBinding
import fr.acinq.phoenix.paymentdetails.PaymentDetailsFragment
import fr.acinq.phoenix.utils.*
import fr.acinq.eclair.wire.SwapInResponse
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
  private lateinit var unitList: List<String>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activity?.onBackPressedDispatcher?.addCallback(this) {
      handleBackAction()
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
      unitList = listOf(`SatUnit$`.`MODULE$`.code(), `MBtcUnit$`.`MODULE$`.code(), `BtcUnit$`.`MODULE$`.code(), Prefs.getFiatCurrency(it))
      ArrayAdapter(it, android.R.layout.simple_spinner_item, unitList).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.amountUnit.adapter = adapter
      }
      val unit = Prefs.getCoinUnit(it)
      mBinding.amountUnit.setSelection(unitList.indexOf(unit.code()))
    }

    model.invoice.observe(viewLifecycleOwner, Observer {
      if (it != null) {
        model.generateQrCodeBitmap()
      }
    })

    model.bitmap.observe(viewLifecycleOwner, Observer { bitmap ->
      if (bitmap != null) {
        mBinding.qrImage.setImageBitmap(bitmap)
        model.invoice.value?.let {
          if (it.second != null) {
            model.state.value = SwapInState.DONE
          } else {
            model.state.value = PaymentGenerationState.DONE
          }
        }
      }
    })

    appKit.pendingSwapIns.observe(viewLifecycleOwner, Observer {
      model.invoice.value?.let { invoice ->
        // if user is swapping in and a payment is incoming on this address, move to main
        if (invoice.second != null && invoice.second != null && model.state.value == SwapInState.DONE) {
          val currentOnchainAddress = invoice.second
          if (it.keys.contains(currentOnchainAddress)) {
            findNavController().navigate(R.id.action_receive_to_main)
          }
        }
      }
    })
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }

    mBinding.amountValue.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) = Unit

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        refreshConversionDisplay()
      }
    })

    mBinding.amountUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) = Unit

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
      model.invoice.value?.let {
        val source = if (model.invoice.value!!.second != null) "bitcoin:${model.invoice.value!!.second}" else "lightning:${PaymentRequest.write(model.invoice.value!!.first)}"
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.receive_share_subject))
        shareIntent.putExtra(Intent.EXTRA_TEXT, source)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.receive_share_title)))
      }
    }

    mBinding.editButton.setOnClickListener {
      model.state.value = PaymentGenerationState.EDITING_REQUEST
    }

    mBinding.swapInButton.setOnClickListener {
      val swapInFee = 100 * (appKit.swapInSettings.value?.feePercent ?: Constants.DEFAULT_SWAP_IN_SETTINGS.feePercent)
      AlertHelper.build(layoutInflater, getString(R.string.receive_swap_in_disclaimer_title),
        Converter.html(getString(R.string.receive_swap_in_disclaimer_message, String.format("%.2f", swapInFee))))
        .setPositiveButton(R.string.utils_proceed) { _, _ -> generateSwapIn() }
        .setNegativeButton(R.string.btn_cancel, null)
        .show()
    }

    mBinding.actionBar.setOnBackAction(View.OnClickListener { handleBackAction() })

    if (model.state.value == PaymentGenerationState.INIT) {
      generatePaymentRequest()
    }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  private fun handleBackAction() {
    if (model.state.value == PaymentGenerationState.EDITING_REQUEST) {
      model.state.value = PaymentGenerationState.DONE
    } else {
      findNavController().navigate(R.id.action_receive_to_main)
    }
  }

  private fun copyInvoice() {
    try {
      val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val source = model.invoice.value!!.second ?: PaymentRequest.write(model.invoice.value!!.first)
      clipboard.primaryClip = ClipData.newPlainText("Payment request", source)
      Toast.makeText(activity!!.applicationContext, R.string.copied, Toast.LENGTH_SHORT).show()
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
      model.invoice.value = Pair(appKit.generatePaymentRequest(if (desc.isBlank()) getString(R.string.receive_default_desc) else desc, extractAmount()), null)
    }
  }

  private fun generateSwapIn() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when generating swap in: ", exception)
      model.state.value = SwapInState.ERROR
    }) {
      Wallet.hideKeyboard(context, mBinding.amountValue)
      model.state.value = SwapInState.IN_PROGRESS
      appKit.sendSwapIn()
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
  fun handleEvent(event: PaymentReceived) {
    model.invoice.value?.let {
      if (event.paymentHash() == it.first.paymentHash()) {
        val action = NavGraphMainDirections.globalActionAnyToPaymentDetails(PaymentDetailsFragment.INCOMING, event.paymentHash().toString(), fromEvent = true)
        findNavController().navigate(action)
      }
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleEvent(event: SwapInResponse) {
    model.invoice.value = model.invoice.value?.copy(second = event.bitcoinAddress())
  }
}
