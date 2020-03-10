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
import androidx.lifecycle.Observer
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
import fr.acinq.phoenix.utils.*
import kotlinx.coroutines.CoroutineExceptionHandler
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

    context?.let {
      unitList = listOf(SatUnit.code(), BitUnit.code(), MBtcUnit.code(), BtcUnit.code(), Prefs.getFiatCurrency(it))
      ArrayAdapter(it, android.R.layout.simple_spinner_item, unitList).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.unit.adapter = adapter
      }
      val unit = Prefs.getCoinUnit(it)
      mBinding.unit.setSelection(unitList.indexOf(unit.code()))
    }

    model.state.observe(viewLifecycleOwner, Observer { state ->
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
              val fee = totalAfterSwap.`$minus`(amountEnteredByUser.get())
              log.info("swap-out [ amountEnteredByUser=$amountEnteredByUser fee=$fee totalAfterSwap=$totalAfterSwap ]")
              if (fee.toLong() < 0) {
                model.state.value = SendState.Onchain.SwapRequired(state.uri)
              } else {
                mBinding.swapRecapAmountValue.text = Converter.printAmountPretty(amountEnteredByUser.get(), ctx, withUnit = true)
                mBinding.swapRecapFeeValue.setTextColor(ThemeHelper.color(ctx, R.attr.textColor))
                mBinding.swapRecapFeeValue.text = Converter.printAmountPretty(fee, ctx, withUnit = true)
                mBinding.swapRecapTotalValue.text = Converter.printAmountPretty(totalAfterSwap, ctx, withUnit = true)
                if (totalAfterSwap.`$greater`(app.balance.value)) {
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

    model.amountErrorMessage.observe(viewLifecycleOwner, Observer { msgId ->
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

    model.useMaxBalance.observe(viewLifecycleOwner, Observer { useMax ->
      if (useMax && context != null && app.balance.value != null) {
        app.balance.value!!.run {
          val unit = Prefs.getCoinUnit(context!!)
          mBinding.unit.setSelection(unitList.indexOf(unit.code()))
          mBinding.amount.setText(Converter.printAmountRaw(this, context!!))
        }
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

    app.balance.value?.let {
      mBinding.balanceValue.setAmount(it)
    } ?: log.warn("balance is not available yet")

    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })

    mBinding.sendButton.setOnClickListener {
      model.isAmountFieldPristine.value = false
      Wallet.hideKeyboard(context, mBinding.amount)
      val amount = checkAmount()
      if (amount.isDefined) {
        when (val state = model.state.value) {
          is SendState.Onchain.Ready -> sendPaymentFinal(state.pr.amount().get(), state.pr)
          is SendState.Lightning.Ready -> sendPaymentFinal(amount.get(), state.pr)
        }
      }
    }

    mBinding.swapButton.setOnClickListener {
      model.state.value?.let {
        if (it is SendState.Onchain) {
          requestSwapOut(it.uri)
        }
      }
    }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  private fun requestSwapOut(uri: BitcoinURI) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when sending SwapOut: ", exception)
      model.state.postValue(SendState.Onchain.SwapRequired(uri))
      Toast.makeText(context, getString(R.string.send_swap_error), Toast.LENGTH_SHORT).show()
    }) {
      model.isAmountFieldPristine.value = false
      model.state.value = SendState.Onchain.Swapping(uri)
      Wallet.hideKeyboard(context, mBinding.amount)
      val amount = checkAmount()
      if (amount.isDefined) {
        // FIXME: use feerate provided by user, like in eclair mobile
        val feeratePerKw = app.kit?.run { nodeParams().onChainFeeConf().feeEstimator().getFeeratePerKw(6) } ?: throw KitNotInitialized()
        app.requestSwapOut(amount = Converter.msat2sat(amount.get()), address = uri.address, feeratePerKw = feeratePerKw)
      } else {
        model.state.postValue(SendState.Onchain.SwapRequired(uri))
      }
    }
  }

  private fun sendPaymentFinal(amount: MilliSatoshi, pr: PaymentRequest) {
    val state = model.state.value
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when sending payment: ", exception)
      model.state.value = when (state) {
        is SendState.Lightning -> SendState.Lightning.Error.SendingFailure(pr)
        is SendState.Onchain -> SendState.Onchain.Error.SendingFailure(state.uri, pr)
        else -> SendState.CheckingInvoice
      }
    }) {
      model.state.value = when (state) {
        is SendState.Lightning -> SendState.Lightning.Sending(pr)
        is SendState.Onchain -> SendState.Onchain.Sending(state.uri, pr)
        else -> throw RuntimeException("unhandled state=$state when sending payment")
      }
      app.sendPaymentRequest(amount = amount, paymentRequest = pr, subtractFee = model.useMaxBalance.value ?: false)
      findNavController().navigate(R.id.action_send_to_main)
    }
  }

  private fun checkAmount(): Option<MilliSatoshi> {
    return try {
      val unit = mBinding.unit.selectedItem.toString()
      val amountInput = mBinding.amount.text.toString()
      val balance = app.balance.value
      model.amountErrorMessage.value = null
      val fiat = Prefs.getFiatCurrency(context!!)
      val amount = if (unit == fiat) {
        Option.apply(Converter.convertFiatToMsat(context!!, amountInput))
      } else {
        Converter.string2Msat_opt(amountInput, unit)
      }
      if (amount.isDefined) {
        if (unit == fiat) {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printAmountPretty(amount.get(), context!!, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(context!!, amount.get(), withUnit = true))
        }
        if (balance != null && amount.get().`$greater`(balance)) {
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
      model.state.postValue(SendState.Onchain.Ready(state.uri, paymentRequest))
    }
  }
}
