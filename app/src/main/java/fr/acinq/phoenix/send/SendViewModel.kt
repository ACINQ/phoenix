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
import fr.acinq.phoenix.utils.BitcoinURI
import fr.acinq.phoenix.utils.SingleLiveEvent
import fr.acinq.phoenix.utils.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

sealed class SendState {
  object CheckingInvoice : SendState()
  object InvalidInvoice : SendState()

  sealed class Lightning : SendState() {
    abstract val pr: PaymentRequest

    data class Ready(override val pr: PaymentRequest) : Lightning()
    data class Sending(override val pr: PaymentRequest) : Lightning()
    sealed class Error : Lightning() {
      data class SendingFailure(override val pr: PaymentRequest) : Error()
    }
  }

  sealed class Onchain : SendState() {
    abstract val uri: BitcoinURI

    data class SwapRequired(override val uri: BitcoinURI) : Onchain()
    data class Swapping(override val uri: BitcoinURI) : Onchain()
    data class Ready(override val uri: BitcoinURI, val pr: PaymentRequest) : Onchain()
    data class Sending(override val uri: BitcoinURI, val pr: PaymentRequest) : Onchain()
    sealed class Error : Onchain() {
      data class ExceedsBalance(override val uri: BitcoinURI) : Error()
      data class SendingFailure(override val uri: BitcoinURI, val pr: PaymentRequest) : Onchain.Error()
    }
  }
}

class SendViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(SendViewModel::class.java)

  val state = MutableLiveData<SendState>()
  val isAmountFieldPristine = MutableLiveData<Boolean>() // to prevent early validation error message if amount is not set in invoice
  val useMaxBalance = MutableLiveData<Boolean>()
  val amountErrorMessage = SingleLiveEvent<Int>()

  // ---- computed values from payment request

  val description: LiveData<String> = Transformations.map(state) {
    when {
      it is SendState.Lightning && it.pr.description().isLeft -> it.pr.description().left().get()
      it is SendState.Lightning && it.pr.description().isRight -> it.pr.description().right().get().toString()
      else -> ""
    }
  }

  val destination: LiveData<String> = Transformations.map(state) {
    when (it) {
      is SendState.Lightning -> it.pr.nodeId().toString()
      is SendState.Onchain -> it.uri.address
      else -> ""
    }
  }

  val isFormVisible: LiveData<Boolean> = Transformations.map(state) { state ->
    state !is SendState.CheckingInvoice && state !is SendState.InvalidInvoice
  }

  init {
    state.value = SendState.CheckingInvoice
    useMaxBalance.value = false
    isAmountFieldPristine.value = true
    amountErrorMessage.value = null
  }

  // ---- end of computed values

  @UiThread
  fun checkAndSetPaymentRequest(input: String) {
    viewModelScope.launch((Dispatchers.Default)) {
      try {
        val extract = Wallet.parseLNObject(input)
        when (extract) {
          is BitcoinURI -> state.postValue(SendState.Onchain.SwapRequired(extract))
          is PaymentRequest -> state.postValue(SendState.Lightning.Ready(extract))
          else -> throw RuntimeException("unhandled invoice type")
        }
      } catch (e: Exception) {
        log.error("invalid invoice for input=$input: ${e.message}")
        state.postValue(SendState.InvalidInvoice)
      }
    }
  }

}
