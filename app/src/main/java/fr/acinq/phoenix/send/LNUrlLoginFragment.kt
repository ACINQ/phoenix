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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.databinding.FragmentLnurlLoginBinding
import fr.acinq.phoenix.utils.Constants
import fr.acinq.phoenix.utils.Wallet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LNUrlLoginFragment : BaseFragment() {

  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentLnurlLoginBinding
  private lateinit var model: LNUrlLoginViewModel
  private val args: LNUrlLoginFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentLnurlLoginBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(LNUrlLoginViewModel::class.java)
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    if (model.state.value == LNUrlLoginState.INIT) {
      executeLoginRequest()
    }
  }

  private fun executeLoginRequest() {
    lifecycleScope.launch(CoroutineExceptionHandler { _, exception ->
      log.error("error when logging into ${args.uri} using LNUrl: ", exception)
      model.state.value = LNUrlLoginState.ERROR
    }) {
      model.state.value = LNUrlLoginState.IN_PROGRESS
      val json = JSONObject()
      val request = Request.Builder().url(args.uri).post(RequestBody.create(Constants.JSON, json.toString())).build()
      val response = Wallet.httpClient.newCall(request).execute()
      val body = response.body()
      if (response.isSuccessful && body != null) {
      } else {
        throw RuntimeException("LNUrl login failed with code=${response.code()}")
      }
    }
  }
}

enum class LNUrlLoginState {
  INIT, IN_PROGRESS, DONE, ERROR
}

class LNUrlLoginViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<LNUrlLoginState>()

  init {
    state.value = LNUrlLoginState.INIT
  }
}
