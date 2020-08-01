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

package fr.acinq.phoenix.initwallet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eclair.`package$`
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentInitWalletAutoCreateBinding
import fr.acinq.phoenix.utils.crypto.KeystoreHelper
import fr.acinq.phoenix.utils.Wallet
import fr.acinq.phoenix.utils.crypto.EncryptedSeed
import fr.acinq.phoenix.utils.crypto.SeedManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex
import scala.collection.JavaConverters

class AutoCreateFragment : Fragment() {

  private lateinit var mBinding: FragmentInitWalletAutoCreateBinding
  private lateinit var model: AutoCreateViewModel
  val log: Logger = LoggerFactory.getLogger(this::class.java)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentInitWalletAutoCreateBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProvider(this).get(AutoCreateViewModel::class.java)
    model.errorCause.observe(viewLifecycleOwner, Observer {
      mBinding.error.text = getString(R.string.autocreate_error, it)
    })
    model.state.observe(viewLifecycleOwner, Observer { state ->
      if (state == AutoCreateState.DONE) {
        findNavController().navigate(R.id.global_action_any_to_startup)
      }
    })
    activity?.onBackPressedDispatcher?.addCallback(this) {
      log.debug("back button disabled")
    }
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    context?.let {
      if (Wallet.hasWalletBeenSetup(it)) {
        findNavController().navigate(R.id.global_action_any_to_startup)
      } else if (model.state.value == AutoCreateState.START) {
        model.createAndSaveSeed(it)
      }
    }
  }
}

enum class AutoCreateState {
  START, IN_PROGRESS, DONE, ERROR
}

class AutoCreateViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(AutoCreateViewModel::class.java)

  val state = MutableLiveData(AutoCreateState.START)
  val errorCause = MutableLiveData("")

  @UiThread
  fun createAndSaveSeed(context: Context) {
    viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
      log.error("cannot create seed: ", e)
      errorCause.postValue(e.localizedMessage)
      state.postValue(AutoCreateState.ERROR)
    }) {
      state.postValue(AutoCreateState.IN_PROGRESS)
      val words: List<String> = JavaConverters.seqAsJavaListConverter(MnemonicCode.toMnemonics(`package$`.`MODULE$`.randomBytes(16), MnemonicCode.englishWordlist())).asJava()
      val seed: ByteArray = Hex.encode(words.joinToString(" ").toByteArray(Charsets.UTF_8))
      delay(500)
      EncryptedSeed.V2.NoAuth.encrypt(seed).let { SeedManager.writeSeedToDisk(Wallet.getDatadir(context), it) }
      log.info("seed has been encrypted and saved")
      state.postValue(AutoCreateState.DONE)
    }
  }
}
