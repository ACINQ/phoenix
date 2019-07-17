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

package fr.acinq.eclair.phoenix.initwallet

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eclair.`package$`
import fr.acinq.eclair.phoenix.MainActivity
import fr.acinq.eclair.phoenix.R
import fr.acinq.eclair.phoenix.databinding.FragmentInitWalletAutoCreateBinding
import fr.acinq.eclair.phoenix.utils.encrypt.EncryptedSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex
import scala.collection.JavaConverters

class AutoCreateFragment : Fragment() {

  private lateinit var mBinding: FragmentInitWalletAutoCreateBinding
  private lateinit var model: AutoCreateViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentInitWalletAutoCreateBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    model = ViewModelProviders.of(this).get(AutoCreateViewModel::class.java)
    model.errorCause.observe(this, Observer {
      mBinding.error.text = getString(R.string.autocreate_error, it)
    })
    model.state.observe(viewLifecycleOwner, Observer { state ->
      if (state == AutoCreateState.DONE) {
        findNavController().navigate(R.id.global_action_any_to_startup)
      }
    })
    mBinding.model = model
  }

  override fun onStart() {
    super.onStart()
    if (model.state.value == AutoCreateState.START) {
      context?.let {
        model.createAndSaveSeed(it.applicationContext)
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
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        try {
          state.postValue(AutoCreateState.IN_PROGRESS)
          log.info("creating words...")
          val words: List<String> = JavaConverters.seqAsJavaListConverter(MnemonicCode.toMnemonics(`package$`.`MODULE$`.randomBytes(16), MnemonicCode.englishWordlist())).asJava()
          val seed: ByteArray = Hex.encode(words.joinToString(" ").toByteArray(Charsets.UTF_8))
          delay(500)
          EncryptedSeed.writeSeedToFile(context, seed, "tutu")
          log.info("words written to file")
          state.postValue(AutoCreateState.DONE)
        } catch (t: Throwable) {
          log.error("cannot create seed: ", t)
          errorCause.postValue(t.localizedMessage)
          state.postValue(AutoCreateState.ERROR)
        }
      }
    }
  }
}
