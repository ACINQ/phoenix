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
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.`BtcUnit$`
import fr.acinq.eclair.`MBtcUnit$`
import fr.acinq.eclair.`SatUnit$`
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSendBinding
import fr.acinq.eclair.phoenix.receive.SwapInState
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.InsufficientBalance
import fr.acinq.eclair.phoenix.utils.Prefs
import fr.acinq.eclair.phoenix.utils.Wallet
import fr.acinq.eclair.wire.SwapOutResponse
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.Option
import scala.util.Left

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
      unitList = listOf(`SatUnit$`.`MODULE$`.code(), `MBtcUnit$`.`MODULE$`.code(), `BtcUnit$`.`MODULE$`.code(), Prefs.getFiatCurrency(it))
      ArrayAdapter(it, android.R.layout.simple_spinner_item, unitList).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.unit.adapter = adapter
      }
      val unit = Prefs.getCoinUnit(it)
      mBinding.unit.setSelection(unitList.indexOf(unit.code()))
    }

    model.invoice.observe(viewLifecycleOwner, Observer {
      it?.let {
        context?.let { ctx ->
          model.isAmountFieldPristine.value = true
          when {
            // invoice is a bitcoin uri not swapped yet
            it.isLeft && it.left().get().second == null -> {
              model.swapState.value = SwapState.SWAP_REQUIRED
              it.left().get().first.amount?.let { amount -> mBinding.amount.setText(Converter.printAmountRaw(amount, ctx)) }
            }
            // invoice is a bitcoin uri with a swapped LN invoice
            it.isLeft && it.left().get().second != null && it.left().get().second!!.amount().isDefined -> {
              val amountInput = checkAmount()
              if (amountInput.isDefined) {
                val fee = it.left().get().second!!.amount().get().toLong() - amountInput.get().toLong()
                if (fee < 0) {
                  model.swapState.value = SwapState.SWAP_REQUIRED
                  log.error("fee after swap is < 0: $fee (input=${amountInput.get()} pr_amount=${it.left().get().second!!.amount().get().toLong()}")
                } else {
                  mBinding.swapCompleteRecap.text = Converter.html(getString(R.string.send_swap_complete_recap,
                    Converter.printAmountPretty(amount = MilliSatoshi(fee), context = ctx, withUnit = true),
                    Converter.printAmountPretty(amount = it.left().get().second!!.amount().get(), context = context!!, withUnit = true)))
                  model.swapState.value = SwapState.SWAP_COMPLETE
                }
              }
              Unit
            }
            // invoice is a payment request
            it.isRight && it.right().get().amount().isDefined -> {
              model.swapState.value = SwapState.NO_SWAP
              mBinding.amount.setText(Converter.printAmountRaw(it.right().get().amount().get(), ctx))
            }
            else -> Unit
          }
        }
      }
    })

    model.swapMessageEvent.observe(viewLifecycleOwner, Observer {
      Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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
      if (useMax && context != null) {
        appKit.nodeData.value?.run {
          val unit = Prefs.getCoinUnit(context!!)
          mBinding.unit.setSelection(unitList.indexOf(unit.code()))
          mBinding.amount.setText(Converter.printAmountRaw(this.balance, context!!))
        } ?: (log.warn("balance is not available yet").also { model.useMaxBalance.value = false })
      }
    })

    model.checkAndSetPaymentRequest(args.payload)

    mBinding.amount.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) {
        model.isAmountFieldPristine.value = false
        if (model.swapState.value != SwapState.NO_SWAP) {
          model.swapState.value = SwapState.SWAP_REQUIRED
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
        if (model.swapState.value != SwapState.NO_SWAP) {
          model.swapState.value = SwapState.SWAP_REQUIRED
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

    appKit.nodeData.value?.let {
      mBinding.balanceValue.setAmount(it.balance)
    } ?: log.warn("balance is not available yet")

    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })

    mBinding.sendButton.setOnClickListener { _ ->
      model.isAmountFieldPristine.value = false
      model.invoice.value?.let {
        Wallet.hideKeyboard(context, mBinding.amount)
        val amount_opt = checkAmount()
        if (amount_opt.isDefined) {
          val amount = amount_opt.get()
          when {
            it.isLeft && model.swapState.value != SwapState.SWAP_REQUIRED -> {
              it.left()?.get()?.second?.let { pr ->
                if (pr.amount().isDefined) {
                  sendPaymentFinal(pr.amount().get(), pr)
                }
              } ?: log.warn("invoice=$it in model is not valid for swap")
            }
            it.isRight -> {
              sendPaymentFinal(amount, it.right().get())
            }
          }
        }
      }
    }

    mBinding.swapButton.setOnClickListener {
      sendSwapOut()
    }
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  private fun sendSwapOut() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when sending SwapOut: ", exception)
      model.swapState.postValue(SwapState.SWAP_REQUIRED)
      model.swapMessageEvent.postValue(R.string.send_swap_error)
    }) {
      model.isAmountFieldPristine.value = false
      Wallet.hideKeyboard(context, mBinding.amount)
      model.invoice.value?.let {
        val amount_opt = checkAmount()
        if (amount_opt.isDefined) {
          val amount = amount_opt.get()
          if (it.isLeft) {
            val rawAmount = Converter.msat2sat(amount)
            val finalAmount: Satoshi = if (model.useMaxBalance.value == true) {
              // when trying to send all the available balance on chain, a fee should be subtracted from the amount
              // FIXME: stop using hard coded fee...
              rawAmount.`$minus`(Satoshi(300))
            } else {
              rawAmount
            }
            // FIXME: use feerate provided by user, like in eclair mobile
            val feeratePerKw = appKit.kit.value?.run { kit.nodeParams().onChainFeeConf().feeEstimator().getFeeratePerKw(6) } ?: 0L
            model.swapState.postValue(SwapState.SWAP_IN_PROGRESS)
            appKit.sendSwapOut(amount = finalAmount, address = it.left().get().first.address, feeratePerKw = feeratePerKw)
          }
        }
      }
    }
  }

  private fun sendPaymentFinal(amount: MilliSatoshi, pr: PaymentRequest) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when sending payment: ", exception)
      model.state.value = SendState.VALID_INVOICE
    }) {
      model.state.value = SendState.SENDING
      appKit.sendPaymentRequest(amount = amount, paymentRequest = pr, deductFeeFromAmount = model.useMaxBalance.value ?: false, checkFees = false)
      findNavController().navigate(R.id.action_send_to_main)
    }
  }

  private fun checkAmount(): Option<MilliSatoshi> {
    val unit = mBinding.unit.selectedItem.toString()
    val amountInput = mBinding.amount.text.toString()
    val balance = appKit.nodeData.value?.balance

    return try {
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
      } else {
        throw RuntimeException("amount is undefined")
      }
      amount
    } catch (e: InsufficientBalance) {
      model.amountErrorMessage.value = R.string.send_amount_error_balance
      Option.empty()
    } catch (e: Exception) {
      log.info("could not check amount: ${e.message}")
      model.amountErrorMessage.value = R.string.send_amount_error
      Option.empty()
    }
  }

  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun handleEvent(event: SwapOutResponse) {
    model.invoice.value?.run {
      if (isLeft) {
        val bitcoinURI = left().get().first
        val paymentRequest = PaymentRequest.read(event.paymentRequest())
        log.info("swapped $bitcoinURI -> $paymentRequest")
        model.invoice.postValue(Left.apply(Pair(bitcoinURI, paymentRequest)))
      }
    }
  }
}
