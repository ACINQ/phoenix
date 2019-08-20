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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.`BtcUnit$`
import fr.acinq.eclair.`MBtcUnit$`
import fr.acinq.eclair.`SatUnit$`
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSendBinding
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.Prefs
import fr.acinq.eclair.phoenix.utils.Wallet
import scala.Option

class SendFragment : BaseFragment() {

  private lateinit var mBinding: FragmentSendBinding
  private val args: SendFragmentArgs by navArgs()

  private lateinit var model: SendViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentSendBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(SendViewModel::class.java)
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
          when {
            // invoice is a bitcoin uri not swapped yet
            it.isLeft && it.left().get().second == null -> {
              mBinding.sendButton.setText(getString(R.string.send_pay_button_for_swap))
              mBinding.sendButton.setIcon(resources.getDrawable(R.drawable.ic_arrow_next, activity?.theme))
              mBinding.sendButton.background = resources.getDrawable(R.drawable.button_bg_square, activity?.theme)
              it.left().get().first.amount?.let { amount -> mBinding.amount.setText(Converter.printAmountRaw(amount, ctx)) }
            }
            // invoice is a bitcoin uri with a swap PR
            it.isLeft && it.left().get().second != null && it.left().get().second!!.amount().isDefined -> {
              try {
                val amountInput = extractAmount()
                if (amountInput.isDefined) {
                  val fee = it.left().get().second!!.amount().get().amount() - amountInput.get().amount()
                  mBinding.swapInstructions.text = getString(R.string.send_swap_confirm_instructions,
                    Converter.printAmountPretty(amount = it.left().get().second!!.amount().get(), context = context!!, withUnit = true),
                    Converter.printAmountPretty(amount = MilliSatoshi(fee), context = context!!, withUnit = true))
                }
                Unit
              } catch (e: Exception) {
                log.error("could not extract amount when showing swap instructions: ", e)
              }
            }
            // invoice is a payment request
            it.isRight && it.right().get().amount().isDefined -> mBinding.amount.setText(Converter.printAmountRaw(it.right().get().amount().get(), ctx))
            else -> {
            }
          }
        }
      }
    })

    model.checkAndSetPaymentRequest(args.invoice)

    mBinding.amount.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) {}

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        refreshAmountConversionDisplay()
      }
    })

    mBinding.unit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) {
      }

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        refreshAmountConversionDisplay()
      }
    }
  }

  override fun onStart() {
    super.onStart()

    appKit.nodeData.value?.let {
      mBinding.balanceValue.setAmount(it.balance)
    } ?: log.warn("balance is not available yet")

    mBinding.swapCancelButton.setOnClickListener {
      findNavController().navigate(R.id.action_send_to_main)
    }

    mBinding.sendButton.setOnClickListener { _ ->
      model.invoice.value?.let {
        Wallet.hideKeyboard(context, mBinding.amount)
        try {
          val amount_opt = extractAmount()
          if (amount_opt.isDefined) {
            model.errorInAmount.value = false
            when {
              it.isLeft -> {
                // onchain payment must first be swapped
                model.sendSubmarineSwap(Converter.msat2sat(amount_opt.get()), it.left().get().first)
              }
              it.isRight -> {
                appKit.sendPaymentRequest(amount_opt.get(), it.right().get())
                findNavController().navigate(R.id.action_send_to_main)
              }
            }
          } else {
            throw RuntimeException("empty amount")
          }
        } catch (e: Exception) {
          log.error("could not extract amount: ", e)
          model.errorInAmount.value = true
        }
      }
    }

    mBinding.swapConfirmButton.setOnClickListener { _ ->
      model.invoice.value?.let {
        if (it.isLeft && it.left().get().second != null && it.left().get().second!!.amount().isDefined) {
          val pr = it.left().get().second
          appKit.sendPaymentRequest(amount = pr!!.amount().get(), paymentRequest = pr, checkFees = false)
          findNavController().navigate(R.id.action_send_to_main)
        }
      }

    }

    mBinding.cancelButton.setOnClickListener {
      findNavController().navigate(R.id.action_send_to_main)
    }
  }

  private fun refreshAmountConversionDisplay() {
    context?.let {
      try {
        val amount = extractAmount()
        val unit = mBinding.unit.selectedItem.toString()
        if (unit == Prefs.getFiatCurrency(it)) {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printAmountPretty(amount.get(), it, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(it, amount.get(), withUnit = true))
        }
        model.errorInAmount.value = false
      } catch (e: Exception) {
        log.info("could not extract amount: ", e)
        mBinding.amountConverted.text = ""
        model.errorInAmount.value = true
      }
    }
  }

  private fun extractAmount(): Option<MilliSatoshi> {
    val unit = mBinding.unit.selectedItem.toString()
    val amount = mBinding.amount.text.toString()
    return if (unit == Prefs.getFiatCurrency(context!!)) {
      Option.apply(Converter.convertFiatToMsat(context!!, amount))
    } else {
      Converter.string2Msat_opt(amount, unit)
    }
  }
}
