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

package fr.acinq.phoenix.legacy.lnurl

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import fr.acinq.bitcoin.scala.ByteVector32
import fr.acinq.bitcoin.scala.Crypto
import fr.acinq.bitcoin.scala.DeterministicWallet
import fr.acinq.bitcoin.scala.Protocol
import fr.acinq.eclair.crypto.`Mac32$`
import fr.acinq.phoenix.legacy.BaseFragment
import fr.acinq.phoenix.legacy.R
import fr.acinq.phoenix.legacy.background.KitState
import fr.acinq.phoenix.legacy.databinding.FragmentLnurlAuthBinding
import fr.acinq.phoenix.legacy.utils.Converter
import fr.acinq.phoenix.legacy.utils.KitNotInitialized
import fr.acinq.phoenix.legacy.utils.LangExtensions.findNavControllerSafe
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
import java.util.*

class LNUrlAuthFragment : BaseFragment() {
  override val log: Logger = LoggerFactory.getLogger(this::class.java)

  private lateinit var mBinding: FragmentLnurlAuthBinding
  private val args: LNUrlAuthFragmentArgs by navArgs()
  private lateinit var model: LNUrlAuthViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentLnurlAuthBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    val url = try {
      HttpUrl.parse(args.url.url)!!
    } catch (e: Exception) {
      log.error("could not parse url=${args.url.url}: ", e)
      findNavControllerSafe()?.popBackStack()
      return
    }
    model = ViewModelProvider(this, LNUrlAuthViewModel.Factory(url)).get(LNUrlAuthViewModel::class.java)
    model.state.observe(viewLifecycleOwner, { state ->
      when (state) {
        is LNUrlAuthState.Error -> {
          val details = when (state.cause) {
            is LNUrlError.RemoteFailure.CouldNotConnect -> getString(R.string.lnurl_auth_failure_remote_io, model.domainToSignIn)
            is LNUrlError.RemoteFailure.Detailed -> getString(R.string.lnurl_auth_failure_remote_details, state.cause.reason)
            is LNUrlError.RemoteFailure.Code -> getString(R.string.lnurl_auth_failure_remote_details, "HTTP ${state.cause.code}")
            else -> state.cause.localizedMessage ?: state.cause.javaClass.simpleName
          }
          mBinding.errorMessage.text = getString(R.string.lnurl_auth_failure, details)
        }
        is LNUrlAuthState.Done -> Handler().postDelayed({
          if (model.state.value is LNUrlAuthState.Done) {
            findNavControllerSafe()?.popBackStack()
          }
        }, 3000)
        else -> Unit
      }
    })
    mBinding.instructions.text = Converter.html(getString(R.string.lnurl_auth_instructions, model.domainToSignIn))
    mBinding.progress.setText(Converter.html(getString(R.string.lnurl_auth_in_progress, model.domainToSignIn)))
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    mBinding.actionBar.setOnBackAction { findNavController().popBackStack() }
    mBinding.loginButton.setOnClickListener { requestAuth() }
  }

  @UiThread
  private fun requestAuth() {
    lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("error in lnurl auth request to ${args.url.url}: ", e)
      model.state.postValue(LNUrlAuthState.Error(e))
    }) {
      model.state.postValue(LNUrlAuthState.InProgress)
      val domain = model.domainToSignIn
      val key = getAuthLinkingKey(domain)
      val signedK1 = Crypto.compact2der(Crypto.sign(ByteVector32.fromValidHex(args.url.k1).bytes(), key)).toHex()
      val request = Request.Builder().url(model.url.newBuilder()
        .addQueryParameter("sig", signedK1)
        .addQueryParameter("key", key.publicKey().toString())
        .build())
        .get()
        .build()
      val response = try {
        LNUrl.httpClient.newCall(request).execute()
      } catch (e: IOException) {
        log.error("io error when authenticating with lnurl on domain=$domain: ", e)
        throw LNUrlError.RemoteFailure.CouldNotConnect(domain)
      }
      val json = LNUrl.handleLNUrlRemoteResponse(response)
      // if no failure, let's try to map to a pertinent state, with a fallback to Done.Authed
      delay(500)
      model.state.postValue(if (json.has("event")) {
        when (json.getString("event").toLowerCase(Locale.ROOT)) {
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

class LNUrlAuthViewModel(val url: HttpUrl) : ViewModel() {
  private val log = LoggerFactory.getLogger(this::class.java)
  val state = MutableLiveData<LNUrlAuthState>(LNUrlAuthState.Init)

  /**
   * The domain used for the signing key is the effective top level domain + 1, or the host
   * if null (e.g. localhost...). It's better to use eTLD + 1 because the service's lnurl
   * (including sub domains) can change which would in turn change the signing key, i.e lock
   * users out of their accounts.
   */
  val domainToSignIn: String = url.topPrivateDomain() ?: url.host()

  class Factory(private val url: HttpUrl) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return LNUrlAuthViewModel(url) as T
    }
  }
}
