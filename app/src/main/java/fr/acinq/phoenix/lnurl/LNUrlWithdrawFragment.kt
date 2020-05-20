/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.lnurl

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.eclair.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.AppViewModel
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentLnurlWithdrawBinding
import fr.acinq.phoenix.utils.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.Option
import java.io.IOException

class LNUrlWithdrawFragment : BaseFragment() {
  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentLnurlWithdrawBinding
  private lateinit var model: LNUrlWithdrawViewModel
  private val args: LNUrlWithdrawFragmentArgs by navArgs()

  private lateinit var unitList: List<String>

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentLnurlWithdrawBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.let {
      app = ViewModelProvider(it).get(AppViewModel::class.java)
      model = ViewModelProvider(this).get(LNUrlWithdrawViewModel::class.java)
      mBinding.model = model

      model.state.observe(viewLifecycleOwner, Observer { state ->
        context?.let { _ ->
          when (state) {
            is LNUrlWithdrawState.Error.Internal -> mBinding.error.text = getString(R.string.lnurl_withdraw_error_internal)
            is LNUrlWithdrawState.Error.InvalidRequest -> mBinding.error.text = getString(R.string.lnurl_withdraw_error_invalid)
            is LNUrlWithdrawState.Error.RemoteGeneric -> mBinding.error.text = getString(R.string.lnurl_withdraw_error_remote, model.topDomain.value, getString(R.string.utils_unknown))
            is LNUrlWithdrawState.Error.RemoteUnreadable -> mBinding.error.text = getString(R.string.lnurl_withdraw_error_remote, model.topDomain.value, getString(R.string.lnurl_withdraw_error_remote_invalid_response))
            is LNUrlWithdrawState.Error.RemoteWithDetails -> mBinding.error.text = getString(R.string.lnurl_withdraw_error_remote, model.topDomain.value, state.details)
            is LNUrlWithdrawState.Done -> mBinding.success.text = getString(R.string.lnurl_withdraw_success, model.topDomain.value)
            else -> {}
          }
        }
      })

      unitList = listOf(SatUnit.code(), BitUnit.code(), MBtcUnit.code(), BtcUnit.code(), Prefs.getFiatCurrency(it))
      ArrayAdapter(it, android.R.layout.simple_spinner_item, unitList).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.amountUnit.adapter = adapter
      }
      val unit = Prefs.getCoinUnit(it)
      mBinding.amountUnit.setSelection(unitList.indexOf(unit.code()))

    } ?: findNavController().navigate(R.id.action_lnurl_withdraw_to_main)
    model.url.value = args.url
    model.topDomain.value = HttpUrl.get(args.url.callback).topPrivateDomain()
  }

  override fun onStart() {
    super.onStart()

    mBinding.amountValue.addTextChangedListener(object : TextWatcher {
      override fun afterTextChanged(s: Editable?) = Unit

      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        checkAmount()
      }
    })

    mBinding.amountUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) = Unit

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        checkAmount()
      }
    }

    model.url.value?.let {
      try {
        val url = HttpUrl.get(it.callback)
        mBinding.confirmButton.setOnClickListener { _ -> sendWithdrawToRemote(url.newBuilder().addEncodedQueryParameter("k1", it.walletIdentifier), it.description) }
        mBinding.serviceHost.text = Converter.html(getString(R.string.lnurl_withdraw_service_host_label, model.topDomain.value))
        context?.let { ctx -> mBinding.amountValue.setText(Converter.printAmountRaw(it.maxWithdrawable, ctx)) }
        model.editableAmount.value = it.maxWithdrawable.toLong() != it.minWithdrawable.toLong()
      } catch (e: Exception) {
        log.error("error when reading lnurl-withdraw=$it")
        model.state.value = LNUrlWithdrawState.Error.InvalidRequest
      }
    }
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().navigate(R.id.action_lnurl_withdraw_to_main) })
  }

  private fun checkAmount(): Option<MilliSatoshi> {
    return try {
      val unit = mBinding.amountUnit.selectedItem.toString()
      val amountInput = mBinding.amountValue.text.toString()
      mBinding.amountError.text = ""
      val fiat = Prefs.getFiatCurrency(context!!)
      val amountOpt = if (unit == fiat) {
        Option.apply(Converter.convertFiatToMsat(context!!, amountInput))
      } else {
        Converter.string2Msat_opt(amountInput, unit)
      }
      if (amountOpt.isDefined) {
        val amount = amountOpt.get()
        if (amount.`$less`(args.url.minWithdrawable)) {
          throw LNUrlWithdrawAtLeastMinSat(args.url.minWithdrawable)
        } else if (amount.`$greater`(args.url.maxWithdrawable)) {
          throw LNUrlWithdrawAtMostMaxSat(args.url.maxWithdrawable)
        }
        if (unit == fiat) {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printAmountPretty(amount, context!!, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(context!!, amount, withUnit = true))
        }
      } else {
        throw RuntimeException("amount is undefined")
      }
      amountOpt
    } catch (e: Exception) {
      mBinding.amountConverted.text = ""
      mBinding.amountError.text = when (e) {
        is LNUrlWithdrawAtLeastMinSat -> getString(R.string.lnurl_withdraw_error_amount_min, context?.let { Converter.printAmountPretty(e.min, it, withUnit = true) } ?: "")
        is LNUrlWithdrawAtMostMaxSat -> getString(R.string.lnurl_withdraw_error_amount_max, context?.let { Converter.printAmountPretty(e.max, context!!, withUnit = true) } ?: "")
        else -> getString(R.string.lnurl_withdraw_error_amount)
      }
      Option.empty<MilliSatoshi>()
    }
  }

  private fun sendWithdrawToRemote(urlBuilder: HttpUrl.Builder, description: String) {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when sending callback call: ", exception)
      model.state.postValue(LNUrlWithdrawState.Error.Internal)
    }) {
      Wallet.hideKeyboard(context, mBinding.amountValue)
      if (model.state.value == LNUrlWithdrawState.Init) {
        val amount = checkAmount()
        if (!amount.isEmpty) {
          model.state.value = LNUrlWithdrawState.InProgress
          val pr = app.generatePaymentRequest(if (description.isBlank()) getString(R.string.receive_default_desc) else description, amount)
          val url = urlBuilder.addEncodedQueryParameter("pr", PaymentRequest.write(pr)).build()
          log.info("sending LNURL-withdraw request {}", url.toString())
          Wallet.httpClient.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
              log.error("remote error when sending lnurl-withdraw callback: ", e)
              model.state.postValue(LNUrlWithdrawState.Error.RemoteWithDetails(e.localizedMessage))
            }

            override fun onResponse(call: Call, response: Response) {
              try {
                val json = LNUrl.handleLNUrlRemoteResponse(response)
                log.debug("lnurl-withdraw remote responds with {}", json.toString(2))
                model.state.postValue(LNUrlWithdrawState.Done)
              } catch (e: Exception) {
                log.error("error in LNURL-withdraw callback remote: ", e)
                when (e) {
                  is JSONException -> model.state.postValue(LNUrlWithdrawState.Error.RemoteUnreadable)
                  is LNUrlRemoteFailure -> model.state.postValue(LNUrlWithdrawState.Error.RemoteWithDetails(e.localizedMessage))
                  else -> model.state.postValue(LNUrlWithdrawState.Error.RemoteGeneric)
                }
              }
            }
          })
        }
      }
    }
  }
}

sealed class LNUrlWithdrawState {
  object Init : LNUrlWithdrawState()
  object InProgress : LNUrlWithdrawState()
  object Done : LNUrlWithdrawState()
  sealed class Error : LNUrlWithdrawState() {
    object Internal : Error()
    object InvalidRequest : Error()
    object RemoteGeneric : Error()
    object RemoteUnreadable : Error()
    data class RemoteWithDetails(val details: String?) : Error()
  }
}

class LNUrlWithdrawViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)

  val state = MutableLiveData<LNUrlWithdrawState>(LNUrlWithdrawState.Init)
  val url = MutableLiveData<LNUrlWithdraw>()
  val topDomain = MutableLiveData("")
  val editableAmount = MutableLiveData(true)
}
