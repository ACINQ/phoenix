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

package fr.acinq.phoenix.legacy.lnurl

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.scala.Crypto
import fr.acinq.eclair.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.databinding.FragmentLnurlPayBinding
import fr.acinq.phoenix.legacy.db.AppDb
import fr.acinq.phoenix.legacy.db.LNUrlPayActionData
import fr.acinq.phoenix.legacy.db.PaymentMetaRepository
import fr.acinq.phoenix.legacy.utils.AlertHelper
import fr.acinq.phoenix.legacy.utils.Converter
import fr.acinq.phoenix.legacy.utils.Prefs
import fr.acinq.phoenix.legacy.utils.Wallet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.Option
import scodec.bits.ByteVector
import java.io.IOException
import java.util.*
import kotlin.random.Random

class LNUrlPayFragment : BaseFragment() {
  override val log: Logger = LoggerFactory.getLogger(this::class.java)
  private lateinit var mBinding: FragmentLnurlPayBinding
  private lateinit var model: LNUrlPayViewModel
  private val args: LNUrlPayFragmentArgs by navArgs()

  private lateinit var unitList: List<String>

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentLnurlPayBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    val callbackUrl = try {
      HttpUrl.parse(args.url.callbackUrl)!!
    } catch (e: Exception) {
      log.error("could not parse url=${args.url.callbackUrl}: ", e)
      findNavController().popBackStack()
      return
    }

    model = ViewModelProvider(this, LNUrlPayViewModel.Factory(
      callbackUrl,
      args.url.minSendable,
      args.url.maxSendable,
      args.url.rawMetadata,
      args.url.maxCommentLength,
      PaymentMetaRepository.getInstance(AppDb.getInstance(requireContext()).paymentMetaQueries)
    )).get(LNUrlPayViewModel::class.java)

