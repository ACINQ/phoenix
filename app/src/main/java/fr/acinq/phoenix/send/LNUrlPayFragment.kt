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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.AppKitModel
import fr.acinq.phoenix.databinding.FragmentLnurlPayBinding
import okhttp3.HttpUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LNUrlPayFragment : DialogFragment() {
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentLnurlPayBinding
  private lateinit var appKit: AppKitModel
  private lateinit var model: LNUrlPayViewModel
  private val args: LNUrlPayFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    mBinding = FragmentLnurlPayBinding.inflate(inflater, container, true)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    activity?.let {
      appKit = ViewModelProvider(it).get(AppKitModel::class.java)
      model = ViewModelProvider(this).get(LNUrlPayViewModel::class.java)
      mBinding.model = model
      model.baseUrl.value = HttpUrl.get(args.url)
    } ?: dismiss()
  }

  override fun onStart() {
    super.onStart()
  }

  private fun handleError(errorMessage: String) {
    model.state.postValue(LNUrlPayState.ERROR)
    mBinding.error.text = errorMessage
  }

  //  private fun getInvoiceFromRemote() {
  //    if (model.state.value == LNUrlPayState.IN_PROGRESS) {
  //      if (model.callbackUrl.value == null) {
  //        log.error("missing callback url when fetching invoice from lnurl-pay remote")
  //        handleError(getString(R.string.lnurl_pay_error_missing_callback))
  //      } else {
  //        val callbackUrl = model.callbackUrl.value!!
  //        log.info("fetching lnurl-pay invoice from remote $callbackUrl")
  //        Wallet.httpClient.newCall(Request.Builder().url(callbackUrl).build()).enqueue(object : Callback {
  //          override fun onFailure(call: Call, e: IOException) {
  //            log.error("failure when retrieving lnurl-pay invoice from callback url=${callbackUrl}: ", e)
  //            handleError(getString(R.string.lnurl_pay_error_remote, callbackUrl.host()))
  //          }
  //
  //          override fun onResponse(call: Call, response: Response) {
  //            val body = response.body()
  //            if (response.isSuccessful && body != null) {
  //              try {
  //                val json = JSONObject(body.string())
  //                log.debug("lnurl-pay invoice returns {}", json.toString(2))
  //                model.invoice.postValue(PaymentRequest.read(json.getString("pr")))
  //                model.state.postValue(LNUrlPayState.DONE)
  //              } catch (e: JSONException) {
  //                log.error("could not read lnurl-pay server response body: ", e)
  //                handleError(getString(R.string.lnurl_pay_error_remote, callbackUrl.host()))
  //              }
  //            } else {
  //              log.warn("could not retrieve lnurl-pay meta from remote, code=${response.code()}")
  //              handleError(getString(R.string.lnurl_pay_error_remote_code, callbackUrl.host(), response.code().toString()))
  //            }
  //          }
  //        })
  //      }
  //    }
  //  }
}

enum class LNUrlPayState {
  INIT, IN_PROGRESS, DONE, ERROR
}

enum class LNUrlPayMetadataTypes(paramType: String) {
  PLAIN_TEXT("text/plain"), IMAGE_JPG("image/png;base64"), IMAGE_PNG("image/jpeg;base64")
}

class LNUrlPayViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)

  val state = MutableLiveData(LNUrlPayState.INIT)
  val baseUrl = MutableLiveData<HttpUrl>()

  // payment invoice
  val invoice = MutableLiveData<PaymentRequest>()
}
