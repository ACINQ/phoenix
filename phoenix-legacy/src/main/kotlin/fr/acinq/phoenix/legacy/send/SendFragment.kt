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

package fr.acinq.phoenix.legacy.send

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.eclair.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.wire.SwapOutResponse
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.NavGraphMainDirections
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.ServiceStatus
import fr.acinq.phoenix.legacy.databinding.FragmentSendBinding
import fr.acinq.phoenix.legacy.db.AppDb
import fr.acinq.phoenix.legacy.paymentdetails.PaymentDetailsFragment
import fr.acinq.phoenix.legacy.utils.AlertHelper
import fr.acinq.phoenix.legacy.utils.Constants
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.ThemeHelper
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

class SendFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSendBinding
  private val args: SendFragmentArgs by navArgs()
  private lateinit var model: SendViewModel

  private lateinit var unitList: List<String>
  private val swapOutSettings by lazy { appContext()?.swapOutSettings?.value ?: Constants.DEFAULT_SWAP_OUT_SETTINGS }

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

    context?.let {
      unitList = listOf(SatUnit.code(), BitUnit.code(), MBtcUnit.code(), BtcUnit.code(), Prefs.getFiatCurrency(it))
      ArrayAdapter(it, android.R.layout.simple_spinner_item, unitList).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.unit.adapter = adapter
      }
      val unit = Prefs.getCoinUnit(it)
      mBinding.unit.setSelection(unitList.indexOf(unit.code()))
    }

    model.state.observe(viewLifecycleOwner) { state ->
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
                mBinding.swapRecapAmountValueFiat.text = getString(R.string.legacy_utils_converted_amount, Converter.printFiatPretty(ctx, amountEnteredByUser.get(), withUnit = true))
                mBinding.swapRecapFeeValue.setTextColor(ThemeHelper.color(ctx, R.attr.textColor))
                mBinding.swapRecapFeeValue.text = Converter.printAmountPretty(state.fee, ctx, withUnit = true)
                mBinding.swapRecapFeeValueFiat.text = getString(R.string.legacy_utils_converted_amount, Converter.printFiatPretty(ctx, Converter.any2Msat(state.fee), withUnit = true))
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
    }

    model.amountError.observe(viewLifecycleOwner) { error ->
      if (model.isAmountFieldPristine.value != true) {
        if (error != null) {
          mBinding.amountConverted.text = ""
          mBinding.amountError.text = when (error) {
            AmountError.NotEnoughBalance -> getString(R.string.legacy_send_amount_error_balance)
            AmountError.SwapOutBelowMin -> getString(R.string.legacy_send_amount_error_swap_out_too_small, Converter.printAmountPretty(swapOutSettings.minAmount, requireContext(), withUnit = true))
            AmountError.SwapOutAboveMax -> getString(R.string.legacy_send_amount_error_swap_out_above_max, Converter.printAmountPretty(swapOutSettings.maxAmount, requireContext(), withUnit = true))
            AmountError.AboveRequestedAmount -> {
              val state = model.state.value
              val prAmount = if (state is SendState.Lightning && state.pr.amount().isDefined) state.pr.amount().get() else null
              if (prAmount != null) {
                getString(R.string.legacy_send_amount_error_above_request, Converter.printAmountPretty(prAmount.`$times`(2), requireContext(), withUnit = true))
              } else {
                getString(R.string.legacy_send_amount_error_above_request_generic)
              }
            }
            else -> getString(R.string.legacy_send_amount_error)
          }
          mBinding.amountError.visibility = View.VISIBLE
        } else {
          mBinding.amountError.visibility = View.GONE
        }
      }
    }

    app.networkInfo.observe(viewLifecycleOwner) {
      if (!it.lightningConnected) {
        mBinding.sendButton.setIsPaused(true)
        mBinding.sendButton.setText(getString(R.string.legacy_btn_pause_connecting))
      } else if (it.electrumServer == null) {
        mBinding.sendButton.setIsPaused(true)
        mBinding.sendButton.setText(getString(R.string.legacy_btn_pause_connecting_electrum))
      } else {
        mBinding.sendButton.setIsPaused(false)
        mBinding.sendButton.setText(getString(R.string.legacy_send_pay_button))
      }
    }

    model.checkAndSetPaymentRequest(app.service, args.payload, swapOutSettings)

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
          else -> {}
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

    mBinding.swapRecapFeeLabel.setOnClickListener {
      AlertHelper.build(layoutInflater, getString(R.string.legacy_send_mining_fee_info_title), Converter.html(getString(R.string.legacy_send_mining_fee_info_message))).show()
    }

    mBinding.alreadyPaidLayoutButton.setOnClickListener {
      val state = model.state.value
      if (state is SendState.InvalidInvoice.AlreadyPaid) {
        findNavController().navigate(NavGraphMainDirections.globalActionAnyToPaymentDetails(PaymentDetailsFragment.OUTGOING, state.parentId.toString(), false))
      }
    }

    mBinding.swapoutServiceDisabledButton.setOnClickListener {
      findNavController().popBackStack()
    }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  private fun getSwapoutFeerate(): Long {
    return if (swapOutSettings.minFeerateSatByte == 0L) {
      (app.state.value?.kit()?.nodeParams()?.onChainFeeConf()?.feeEstimator()?.getFeeratePerKb(6) ?: 3000) / 1000 // target 1 hour
    } else {
      swapOutSettings.minFeerateSatByte
    }
  }

  private fun requestSwapOut(uri: BitcoinURI) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when requesting swap-out: ", exception)
      model.state.postValue(SendState.Onchain.SwapRequired(uri))
      Toast.makeText(context, getString(R.string.legacy_send_swap_error), Toast.LENGTH_SHORT).show()
    }) {
      model.isAmountFieldPristine.value = false
      val amount = checkAmount()
      val feerateSatPerByte = getSwapoutFeerate()
      log.debug("requesting swap-out with feerate=$feerateSatPerByte sat/b")
      if (amount.isDefined) {
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
        paymentRequest = swapOutData.pr)
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
      app.requireService.sendPaymentRequest(amount = amount, paymentRequest = pr)
      findNavController().navigate(R.id.action_send_to_main)
    }
  }

  private fun checkAmount(): Option<MilliSatoshi> {
    return try {
      val ctx = requireContext()
      val unit = mBinding.unit.selectedItem.toString()
      val amountInput = mBinding.amount.text.toString()
      val balance = appContext(requireContext()).balance.value
      model.amountError.value = null
      val fiat = Prefs.getFiatCurrency(ctx)
      val amount = if (unit == fiat) {
        Option.apply(Converter.convertFiatToMsat(ctx, amountInput))
      } else {
        Converter.string2Msat_opt(amountInput, unit)
      }
      if (amount.isDefined) {
        if (unit == fiat) {
          mBinding.amountConverted.text = getString(R.string.legacy_utils_converted_amount, Converter.printAmountPretty(amount.get(), ctx, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.legacy_utils_converted_amount, Converter.printFiatPretty(ctx, amount.get(), withUnit = true))
        }

        // validate amount
        if (balance != null && amount.get().`$greater`(balance.sendable)) {
          throw AmountError.NotEnoughBalance
        }
        when (val state = model.state.value) {
          is SendState.Onchain -> {
            if (amount.get().`$less`(swapOutSettings.minAmount)) {
              throw AmountError.SwapOutBelowMin
            }
            if (amount.get().`$greater`(swapOutSettings.maxAmount)) {
              throw AmountError.SwapOutAboveMax
            }
          }
          is SendState.Lightning -> {
            val prAmount = if (state.pr.amount().isDefined) state.pr.amount().get() else null
            if (prAmount != null && amount.get().`$greater`(prAmount.`$times`(2))) {
              throw AmountError.AboveRequestedAmount
            }
          }
          else -> {}
        }
      } else {
        throw RuntimeException("amount is undefined")
      }
      amount
    } catch (e: Exception) {
      log.debug("user entered an invalid amount: ${e.message ?: e::class.java.simpleName}")
      mBinding.amountConverted.text = ""
      model.amountError.value = e
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
