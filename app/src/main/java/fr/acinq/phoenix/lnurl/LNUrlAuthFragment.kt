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
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.Protocol
import fr.acinq.eclair.crypto.`Mac32$`
import fr.acinq.phoenix.BaseFragment
import fr.acinq.phoenix.R
import fr.acinq.phoenix.background.KitState
import fr.acinq.phoenix.databinding.FragmentLnurlAuthBinding
import fr.acinq.phoenix.utils.Converter
import fr.acinq.phoenix.utils.KitNotInitialized
import fr.acinq.phoenix.utils.tryWith
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scodec.bits.ByteVector
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteOrder

class LNUrlAuthFragment : BaseFragment() {
  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentLnurlAuthBinding
  private lateinit var model: LNUrlAuthViewModel
  private val args: LNUrlAuthFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentLnurlAuthBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(LNUrlAuthViewModel::class.java)
    mBinding.instructions.text = Converter.html(getString(R.string.lnurl_auth_instructions, args.url.topDomain))
    mBinding.progress.setText(Converter.html(getString(R.string.lnurl_auth_in_progress, args.url.topDomain)))
    model.state.observe(viewLifecycleOwner, Observer { state ->
      when (state) {
        is LNUrlAuthState.Error -> {
          val details = when (state.cause) {
            is LNUrlRemoteFailure.CouldNotConnect -> getString(R.string.lnurl_auth_failure_remote_io, args.url.topDomain)
            is LNUrlRemoteFailure.Detailed -> getString(R.string.lnurl_auth_failure_remote_details, state.cause.reason)
            is LNUrlRemoteFailure.Code -> "HTTP ${state.cause.code}"
            else -> state.cause.localizedMessage ?: state.cause.javaClass.simpleName
          }
          mBinding.failure.text = getString(R.string.lnurl_auth_failure, details)
        }
        is LNUrlAuthState.Done -> Handler().postDelayed({
          if (model.state.value is LNUrlAuthState.Done) {
            findNavController().popBackStack()
          }
        }, 3000)
        else -> Unit
      }
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction(View.OnClickListener { findNavController().popBackStack() })
    mBinding.loginButton.setOnClickListener { requestAuth() }
  }

  @UiThread
  private fun requestAuth() {
    lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("error in lnurl auth request to ${args.url.authEndpoint}: ", e)
      model.state.postValue(LNUrlAuthState.Error(e))
    }) {
      model.state.postValue(LNUrlAuthState.InProgress)
      val url = tryWith(InvalidAuthEndpoint) { HttpUrl.parse(args.url.authEndpoint)!! }
      val key = getAuthLinkingKey(url.topPrivateDomain()!!)
      val signedK1 = Crypto.compact2der(Crypto.sign(ByteVector32.fromValidHex(args.url.k1).bytes(), key)).toHex()
      val request = Request.Builder().url(url.newBuilder()
        .addQueryParameter("sig", signedK1)
        .addQueryParameter("key", key.publicKey().toString())
        .build())
        .get()
        .build()
      val response = try {
        LNUrl.httpClient.newCall(request).execute()
      } catch (e: IOException) {
        log.error("io error when authenticating with lnurl:", e)
        throw LNUrlRemoteFailure.CouldNotConnect
      }
      val json = LNUrl.handleLNUrlRemoteResponse(response)
      // if no failure, let's try to map to a pertinent state, with a fallback to Done.Authed
      delay(500)
      model.state.postValue(if (json.has("event")) {
        when (json.getString("event").toLowerCase()) {
          "registered" -> LNUrlAuthState.Done.Registered
          "loggedin" -> LNUrlAuthState.Done.LoggedIn
          "linked" -> LNUrlAuthState.Done.Linked
          "authed" -> LNUrlAuthState.Done.Authed
          else -> LNUrlAuthState.Done.Authed
        }
      } else {
        LNUrlAuthState.Done.Authed
      })
    }
  }

  private fun getAuthLinkingKey(message: String): Crypto.PrivateKey {
    val appState = app.state.value
    return if (appState is KitState.Started) {
      // 0 - the LNURL auth master key is the node key
      val key = DeterministicWallet.derivePrivateKey(appState.kit.nodeParams().keyManager().nodeKey(), DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/138'/0"))
      // 1 - get derivation path by hashing the service domain name
      val domainHash = `Mac32$`.`MODULE$`.hmac256(key.privateKey().value().bytes(), ByteVector.view(message.toByteArray(Charsets.UTF_8)))
      require(domainHash.bytes().size() >= 16) { "domain hash must be at least 16 chars" }
      val stream = ByteArrayInputStream(domainHash.bytes().slice(0, 16).toArray())
      val path1 = Protocol.uint32(stream, ByteOrder.BIG_ENDIAN)
      val path2 = Protocol.uint32(stream, ByteOrder.BIG_ENDIAN)
      val path3 = Protocol.uint32(stream, ByteOrder.BIG_ENDIAN)
      val path4 = Protocol.uint32(stream, ByteOrder.BIG_ENDIAN)
      val path = DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/138'/$path1/$path2/$path3/$path4")
      // 2 - build key that will be used to link with service
      DeterministicWallet.derivePrivateKey(key, path).privateKey()
    } else {
      throw KitNotInitialized
    }
  }
}

private object InvalidAuthEndpoint : RuntimeException()

sealed class LNUrlAuthState {
  object Init : LNUrlAuthState()
  object InProgress : LNUrlAuthState()
  sealed class Done : LNUrlAuthState() {
    object Registered : Done()
    object LoggedIn : Done()
    object Linked : Done()
    object Authed : Done()
  }

  data class Error(val cause: Throwable) : LNUrlAuthState()
}

class LNUrlAuthViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<LNUrlAuthState>()

  init {
    state.value = LNUrlAuthState.Init
  }
}
