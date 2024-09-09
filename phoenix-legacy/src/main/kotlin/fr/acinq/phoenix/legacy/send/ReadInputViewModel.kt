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

package fr.acinq.phoenix.legacy.send

import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.legacy.lnurl.LNUrl
import fr.acinq.phoenix.legacy.lnurl.LNUrlError
import fr.acinq.phoenix.legacy.utils.BitcoinURI
import fr.acinq.phoenix.legacy.utils.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

sealed class ReadInputState {
  object Scanning : ReadInputState()
  data class Reading(val input: String) : ReadInputState()
  sealed class Done : ReadInputState() {
    data class Lightning(val pr: PaymentRequest) : Done()
    data class Url(val url: LNUrl) : Done()
  }

  sealed class Error : ReadInputState() {
    object PayToSelf : Error()
    object PaymentExpired : Error()
    object OnChain : Error()
    object InvalidChain : Error()
    data class ErrorInLNURLResponse(val error: LNUrlError) : Error()
    object UnhandledLNURL : Error()
    object UnhandledInput : Error()
  }
}

class ReadInputViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(ReadInputViewModel::class.java)

  val hasCameraAccess = MutableLiveData<Boolean>()
  val lastFailedInput = MutableLiveData("")
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
      lastFailedInput.value = ""
      viewModelScope.launch {
        withContext(Dispatchers.Default) {
          try {
            val res = Wallet.parseLNObject(input)
            inputState.postValue(when (res) {
              is PaymentRequest -> ReadInputState.Done.Lightning(res)
              is BitcoinURI -> ReadInputState.Error.OnChain
              is LNUrl -> ReadInputState.Done.Url(res)
              else -> ReadInputState.Error.UnhandledInput
            })
          } catch (e: Exception) {
            log.info("invalid lightning object: ${e.localizedMessage}")
            lastFailedInput.postValue(input)
            inputState.postValue(when (e) {
              is LNUrlError -> ReadInputState.Error.ErrorInLNURLResponse(e)
              else -> ReadInputState.Error.UnhandledInput
            })
          }
        }
      }
    }
  }
}
