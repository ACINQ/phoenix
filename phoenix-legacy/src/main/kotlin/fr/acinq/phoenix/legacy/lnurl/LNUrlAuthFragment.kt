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
import fr.acinq.eclair.crypto.KeyManager
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
    model.state.observe(viewLifecycleOwner) { state ->
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
    }
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
      val appState = app.state.value

      val (signedK1, authKey) = if (appState is KitState.Started) {
        signLnurlAuthK1WithKey(keyManager = appState.kit.nodeParams().keyManager(), k1 = args.url.k1, url = model.url)
      } else {
        throw KitNotInitialized
      }

      val request = Request.Builder().url(
        model.url.newBuilder()
          .addQueryParameter("sig", signedK1)
          .addQueryParameter("key", authKey.publicKey().toString())
          .build()
      )
        .get()
        .build()
      val response = try {
        LNUrl.httpClient.newCall(request).execute()
      } catch (e: IOException) {
        log.error("io error when authenticating with lnurl on domain=${model.domainToSignIn}: ", e)
        throw LNUrlError.RemoteFailure.CouldNotConnect(model.domainToSignIn)
      }
      val json = LNUrl.handleLNUrlRemoteResponse(response)
      // if no failure, let's try to map to a pertinent state, with a fallback to Done.Authed
      delay(500)
      model.state.postValue(
        if (json.has("event")) {
          when (json.getString("event").lowercase(Locale.ROOT)) {
            "registered" -> LNUrlAuthState.Done.Registered
            "loggedin" -> LNUrlAuthState.Done.LoggedIn
            "linked" -> LNUrlAuthState.Done.Linked
            "authed" -> LNUrlAuthState.Done.Authed
            else -> LNUrlAuthState.Done.Authed
          }
        } else {
          LNUrlAuthState.Done.Authed
        }
      )
    }
  }

  companion object {

    /**
     * Domains where we should use a legacy path instead of the regular full domain, for
     * backward compatibility reasons.
     *
     * Those services are listed as using LUD-04 on the lnurl specs:
     * https://github.com/fiatjaf/lnurl-rfc/tree/38d8baa6f8e3b3dfd13649bfa79e2175d6ca42ff#services
     */
    private enum class LegacyDomain(val host: String, val legacyCompatDomain: String) {
      GEYSER("auth.geyser.fund", "geyser.fund"),
      KOLLIDER("api.kollider.xyz", "kollider.xyz"),
      LNMARKETS("api.lnmarkets.com", "lnmarkets.com"),
      // LNBITS("", ""),
      GETALBY("getalby.com", "getalby.com"),
      LIGHTNING_VIDEO("lightning.video", "lightning.video"),
      LOFT("api.loft.trade", "loft.trade"),
      // WHEEL_OF_FORTUNE("", ""),
      // COINOS("", ""),
      LNSHORT("lnshort.it", "lnshort.it"),
      STACKERNEWS("stacker.news", "stacker.news"),
      ;
    }

    /** Get the domain for the given [HttpUrl]. If eligible returns a legacy domain, or the full domain name otherwise (i.e. specs compliant). */
    private fun isLegacyEligible(url: HttpUrl): Boolean {
      return LegacyDomain.values().any { it.host == url.host() }
    }

    /** Get the domain for the given [HttpUrl]. If eligible returns a legacy domain, or the full domain name otherwise (i.e. specs compliant). */
    fun filterDomain(url: HttpUrl): String {
      return LegacyDomain.values().firstOrNull { it.host == url.host() }?.legacyCompatDomain ?: url.host()
    }

    fun signLnurlAuthK1WithKey(keyManager: KeyManager, k1: String, url: HttpUrl): Pair<String, Crypto.PrivateKey> {
      val authKey = getAuthLinkingKey(keyManager, url)
      return Crypto.compact2der(Crypto.sign(ByteVector32.fromValidHex(k1).bytes(), authKey)).toHex() to authKey
    }

    private fun getAuthLinkingKey(keyManager: KeyManager, url: HttpUrl): Crypto.PrivateKey {
      // 0 - the LNURL hashing key is the node key on legacy domains, master otherwise
      val hashingKey = DeterministicWallet.derivePrivateKey(
        if (isLegacyEligible(url)) keyManager.nodeKey() else keyManager.master(),
        DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/138'/0")
      )
      // 1 - get derivation path by hashing the service domain name
      val domain = filterDomain(url)
      val domainHash = `Mac32$`.`MODULE$`.hmac256(hashingKey.privateKey().value().bytes(), ByteVector.view(domain.toByteArray(Charsets.UTF_8)))
      require(domainHash.bytes().size() >= 16) { "domain hash must be at least 16 chars" }
      val stream = ByteArrayInputStream(domainHash.bytes().slice(0, 16).toArray())
      val path1 = Protocol.uint32(stream, ByteOrder.BIG_ENDIAN)
      val path2 = Protocol.uint32(stream, ByteOrder.BIG_ENDIAN)
      val path3 = Protocol.uint32(stream, ByteOrder.BIG_ENDIAN)
      val path4 = Protocol.uint32(stream, ByteOrder.BIG_ENDIAN)
      val path = DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/138'/$path1/$path2/$path3/$path4")
      // 2 - build key that will be used to link with service
      return DeterministicWallet.derivePrivateKey(if (isLegacyEligible(url)) hashingKey else keyManager.master(), path).privateKey()
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

  val domainToSignIn: String = LNUrlAuthFragment.filterDomain(url)

  class Factory(private val url: HttpUrl) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return LNUrlAuthViewModel(url) as T
    }
  }
}
