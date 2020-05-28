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

import androidx.annotation.UiThread
import androidx.lifecycle.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.R
import fr.acinq.phoenix.lnurl.LNUrl
import fr.acinq.phoenix.utils.BitcoinURI
import fr.acinq.phoenix.utils.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

sealed class ReadInputState {
  object Scanning : ReadInputState()
  data class Reading(val input: String) : ReadInputState()
  sealed class Done : ReadInputState() {
    data class Lightning(val pr: PaymentRequest) : Done()
    data class Onchain(val bitcoinUri: BitcoinURI) : Done()
    data class Url(val url: LNUrl) : Done()
  }
  sealed class Error : ReadInputState() {
    object PayToSelf : Error()
    object PaymentExpired : Error()
    object InvalidChain : Error()
    object UnhandledLNURL : Error()
    object UnhandledInput : Error()
  }
}

class ReadInputViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(ReadInputViewModel::class.java)

  val hasCameraAccess = MutableLiveData<Boolean>()
  val inputState = MutableLiveData<ReadInputState>()

  init {
    hasCameraAccess.value = false
    inputState.value = ReadInputState.Scanning
  }

  @UiThread
  fun readInput(input: String) {
    log.debug("reading input=$input")
    if (inputState.value is ReadInputState.Scanning) {
      inputState.value = ReadInputState.Reading(input)
      viewModelScope.launch {
        withContext(Dispatchers.Default) {
          try {
            val res = Wallet.parseLNObject(input)
            inputState.postValue(when (res) {
              is PaymentRequest -> ReadInputState.Done.Lightning(res)
              is BitcoinURI -> ReadInputState.Done.Onchain(res)
              is LNUrl -> ReadInputState.Done.Url(res)
              else -> ReadInputState.Error.UnhandledInput
            })
          } catch (e: Exception) {
            log.info("invalid lightning object: ${e.localizedMessage}")
            inputState.postValue(ReadInputState.Error.UnhandledInput)
          }
        }
      }
    }
  }
}
