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

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.MilliSatoshi
import fr.acinq.eclair.phoenix.BaseFragment
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentSendBinding
import fr.acinq.eclair.phoenix.utils.Converter
import fr.acinq.eclair.phoenix.utils.Wallet
import fr.acinq.eclair.phoenix.utils.customviews.CoinView

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

    model.invoice.observe(viewLifecycleOwner, Observer {
      it?.let {
        when {
          it.isLeft && it.left().get().second == null -> {
            mBinding.sendButton.setText(getString(R.string.send_pay_button_for_swap))
            mBinding.sendButton.setIcon(resources.getDrawable(R.drawable.ic_arrow_next, activity?.theme))
            mBinding.sendButton.background = resources.getDrawable(R.drawable.button_bg_square, activity?.theme)
            it.left().get().first.amount?.let { amount -> mBinding.amount.setAmount(amount) }
          }
          it.isLeft && it.left().get().second != null
            && it.left().get().second!!.amount().isDefined && context != null && mBinding.amount.getAmount().isDefined -> {
            val fee = it.left().get().second!!.amount().get().amount() - mBinding.amount.getAmount().get().amount()
            mBinding.swapInstructions.text = getString(R.string.send_swap_confirm_instructions,
              Converter.formatAmount(amount = it.left().get().second!!.amount().get(), context = context!!, withUnit = true),
              Converter.formatAmount(amount = MilliSatoshi(fee), context = context!!, withUnit = true))
          }
          it.isRight && it.right().get().amount().isDefined -> mBinding.amount.setAmount(it.right().get().amount().get())
          else -> {}
        }
      }
    })

    model.checkAndSetPaymentRequest(args.invoice)

    mBinding.amount.setAmountWatcher(object : CoinView.CoinViewWatcher() {
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        mBinding.amount.handleEmptyAmountIfEditable()
      }
    })
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
        val amount_opt = mBinding.amount.getAmount()
        if (amount_opt.isDefined) {
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
          log.info("empty amount!")
          model.state.value = SendState.ERROR_IN_AMOUNT
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
}
