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
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import fr.acinq.phoenix.MainActivity
import fr.acinq.phoenix.R
import fr.acinq.phoenix.databinding.FragmentInitWalletSetupPinBinding
import fr.acinq.phoenix.utils.encrypt.EncryptedSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.spongycastle.util.encoders.Hex

class SetupPinFragment : InitWalletBaseFragment() {

  private lateinit var mBinding: FragmentInitWalletSetupPinBinding
  private lateinit var model: SetupPinViewModel

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    mBinding = FragmentInitWalletSetupPinBinding.inflate(inflater, container, false)
    mBinding.lifecycleOwner = this
    return mBinding.root
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    seedModel.words.observe(viewLifecycleOwner, Observer { words ->
      if (words.isNullOrEmpty()) {
        log.info("reference to seed lost when setting up PIN")
        findNavController().popBackStack(R.id.global_action_any_to_init_wallet, true)
      }
    })

    // fragment model/binding
    model = ViewModelProvider(this).get(SetupPinViewModel::class.java)
    mBinding.model = model
    model.state.observe(viewLifecycleOwner, Observer { state ->
      if (state == SeedEncryptionState.DONE && activity != null) {
        startActivity(Intent(activity, MainActivity::class.java))
        requireActivity().finish()
      }
    })
  }

  override fun onStart() {
    super.onStart()
    mBinding.confirmButton.setOnClickListener {
      if (context != null) {
        model.encryptSeed(context!!, seedModel.words.value, "111111")
      }
    }
  }

  enum class SeedEncryptionState {
    OFF, ENCRYPTING, DONE, ERROR
  }

  class SetupPinViewModel : ViewModel() {
    private val log = LoggerFactory.getLogger(SetupPinViewModel::class.java)

    private val _state = MutableLiveData<SeedEncryptionState>(SeedEncryptionState.OFF)
    val state: LiveData<SeedEncryptionState> get() = _state

    @UiThread
    fun encryptSeed(context: Context, words: List<String>?, password: String) {
      viewModelScope.launch {
        _state.value = SeedEncryptionState.ENCRYPTING
        _state.value = withContext(viewModelScope.coroutineContext) { encryptAndWriteSeed(context, words, password) }
      }
    }

    private suspend fun encryptAndWriteSeed(context: Context, words: List<String>?, password: String): SeedEncryptionState {
      return withContext(Dispatchers.Default) {
        if (!words.isNullOrEmpty()) {
          val seed: ByteArray = Hex.encode(words.joinToString(" ").toByteArray(Charsets.UTF_8))
          try {
            EncryptedSeed.writeSeedToFile(context, seed, password)
            SeedEncryptionState.DONE
          } catch (t: Throwable) {
            log.error("cannot encrypt seed: ", t.localizedMessage)
            SeedEncryptionState.ERROR
          }
        } else {
          log.error("cannot encrypt seed because it is empty")
          SeedEncryptionState.ERROR
        }
      }
    }
  }
}
