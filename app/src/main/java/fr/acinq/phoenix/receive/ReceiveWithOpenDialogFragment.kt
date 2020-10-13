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

import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.phoenix.AppViewModel
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentReceiveWithOpenBinding
import fr.acinq.phoenix.utils.Converter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.DateFormat
import java.util.*
import kotlin.math.max


open class ReceiveWithOpenDialogFragment : DialogFragment() {

  val log: Logger = LoggerFactory.getLogger(ReceiveWithOpenDialogFragment::class.java)
  private val args: ReceiveWithOpenDialogFragmentArgs by navArgs()

  lateinit var mBinding: FragmentReceiveWithOpenBinding
  private lateinit var app: AppViewModel
  private lateinit var model: ReceiveWithOpenViewModel

  private val SAFETY_MARGIN = 20 * 1000L

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentReceiveWithOpenBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.run {
      val timeRemaining = (args.expireAt * 1000) - System.currentTimeMillis() - SAFETY_MARGIN
      log.info("display popup for payToOpen request [ payment_hash=${args.paymentHash} amount_msat=${args.amountMsat} fee_sat=${args.feeSat} funding_sat=${args.fundingSat} expire_at=${DateFormat.getDateTimeInstance().format(args.expireAt * 1000)} time_remaining=${timeRemaining}ms]")

      app = ViewModelProvider(this).get(AppViewModel::class.java)
      model = ViewModelProvider(this, ReceiveWithOpenViewModelFactory(timeRemaining)).get(ReceiveWithOpenViewModel::class.java)
      mBinding.model = model

      model.timeToExpiry.observe(viewLifecycleOwner, Observer {
        mBinding.acceptButton.setDefaultText(getString(R.string.receive_with_open_accept, max(it / 1000, 0).toString()))
        if (it <= 0) {
          log.info("pay to open with payment_hash=${args.paymentHash} has expired and is declined")
          app.service?.rejectPayToOpen(ByteVector32.fromValidHex(args.paymentHash))
        }
      })

      this.onBackPressedDispatcher.addCallback(this) {
        log.debug("back pressed disabled here")
      }
    } ?: dismiss()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    context?.let {
      mBinding.amountValue.text = Converter.printAmountPretty(MilliSatoshi(args.amountMsat), it, withUnit = true)
      mBinding.amountFiat.text = Converter.html(getString(R.string.utils_converted_amount, Converter.printFiatPretty(it, MilliSatoshi(args.amountMsat), withUnit = true)))
      mBinding.cost.text = Html.fromHtml(getString(R.string.receive_with_open_cost,
        Converter.printAmountPretty(Satoshi(args.feeSat), it, withUnit = true),
        Converter.printFiatPretty(it, Converter.any2Msat(Satoshi(args.feeSat)), withUnit = true)))
    }
  }

  override fun onStart() {
    super.onStart()
    mBinding.acceptButton.setOnClickListener {
      app.service?.apply {
        log.info("accepting pay-to-open with payment_hash=${args.paymentHash} fee=${args.feeSat}")
        acceptPayToOpen(ByteVector32.fromValidHex(args.paymentHash))
        dismiss()
      }
    }
    mBinding.declineButton.setOnClickListener {
      log.info("declining pay-to-open with payment_hash=${args.paymentHash}")
      app.service?.apply {
        rejectPayToOpen(ByteVector32.fromValidHex(args.paymentHash))
        dismiss()
      }
    }
    mBinding.dismissButton.setOnClickListener { dismiss() }
    mBinding.helpButton.setOnClickListener {
      model.showHelp.value?.let {
        model.showHelp.value = !it
      }
    }
  }

  override fun onCancel(dialog: DialogInterface) {
    app.service?.rejectPayToOpen(ByteVector32.fromValidHex(args.paymentHash))
    super.onCancel(dialog)
  }
}

private class ReceiveWithOpenViewModelFactory(private val timeRemainingMs: Long): ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T = ReceiveWithOpenViewModel(timeRemainingMs) as T
}

class ReceiveWithOpenViewModel(timeRemainingMs: Long) : ViewModel() {
  private val log = LoggerFactory.getLogger(ReceiveWithOpenViewModel::class.java)
  private val timer: Timer = Timer()
  val showHelp = MutableLiveData(false)
  val timeToExpiry = MutableLiveData(timeRemainingMs)

  init {
    timer.schedule(object : TimerTask() {
      override fun run() {
        timeToExpiry.value?.let {
          val countdown = max(0, it - 1000)
          timeToExpiry.postValue(max(0, it - 1000))
          if (countdown <= 0) {
            timer.cancel()
          }
        }
      }
    }, 0, 1000)
  }

  override fun onCleared() {
    super.onCleared()
    timer.cancel()
  }
}
