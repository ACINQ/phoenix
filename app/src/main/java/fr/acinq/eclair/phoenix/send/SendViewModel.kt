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

package fr.acinq.eclair.phoenix.send

import androidx.annotation.UiThread
import androidx.lifecycle.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.phoenix.utils.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

enum class SendState {
  VERIFYING_PR, INVALID_PR, VALID_PR, ERROR_IN_AMOUNT, SENDING
}

class SendViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(SendViewModel::class.java)

  val state = MutableLiveData<SendState>()
  val paymentRequest = MutableLiveData<PaymentRequest>(null)

  // ---- computed values from payment request

  val paymentHash: LiveData<String> = Transformations.map(paymentRequest) { pr ->
    "${pr?.paymentHash() ?: ""}"
  }

  val description: LiveData<String> = Transformations.map(paymentRequest) { pr ->
    pr?.let {
      when {
        it.description().isLeft -> it.description().left().get()
        it.description().isRight -> it.description().right().get().toString()
        else -> ""
      }
    } ?: ""
  }

  val destination: LiveData<String> = Transformations.map(paymentRequest) { pr ->
    pr?.let { it.nodeId().toString() } ?: ""
  }

  val isFormVisible: LiveData<Boolean> = Transformations.map(state) { state ->
    state == SendState.VALID_PR || state == SendState.ERROR_IN_AMOUNT || state == SendState.SENDING
  }

  // ---- end of computed values

  init {
    state.value = SendState.VERIFYING_PR
  }

  @UiThread
  fun checkAndSetPaymentRequest(input: String) {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        try {
          paymentRequest.postValue(PaymentRequest.read(Wallet.cleanPaymentRequest(input)))
          state.postValue(SendState.VALID_PR)
        } catch (e: Exception) {
          log.info("invalid payment request $input: ${e.message}")
          paymentRequest.postValue(null)
          state.postValue(SendState.INVALID_PR)
        }
      }
    }
  }

}
