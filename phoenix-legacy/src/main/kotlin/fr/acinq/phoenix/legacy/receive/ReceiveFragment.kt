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

package fr.acinq.phoenix.legacy.receive

import android.content.*
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import fr.acinq.bitcoin.scala.Satoshi
import fr.acinq.eclair.*
import fr.acinq.eclair.channel.`NORMAL$`
import fr.acinq.eclair.channel.`OFFLINE$`
import fr.acinq.eclair.channel.`WAIT_FOR_FUNDING_CONFIRMED$`
import fr.acinq.eclair.payment.PaymentReceived
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.wire.SwapInResponse
import fr.acinq.phoenix.legacy.*
import fr.acinq.phoenix.legacy.databinding.FragmentReceiveBinding
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.PayToOpenSettings
import fr.acinq.phoenix.legacy.ServiceStatus
import fr.acinq.phoenix.legacy.SwapInSettings
import fr.acinq.phoenix.legacy.paymentdetails.PaymentDetailsFragment
import fr.acinq.phoenix.legacy.utils.AlertHelper
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
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
  private var powerSavingReceiver: BroadcastReceiver? = null

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
    mBinding.appModel = app

    context?.let {
      unitList = listOf(SatUnit.code(), BitUnit.code(), MBtcUnit.code(), BtcUnit.code(), Prefs.getFiatCurrency(it))
      ArrayAdapter(it, android.R.layout.simple_spinner_item, unitList).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.amountUnit.adapter = adapter
      }
      val unit = Prefs.getCoinUnit(it)
      mBinding.amountUnit.setSelection(unitList.indexOf(unit.code()))
    }

    model.invoice.observe(viewLifecycleOwner, {
      if (it != null) {
        model.generateQrCodeBitmap()
        mBinding.rawInvoice.text = if (it.second == null) PaymentRequest.write(it.first) else it.second
      } else {
        mBinding.rawInvoice.text = ""
      }
    })

    model.bitmap.observe(viewLifecycleOwner, { bitmap ->
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

    app.pendingSwapIns.observe(viewLifecycleOwner, {
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

    context?.let { mBinding.descValue.setText(Prefs.getDefaultPaymentDescription(it)) }
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

    mBinding.enlargeButton.setOnClickListener {
      val qrImage = model.bitmap.value
      if (context != null && qrImage != null) {
        AlertHelper.buildFullScreenImage(layoutInflater, qrImage).show()
      }
    }

    mBinding.qrImage.setOnClickListener {
      copyInvoice()
    }

    mBinding.shareButton.setOnClickListener {
      model.invoice.value?.let {
        val source = if (model.invoice.value!!.second != null) "bitcoin:${model.invoice.value!!.second}" else "lightning:${PaymentRequest.write(model.invoice.value!!.first)}"
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.legacy_receive_share_subject))
        shareIntent.putExtra(Intent.EXTRA_TEXT, source)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.legacy_receive_share_title)))
      }
    }

    mBinding.editButton.setOnClickListener {
      model.state.value = PaymentGenerationState.EDITING_REQUEST
    }

    mBinding.withdrawButton.setOnClickListener {
      findNavController().navigate(R.id.global_action_any_to_read_input)
    }

    mBinding.actionBar.setOnBackAction { handleBackAction() }

    if (model.state.value == PaymentGenerationState.INIT) {
      generatePaymentRequest()
    }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
    context?.let {
      if (powerSavingReceiver != null) {
        it.unregisterReceiver(powerSavingReceiver!!)
      }
    }
  }

  private fun handleBackAction() {
    if (model.state.value == PaymentGenerationState.EDITING_REQUEST) {
      model.state.value = PaymentGenerationState.DONE
    } else {
      findNavController().navigate(R.id.action_receive_to_main)
    }
  }

  private fun copyInvoice() {
    context?.run {
      val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val source = model.invoice.value!!.second ?: PaymentRequest.write(model.invoice.value!!.first)
      clipboard.setPrimaryClip(ClipData.newPlainText("Payment request", source))
      Toast.makeText(this, R.string.legacy_utils_copied, Toast.LENGTH_SHORT).show()
    }
  }

  private fun generatePaymentRequest() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when generating payment request: ", exception)
      model.state.value = PaymentGenerationState.ERROR
    }) {
      Wallet.hideKeyboard(context, mBinding.amountValue)
      model.state.value = PaymentGenerationState.IN_PROGRESS
      val invoice = app.requireService.generatePaymentRequest(mBinding.descValue.text.toString(), extractAmount(), Prefs.getPaymentsExpirySeconds(requireContext()))
      model.invoice.value = Pair(invoice, null)
    }
  }

  private fun generateSwapIn(context: Context) {
    Wallet.hideKeyboard(context, mBinding.amountValue)
    when (appContext(context).swapInSettings.value?.status ?: ServiceStatus.Unknown) {
      ServiceStatus.Unknown -> {
        model.state.value = SwapInState.DISABLED
        mBinding.swapInDisabledMessage.text = Converter.html(getString(R.string.legacy_receive_swap_in_unknown))
        return
      }
      ServiceStatus.Disabled.Generic -> {
        model.state.value = SwapInState.DISABLED
        mBinding.swapInDisabledMessage.text = Converter.html(getString(R.string.legacy_receive_swap_in_disabled))
        return
      }
      ServiceStatus.Disabled.MempoolFull -> {
        model.state.value = SwapInState.DISABLED
        mBinding.swapInDisabledMessage.text = Converter.html(getString(R.string.legacy_receive_swap_in_disabled_mempool))
        return
      }
      else -> lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
        log.error("error when generating swap in: ", exception)
        model.state.value = SwapInState.ERROR
      }) {
        model.state.value = SwapInState.IN_PROGRESS
        app.requireService.requestSwapIn()
      }
    }
  }

  private fun refreshConversionDisplay() {
    context?.let {
      try {
        val amount = extractAmount()
        val unit = mBinding.amountUnit.selectedItem.toString()
        if (unit == Prefs.getFiatCurrency(it)) {
          mBinding.amountConverted.text = getString(R.string.legacy_utils_converted_amount, Converter.printAmountPretty(amount.get(), it, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.legacy_utils_converted_amount, Converter.printFiatPretty(it, amount.get(), withUnit = true))
        }
      } catch (e: Exception) {
        log.info("could not extract amount: ${e.message}")
        mBinding.amountConverted.text = ""
      }
    }
  }

  private fun extractAmount(): Option<MilliSatoshi> {
    val unit = mBinding.amountUnit.selectedItem
    val amount = mBinding.amountValue.text.toString()
    return context?.run {
      when (unit) {
        null -> Option.empty()
        Prefs.getFiatCurrency(this) -> Option.apply(Converter.convertFiatToMsat(this, amount))
        else -> Converter.string2Msat_opt(amount, unit.toString())
      }
    } ?: Option.empty()
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