    context?.let { ctx ->
      unitList = listOf(SatUnit.code(), BitUnit.code(), MBtcUnit.code(), BtcUnit.code(), Prefs.getFiatCurrency(ctx))
      ArrayAdapter(ctx, android.R.layout.simple_spinner_item, unitList).also { adapter ->
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBinding.unit.adapter = adapter
      }
      val unit = Prefs.getCoinUnit(ctx)
      mBinding.unit.setSelection(unitList.indexOf(unit.code()))
      mBinding.amount.setText(Converter.printAmountRaw(model.minSendable, ctx))
      mBinding.amount.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) = Unit

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
          checkAndGetAmount()
        }
      })
      mBinding.unit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
          checkAndGetAmount()
        }
      }
      mBinding.metadata.text = model.metadata.plainText
      mBinding.domain.text = Converter.html(getString(R.string.lnurl_pay_domain, callbackUrl.host()))
    }

    model.state.observe(viewLifecycleOwner, { state ->
      when (state) {
        is LNUrlPayState.Error -> mBinding.errorMessage.text = when (state.cause) {
          is InvoiceChainDoesNotMatch -> getString(R.string.lnurl_pay_error_invalid_invoice, model.callbackUrl.host(), getString(R.string.scan_error_invalid_chain))
          is LNUrlPayInvalidResponse -> getString(R.string.lnurl_pay_error_invalid_invoice, model.callbackUrl.host(), state.cause.message)
          is LNUrlError.RemoteFailure.CouldNotConnect -> getString(R.string.lnurl_pay_error_unreachable, model.callbackUrl.host())
          is LNUrlError.RemoteFailure.Detailed -> getString(R.string.lnurl_pay_error_remote_error, model.callbackUrl.host(), state.cause.reason)
          is LNUrlError.RemoteFailure.Code -> getString(R.string.lnurl_pay_error_remote_error, model.callbackUrl.host(), "HTTP ${state.cause.code}")
          else -> getString(R.string.lnurl_pay_error_remote_error, model.callbackUrl.host(), state.cause.localizedMessage ?: state.cause.javaClass.simpleName)
        }
        is LNUrlPayState.RequestingInvoice -> mBinding.sendingPaymentProgress.setText(getString(R.string.lnurl_pay_sending_payment))
        is LNUrlPayState.ValidInvoice -> {
          mBinding.sendingPaymentProgress.setText(getString(R.string.lnurl_pay_checking_invoice))
          sendPayment(state.paymentRequest, state.action)
        }
        is LNUrlPayState.SendingPayment -> mBinding.sendingPaymentProgress.setText(getString(R.string.lnurl_pay_sending_payment))
        else -> Unit
      }
    })

    model.amountState.observe(viewLifecycleOwner, { state ->
      val ctx = requireContext()
      mBinding.amountError.text = when (state) {
        LNUrlPayAmountState.Pristine -> ""
        LNUrlPayAmountState.Error.Empty -> getString(R.string.lnurl_pay_amount_empty)
        LNUrlPayAmountState.Error.BelowMin -> getString(R.string.lnurl_pay_amount_below_min, Converter.printAmountPretty(model.minSendable, ctx, withUnit = true))
        LNUrlPayAmountState.Error.AboveMax -> getString(R.string.lnurl_pay_amount_above_max, Converter.printAmountPretty(model.maxSendable, ctx, withUnit = true))
        LNUrlPayAmountState.Error.AboveBalance -> getString(R.string.lnurl_pay_amount_above_balance)
        LNUrlPayAmountState.Error.Invalid -> getString(R.string.lnurl_pay_amount_invalid)
      }
    })

    if (model.metadata.image != null) {
      mBinding.metadataImage.setImageBitmap(model.metadata.image)
    } else {
      mBinding.metadataImage.visibility = View.GONE
    }
    mBinding.appModel = app
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction { findNavController().navigate(R.id.action_lnurl_pay_to_main) }
    mBinding.startPaymentButton.setOnClickListener {
      val maxCommentLength = model.maxCommentLength ?: 0
      if (maxCommentLength > 0) {
        AlertHelper.buildWithInput(layoutInflater,
          title = getString(R.string.lnurl_pay_comment_title),
          message = getString(R.string.lnurl_pay_comment_message, maxCommentLength),
          inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
          maxLines = 3,
          maxLength = maxCommentLength.toInt(),
          callback = { comment -> requestInvoice(comment) },
          defaultValue = "",
          hint = getString(R.string.lnurl_pay_comment_hint))
          .show()
      } else {
        requestInvoice(null)
      }
    }
    mBinding.tryAgainButton.setOnClickListener { model.state.value = LNUrlPayState.Init }
  }

  /**
   * 1) Check that the amount is within the LNUrlPay min/max, and below the wallet's balance.
   * 2) Refresh the converted amount display.
   * 3) Update the amount error state accordingly.
   * Return the amount if valid, null otherwise.
   */
  private fun checkAndGetAmount(): MilliSatoshi? {
    return try {
      val ctx = requireContext()
      val unit = mBinding.unit.selectedItem.toString()
      val amountInput = mBinding.amount.text.toString()
      val balance = appContext(requireContext()).balance.value
      model.amountState.value = LNUrlPayAmountState.Pristine
      val fiat = Prefs.getFiatCurrency(ctx)
      val amountOpt = if (unit == fiat) {
        Option.apply(Converter.convertFiatToMsat(ctx, amountInput))
      } else {
        Converter.string2Msat_opt(amountInput, unit)
      }
      if (amountOpt.isDefined) {
        val amount = amountOpt.get()
        if (unit == fiat) {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printAmountPretty(amount, ctx, withUnit = true))
        } else {
          mBinding.amountConverted.text = getString(R.string.utils_converted_amount, Converter.printFiatPretty(ctx, amount, withUnit = true))
        }
        if (balance != null && amount.`$greater`(balance.sendable)) {
          model.amountState.value = LNUrlPayAmountState.Error.AboveBalance
          null
        } else if (amount.`$less`(model.minSendable)) {
          model.amountState.value = LNUrlPayAmountState.Error.BelowMin
          null
        } else if (amount.`$greater`(model.maxSendable)) {
          model.amountState.value = LNUrlPayAmountState.Error.AboveMax
          null
        } else {
          amount
        }
      } else {
        model.amountState.value = LNUrlPayAmountState.Error.Empty
        mBinding.amountConverted.text = ""
        null
      }
    } catch (e: Exception) {
      log.debug("could not check amount: ${e.message}")
      mBinding.amountConverted.text = ""
      model.amountState.value = LNUrlPayAmountState.Error.Invalid
      null
    }
  }

  @UiThread
  private fun requestInvoice(comment: String?) {
    model.state.value = LNUrlPayState.RequestingInvoice
    val amount = checkAndGetAmount()
    if (amount != null) {
      lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        log.error("error when requesting invoice from url=${model.callbackUrl}: ", e)
        model.state.postValue(LNUrlPayState.Error(e))
      }) {
        val request = Request.Builder().url(model.callbackUrl.newBuilder()
          .addQueryParameter("amount", amount.toLong().toString())
          .addQueryParameter("nonce", Random.nextBytes(8).toString(Charsets.UTF_8))
          .addQueryParameter("comment", comment)
          .build())
          .get()
          .build()
        val response = try {
          LNUrl.httpClient.newCall(request).execute()
        } catch (e: IOException) {
          log.error("io error when requesting invoice from url=${model.callbackUrl}: ", e)
          throw LNUrlError.RemoteFailure.CouldNotConnect(model.callbackUrl.toString())
        }
        val json = LNUrl.handleLNUrlRemoteResponse(response)
        val pr = PaymentRequest.read(json.getString("pr"))
        val action = json.optJSONObject("successAction")?.run { extractAction(this) }
        if (!Wallet.isSupportedPrefix(pr)) {
          throw InvoiceChainDoesNotMatch(pr)
        } else if (pr.amount().isEmpty) {
          throw IllegalArgumentException("invoice does not define an amount")
        } else if (!pr.amount().get().equals(amount)) {
          throw InvoiceAmountDoesNotMatch(pr, amount)
        } else if (!pr.description().isRight) {
          throw InvoiceNoDescriptionHash(pr)
        } else if (!pr.description().right().get().equals(Crypto.sha256().apply(ByteVector.encodeUtf8(model.metadata.raw).right().get()))) {
          throw InvoiceDescriptionHashDoesNotMatch(pr, model.metadata.raw)
        } else {
          model.state.postValue(LNUrlPayState.ValidInvoice(pr, action))
        }
      }
    } else {
      model.state.postValue(LNUrlPayState.Init)
    }
  }

  private fun extractAction(json: JSONObject): LNUrlPayActionData = when (json.getString("tag").toLowerCase(Locale.US)) {
    "url" -> LNUrlPayActionData.Url.V0(description = json.getString("description"), url = json.getString("url"))
    "message" -> LNUrlPayActionData.Message.V0(message = json.getString("message"))
    "aes" -> LNUrlPayActionData.Aes.V0(description = json.getString("description"), cipherText = json.getString("ciphertext"), iv = json.getString("iv").run {
      if (length != 24) {
        throw IllegalArgumentException("invalid length")
      } else {
        Base64.decode(this, Base64.DEFAULT)
        this
      }
    })
    else -> throw UnhandledLNUrlPaySuccessAction(json.toString())
  }

  private fun sendPayment(pr: PaymentRequest, action: LNUrlPayActionData?) {
    model.state.value = LNUrlPayState.SendingPayment(pr)
    lifecycleScope.launch(Dispatchers.Main + CoroutineExceptionHandler { _, e ->
      log.error("error when sending payment in state=${model.state}: ", e)
      model.state.value = LNUrlPayState.Error(e)
    }) {
      log.info("sending payment for invoice=$pr")
      val paymentId = app.requireService.sendPaymentRequest(amount = pr.amount().get(), paymentRequest = pr, subtractFee = false)
      if (paymentId != null) {
        model.saveLNUrlInfo(paymentId.toString(), action)
      }
      findNavController().navigate(R.id.action_lnurl_pay_to_main)
    }
  }

}

