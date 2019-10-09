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
import android.text.Html
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.`BtcUnit$`
import fr.acinq.eclair.`MBtcUnit$`
import fr.acinq.eclair.`SatUnit$`
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSendBinding
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.Prefs
import fr.acinq.eclair.phoenix.utils.Wallet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.Option

class SendFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentSendBinding
  private val args: SendFragmentArgs by navArgs()

  private lateinit var model: SendViewModel
  private var amountPristine = true // to prevent early validation error message if amount is not set in invoice

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
      ArrayAdapter(it,
        android.R.layout.simple_spinner_item,
        listOf(`SatUnit$`.`MODULE$`.code(), `MBtcUnit$`.`MODULE$`.code(), `BtcUnit$`.`MODULE$`.code(), Prefs.getFiatCurrency(it))).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.unit.adapter = adapter
      }
    }

    model.invoice.observe(viewLifecycleOwner, Observer {
      it?.let {
        context?.let { ctx ->
          amountPristine = true
          when {
            // invoice is a bitcoin uri not swapped yet
            it.isLeft && it.left().get().second == null -> {
              model.swapState.value = SwapState.SWAP_REQUIRED
              it.left().get().first.amount?.let { amount -> mBinding.amount.setText(Converter.printAmountRaw(amount, ctx)) }
            }
            // invoice is a bitcoin uri with a swapped LN invoice
            it.isLeft && it.left().get().second != null && it.left().get().second!!.amount().isDefined -> {
              val amountInput = extractAmount()
              if (amountInput.isDefined) {
                val fee = it.left().get().second!!.amount().get().toLong() - amountInput.get().toLong()
                if (fee <= 0) {
                  model.swapState.value = SwapState.SWAP_REQUIRED
                } else {
                  mBinding.swapCompleteRecap.text = Html.fromHtml(getString(R.string.send_swap_complete_recap,
                    Converter.printAmountPretty(amount = MilliSatoshi(fee), context = ctx, withUnit = true),
                    Converter.printAmountPretty(amount = it.left().get().second!!.amount().get(), context = context!!, withUnit = true)), Html.FROM_HTML_MODE_COMPACT)
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

    model.checkAndSetPaymentRequest(args.invoice)

    mBinding.amount.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) {
        amountPristine = false
        if (model.swapState.value != SwapState.NO_SWAP) {
          model.swapState.value = SwapState.SWAP_REQUIRED
        }
      }

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        refreshAmountConversionDisplay()
      }
    })

    mBinding.unit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) = Unit

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (model.swapState.value != SwapState.NO_SWAP) {
          model.swapState.value = SwapState.SWAP_REQUIRED
        }
        refreshAmountConversionDisplay()
      }
    }
  }

  override fun onStart() {
    super.onStart()

    appKit.nodeData.value?.let {
      mBinding.balanceValue.setAmount(it.balance)
    } ?: log.warn("balance is not available yet")

    mBinding.sendButton.setOnClickListener { _ ->
      model.invoice.value?.let {
        Wallet.hideKeyboard(context, mBinding.amount)
        val amount_opt = extractAmount()
        if (amount_opt.isDefined) {
          model.errorInAmount.value = false
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
      Wallet.hideKeyboard(context, mBinding.amount)
      model.invoice.value?.let {
        val amount_opt = extractAmount()
        if (amount_opt.isDefined) {
          model.errorInAmount.value = false
          val amount = amount_opt.get()
          if (it.isLeft) {
            model.setupSubmarineSwap(Converter.msat2sat(amount), it.left().get().first)
          }
        } else {
          model.errorInAmount.value = true
        }
      }
    }
  }

  private fun sendPaymentFinal(amount: MilliSatoshi, pr: PaymentRequest) {
    appKit.sendPaymentRequest(amount = amount, paymentRequest = pr, checkFees = false)
    findNavController().navigate(R.id.action_send_to_main)
  }

  private fun refreshAmountConversionDisplay() {
    context?.let {
      val amount = extractAmount(false)
      if (amount.isDefined) {
        val unit = mBinding.unit.selectedItem.toString()
        if (unit == Prefs.getFiatCurrency(it)) {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printAmountPretty(amount.get(), it, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(it, amount.get(), withUnit = true))
        }
        model.errorInAmount.value = false
      } else {
        mBinding.amountConverted.text = ""
        if (!amountPristine) {
          model.errorInAmount.value = true
        }
      }
    }
  }

  private fun extractAmount(showError: Boolean = true): Option<MilliSatoshi> {
    val unit = mBinding.unit.selectedItem.toString()
    val amount = mBinding.amount.text.toString()
    return try {
      if (unit == Prefs.getFiatCurrency(context!!)) {
        Option.apply(Converter.convertFiatToMsat(context!!, amount))
      } else {
        Converter.string2Msat_opt(amount, unit)
      }
    } catch (e: Exception) {
      log.error("could not extract amount: ${e.message}")
      model.errorInAmount.value = showError
      Option.empty()
    }
  }
}
