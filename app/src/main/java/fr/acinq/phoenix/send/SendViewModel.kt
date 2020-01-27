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
import scala.util.Either
import scala.util.Left
import scala.util.Right

enum class SendState {
  VALIDATING_INVOICE, INVALID_INVOICE, VALID_INVOICE, SENDING
}

enum class SwapState {
  NO_SWAP, SWAP_REQUIRED, SWAP_IN_PROGRESS, SWAP_COMPLETE
}

class SendViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(SendViewModel::class.java)

  val state = MutableLiveData<SendState>(SendState.VALIDATING_INVOICE)
  val swapState = MutableLiveData<SwapState>(SwapState.NO_SWAP)
  val isAmountFieldPristine = MutableLiveData(true) // to prevent early validation error message if amount is not set in invoice
  val useMaxBalance = MutableLiveData<Boolean>()
  val amountErrorMessage = SingleLiveEvent<Int>()
  val swapTotalTooLarge = MutableLiveData(false)
  val invoice = MutableLiveData<Either<Pair<BitcoinURI, PaymentRequest?>, PaymentRequest>>(null)

  // ---- computed values from payment request

  val description: LiveData<String> = Transformations.map(invoice) {
    it?.let {
      when {
        it.isLeft -> it.left().get().first.label
        it.isRight && it.right().get().description().isLeft -> it.right().get().description().left().get()
        it.isRight && it.right().get().description().isRight -> it.right().get().description().right().get().toString()
        else -> ""
      }
    } ?: ""
  }

  val destination: LiveData<String> = Transformations.map(invoice) {
    it?.let {
      when {
        it.isLeft -> it.left().get().first.address
        it.isRight -> it.right().get().nodeId().toString()
        else -> ""
      }
    } ?: ""
  }

  val isLightning: LiveData<Boolean> = Transformations.map(invoice) {
    it != null && it.isRight
  }

  val isFormVisible: LiveData<Boolean> = Transformations.map(state) { state ->
    state == SendState.VALID_INVOICE || state == SendState.SENDING
  }

  init {
    useMaxBalance.value = false
  }

  // ---- end of computed values

  @UiThread
  fun checkAndSetPaymentRequest(input: String) {
    log.debug("checking input=$input")
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        try {
          val extract = Wallet.extractInvoice(input)
          when (extract) {
            is BitcoinURI -> invoice.postValue(Left.apply(Pair(extract, null)))
            is PaymentRequest -> invoice.postValue(Right.apply(extract))
            else -> throw RuntimeException("unhandled invoice type")
          }
          state.postValue(SendState.VALID_INVOICE)
        } catch (e: Exception) {
          log.error("invalid payment request $input: ${e.message}")
          invoice.postValue(null)
          state.postValue(SendState.INVALID_INVOICE)
        }
      }
    }
  }

}