sealed class LNUrlPayInvalidResponse(override val message: String) : IllegalArgumentException(message)
data class UnhandledLNUrlPaySuccessAction(val json: String) : LNUrlPayInvalidResponse("unhandled success action=$json")
data class InvoiceNoDescriptionHash(val pr: PaymentRequest) : LNUrlPayInvalidResponse("invoice must have a description hash")
data class InvoiceDescriptionHashDoesNotMatch(val pr: PaymentRequest, val expected: String) : LNUrlPayInvalidResponse("invoice description does not match metadata")
data class InvoiceChainDoesNotMatch(val pr: PaymentRequest) : LNUrlPayInvalidResponse("invoice does not match wallet's chain")
data class InvoiceAmountDoesNotMatch(val pr: PaymentRequest, val expected: MilliSatoshi) :
  LNUrlPayInvalidResponse("invoice does not match expected amount [ expected=$expected actual=${pr.amount()} ]")

sealed class LNUrlPayAmountState {
  object Pristine : LNUrlPayAmountState()
  sealed class Error : LNUrlPayAmountState() {
    object BelowMin : Error()
    object AboveMax : Error()
    object AboveBalance : Error()
    object Invalid : Error()
    object Empty : Error()
  }
}

sealed class LNUrlPayState {
  object Init : LNUrlPayState()
  object RequestingInvoice : LNUrlPayState()
  data class ValidInvoice(val paymentRequest: PaymentRequest, val action: LNUrlPayActionData?) : LNUrlPayState()
  data class SendingPayment(val paymentRequest: PaymentRequest) : LNUrlPayState()
  data class Error(val cause: Throwable) : LNUrlPayState()
}

class LNUrlPayViewModel(
  val callbackUrl: HttpUrl,
  val minSendable: MilliSatoshi,
  val maxSendable: MilliSatoshi,
  val metadata: LNUrlPayMetadata,
  val maxCommentLength: Long?,
  private val paymentMetaRepository: PaymentMetaRepository,
) : ViewModel() {

  val state = MutableLiveData<LNUrlPayState>(LNUrlPayState.Init)
  val amountState = MutableLiveData<LNUrlPayAmountState>(LNUrlPayAmountState.Pristine)

  suspend fun saveLNUrlInfo(paymentId: String, action: LNUrlPayActionData?) {
    paymentMetaRepository.saveLNUrlPayInfo(paymentId, callbackUrl, metadata, action)
  }

  class Factory(
    val url: HttpUrl,
    val minSendable: MilliSatoshi,
    val maxSendable: MilliSatoshi,
    val metadata: LNUrlPayMetadata,
    val maxCommentLength: Long?,
    val paymentMetaRepository: PaymentMetaRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return LNUrlPayViewModel(url, minSendable, maxSendable, metadata, maxCommentLength, paymentMetaRepository) as T
    }
  }
}
