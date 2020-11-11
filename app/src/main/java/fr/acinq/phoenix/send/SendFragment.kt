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

package fr.acinq.phoenix.send

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.wire.SwapOutResponse
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentSendBinding
import fr.acinq.phoenix.db.AppDb
import fr.acinq.phoenix.utils.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.Option

class SendFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSendBinding
  private val args: SendFragmentArgs by navArgs()
  private lateinit var model: SendViewModel

  private lateinit var unitList: List<String>

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSendBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(SendViewModel::class.java)
    mBinding.model = model
    mBinding.appModel = app

    app.state.value?.kit()?.run {
      val minFeerateSwapout = appContext()?.swapOutSettings?.value?.minFeerateSatByte ?: 1
      val feerate = nodeParams().onChainFeeConf().feeEstimator().run {
        FeerateEstimationPerKb(
          rate20min = (getFeeratePerKb(2) / 1000).coerceAtLeast(minFeerateSwapout),
          rate60min = (getFeeratePerKb(6) / 1000).coerceAtLeast(minFeerateSwapout),
          rate12hours = (getFeeratePerKb(72) / 1000).coerceAtLeast(minFeerateSwapout))
      }
      log.info("feerates base estimation=$feerate")
      model.feerateEstimation.value = feerate
      model.chainFeesSatBytes.value = feerate.rate60min
    }

    context?.let {
      unitList = listOf(SatUnit.code(), BitUnit.code(), MBtcUnit.code(), BtcUnit.code(), Prefs.getFiatCurrency(it))
      ArrayAdapter(it, android.R.layout.simple_spinner_item, unitList).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.unit.adapter = adapter
      }
      val unit = Prefs.getCoinUnit(it)
      mBinding.unit.setSelection(unitList.indexOf(unit.code()))
    }

    model.state.observe(viewLifecycleOwner, { state ->
      context?.let { ctx ->
        model.isAmountFieldPristine.value = true
        when {
          state is SendState.Lightning.Ready && state.pr.amount().isDefined -> {
            mBinding.amount.setText(Converter.printAmountRaw(state.pr.amount().get(), ctx))
          }
          state is SendState.Onchain.SwapRequired && state.uri.amount != null -> {
            mBinding.amount.setText(Converter.printAmountRaw(state.uri.amount, ctx))
          }
          state is SendState.Onchain.Ready && state.pr.amount().isDefined -> {
            val amountEnteredByUser = checkAmount()
            if (amountEnteredByUser.isEmpty) {
              model.state.value = SendState.Onchain.SwapRequired(state.uri)
            } else {
              val totalAfterSwap = state.pr.amount().get()
              log.info("swap-out [ amountEnteredByUser=$amountEnteredByUser fee=${state.fee} totalAfterSwap=$totalAfterSwap ]")
              if (state.fee.toLong() < 0) {
                model.state.value = SendState.Onchain.SwapRequired(state.uri)
              } else {
                mBinding.swapRecapAmountValue.text = Converter.printAmountPretty(amountEnteredByUser.get(), ctx, withUnit = true)
                mBinding.swapRecapAmountValueFiat.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(ctx, amountEnteredByUser.get(), withUnit = true))
                mBinding.swapRecapFeeValue.setTextColor(ThemeHelper.color(ctx, R.attr.textColor))
                mBinding.swapRecapFeeValue.text = Converter.printAmountPretty(state.fee, ctx, withUnit = true)
                mBinding.swapRecapFeeValueFiat.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(ctx, Converter.any2Msat(state.fee), withUnit = true))
                mBinding.swapRecapTotalValue.text = Converter.printAmountPretty(totalAfterSwap, ctx, withUnit = true)
                val sendable = appContext(ctx).balance.value?.sendable
                if (sendable == null || totalAfterSwap.`$greater`(sendable)) {
                  model.state.value = SendState.Onchain.Error.ExceedsBalance(state.uri)
                }
              }
            }
          }
          state is SendState.Onchain.Error.ExceedsBalance -> mBinding.swapRecapFeeValue.setTextColor(ThemeHelper.color(ctx, R.attr.negativeColor))
          else -> Unit
        }
      }
    })

    model.amountErrorMessage.observe(viewLifecycleOwner, { msgId ->
      if (model.isAmountFieldPristine.value != true) {
        if (msgId != null) {
          mBinding.amountConverted.text = ""
          mBinding.amountError.text = getString(msgId)
          mBinding.amountError.visibility = View.VISIBLE
        } else {
          mBinding.amountError.visibility = View.GONE
        }
      }
    })

    model.useMaxBalance.observe(viewLifecycleOwner, { useMax ->
      if (useMax) {
        context?.let { ctx ->
          appContext(ctx).balance.value?.let {
            val unit = Prefs.getCoinUnit(ctx)
            mBinding.unit.setSelection(unitList.indexOf(unit.code()))
            mBinding.amount.setText(Converter.printAmountRaw(it.sendable, ctx))
          }
        }
      }
    })

    model.chainFeesSatBytes.observe(viewLifecycleOwner, { feerate ->
      val state = model.state.value
      if (state is SendState.Onchain && state !is SendState.Onchain.SwapRequired) {
        model.state.value = SendState.Onchain.SwapRequired(state.uri)
      }

      model.feerateEstimation.value?.let { feerateEstimation ->
        val minFeerateSwapout = appContext()?.swapOutSettings?.value?.minFeerateSatByte ?: 1
        when {
          feerate <= 0 -> mBinding.chainFeesFeedback.apply {
            text = getString(R.string.send_chain_fees_feedback_invalid)
            setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(context, R.drawable.ic_alert_triangle), null, null, null)
          }
          feerate < minFeerateSwapout -> mBinding.chainFeesFeedback.apply {
            text = getString(R.string.send_chain_fees_feedback_too_low, minFeerateSwapout.toString())
            setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(context, R.drawable.ic_alert_triangle), null, null, null)
          }
          else -> mBinding.chainFeesFeedback.apply {
            text = getString(when {
              feerate < feerateEstimation.rate12hours -> R.string.send_chain_fees_feedback_inf
              feerate < feerateEstimation.rate60min -> R.string.send_chain_fees_feedback_12h
              feerate < feerateEstimation.rate20min -> R.string.send_chain_fees_feedback_1h
              else -> R.string.send_chain_fees_feedback_20min
            })
            setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(context, R.drawable.ic_blank), null, null, null)
          }
        }
      }
    })

    app.networkInfo.observe(viewLifecycleOwner, {
      if (!it.lightningConnected) {
        mBinding.sendButton.setIsPaused(true)
        mBinding.sendButton.setText(getString(R.string.btn_pause_connecting))
      } else if (it.electrumServer == null) {
        mBinding.sendButton.setIsPaused(true)
        mBinding.sendButton.setText(getString(R.string.btn_pause_connecting_electrum))
      } else {
        mBinding.sendButton.setIsPaused(false)
        mBinding.sendButton.setText(getString(R.string.send_pay_button))
      }
    })

    model.checkAndSetPaymentRequest(args.payload)

    mBinding.amount.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) {
        model.isAmountFieldPristine.value = false
        val state = model.state.value
        if (state is SendState.Onchain && state !is SendState.Onchain.SwapRequired) {
          model.state.value = SendState.Onchain.SwapRequired(state.uri)
        }
      }

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        checkAmount()
      }
    })

    mBinding.unit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) = Unit

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val state = model.state.value
        if (state is SendState.Onchain && state !is SendState.Onchain.SwapRequired) {
          model.state.value = SendState.Onchain.SwapRequired(state.uri)
        }
        checkAmount()
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }

    appContext()?.balance?.value?.let {
      mBinding.balanceValue.setAmount(it.sendable)
    }

    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }

    mBinding.sendButton.setOnClickListener {
      model.isAmountFieldPristine.value = false
      Wallet.hideKeyboard(context, mBinding.amount)
      val amount = checkAmount()
      if (amount.isDefined) {
        when (val state = model.state.value) {
          is SendState.Onchain.Ready -> sendSwapOut(state)
          is SendState.Lightning.Ready -> sendPaymentFinal(amount.get(), state.pr)
        }
      }
    }

    mBinding.prepareSwapButton.setOnClickListener {
      model.state.value?.let {
        if (it is SendState.Onchain) {
          requestSwapOut(it.uri)
        }
      }
    }

    mBinding.showChainFeesButton.setOnClickListener { model.showFeeratesForm.value = true }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  private fun requestSwapOut(uri: BitcoinURI) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when requesting swap-out: ", exception)
      model.state.postValue(SendState.Onchain.SwapRequired(uri))
      Toast.makeText(context, getString(R.string.send_swap_error), Toast.LENGTH_SHORT).show()
    }) {
      model.isAmountFieldPristine.value = false
      val amount = checkAmount()
      val feerateSatPerByte = model.chainFeesSatBytes.value!!
      if (feerateSatPerByte <= 0 || feerateSatPerByte < appContext()!!.swapOutSettings.value!!.minFeerateSatByte) {
        model.showFeeratesForm.value = true
      } else if (amount.isDefined) {
        Wallet.hideKeyboard(context, mBinding.amount)
        model.state.value = SendState.Onchain.Swapping(uri, feerateSatPerByte)
        app.requireService.requestSwapOut(amount = Converter.msat2sat(amount.get()), address = uri.address, feeratePerKw = `package$`.`MODULE$`.feerateByte2Kw(feerateSatPerByte))
      }
    }
  }

  private fun sendSwapOut(swapOutData: SendState.Onchain.Ready) {
    lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, exception ->
      log.error("error when sending payment: ", exception)
      model.state.postValue(SendState.Onchain.Error.SendingFailure(swapOutData.uri, swapOutData.pr))
    }) {
      model.state.postValue(SendState.Onchain.Sending(swapOutData.uri, swapOutData.pr, swapOutData.feeratePerByte, swapOutData.fee))
      val uuid = app.requireService.sendPaymentRequest(
        amount = swapOutData.pr.amount().get(),
        paymentRequest = swapOutData.pr,
        subtractFee = model.useMaxBalance.value ?: false)
      if (uuid != null) {
        context?.let {
          AppDb.getInstance(it.applicationContext).paymentMetaQueries.insertSwapOut(id = uuid.toString(),
            swap_out_address = swapOutData.uri.address,
            swap_out_feerate_per_byte = swapOutData.feeratePerByte,
            swap_out_fee_sat = swapOutData.fee.toLong()
          )
        }
      }
      findNavController().navigate(R.id.action_send_to_main)
    }
  }

  private fun sendPaymentFinal(amount: MilliSatoshi, pr: PaymentRequest) {
    val state = model.state.value
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when sending payment: ", exception)
      model.state.value = when (state) {
        is SendState.Lightning -> SendState.Lightning.Error.SendingFailure(pr)
        else -> SendState.CheckingInvoice
      }
    }) {
      model.state.value = when (state) {
        is SendState.Lightning -> SendState.Lightning.Sending(pr)
        else -> throw RuntimeException("unhandled state=$state when sending payment")
      }
      app.requireService.sendPaymentRequest(amount = amount, paymentRequest = pr, subtractFee = model.useMaxBalance.value ?: false)
      findNavController().navigate(R.id.action_send_to_main)
    }
  }

  private fun checkAmount(): Option<MilliSatoshi> {
    return try {
      val ctx = requireContext()
      val unit = mBinding.unit.selectedItem.toString()
      val amountInput = mBinding.amount.text.toString()
      val balance = appContext(requireContext()).balance.value
      model.amountErrorMessage.value = null
      val fiat = Prefs.getFiatCurrency(ctx)
      val amount = if (unit == fiat) {
        Option.apply(Converter.convertFiatToMsat(ctx, amountInput))
      } else {
        Converter.string2Msat_opt(amountInput, unit)
      }
      if (amount.isDefined) {
        if (unit == fiat) {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printAmountPretty(amount.get(), ctx, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(ctx, amount.get(), withUnit = true))
        }
        if (balance != null && amount.get().`$greater`(balance.sendable)) {
          throw InsufficientBalance()
        }
        if (model.state.value is SendState.Onchain && amount.get().`$less`(Satoshi(10000))) {
          throw SwapOutInsufficientAmount()
        }
      } else {
        throw RuntimeException("amount is undefined")
      }
      amount
    } catch (e: Exception) {
      log.info("could not check amount: ${e.message}")
      mBinding.amountConverted.text = ""
      model.amountErrorMessage.value = when (e) {
        is SwapOutInsufficientAmount -> R.string.send_amount_error_swap_out_too_small
        is InsufficientBalance -> R.string.send_amount_error_balance
        else -> R.string.send_amount_error
      }
      Option.empty()
    }
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: SwapOutResponse) {
    val state = model.state.value
    if (state is SendState.Onchain.Swapping) {
      val paymentRequest = PaymentRequest.read(event.paymentRequest())
      log.info("swapped ${state.uri} -> $paymentRequest")
      model.state.postValue(SendState.Onchain.Ready(state.uri, paymentRequest, state.feeratePerByte, event.feeSatoshis()))
    }
  }
}
