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
import androidx.lifecycle.*
import fr.acinq.bitcoin.scala.Satoshi
import fr.acinq.eclair.db.OutgoingPaymentStatus
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.legacy.ServiceStatus
import fr.acinq.phoenix.legacy.SwapOutSettings
import fr.acinq.phoenix.legacy.background.EclairNodeService
import fr.acinq.phoenix.legacy.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*

sealed class SendState {
  object CheckingInvoice : SendState()
  sealed class InvalidInvoice : SendState() {
    object Generic: InvalidInvoice()
    data class AlreadyPaid(val parentId: UUID): InvalidInvoice()
  }

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

    data class SwapOutServiceDisabled(override val uri: BitcoinURI) : Onchain()
    data class SwapRequired(override val uri: BitcoinURI) : Onchain()
    data class Swapping(override val uri: BitcoinURI, val feeratePerByte: Long) : Onchain()
    data class Ready(override val uri: BitcoinURI, val pr: PaymentRequest, val feeratePerByte: Long, val fee: Satoshi) : Onchain()
    data class Sending(override val uri: BitcoinURI, val pr: PaymentRequest, val feeratePerByte: Long, val fee: Satoshi) : Onchain()
    sealed class Error : Onchain() {
      data class ExceedsBalance(override val uri: BitcoinURI) : Error()
      data class SendingFailure(override val uri: BitcoinURI, val pr: PaymentRequest) : Onchain.Error()
    }
  }
}

data class FeerateEstimationPerKb(val rate20min: Long, val rate60min: Long, val rate12hours: Long)

class SendViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(SendViewModel::class.java)

  val state = MutableLiveData<SendState>()
  /** Prevents early validation error message if amount is not set in invoice. */
  val isAmountFieldPristine = MutableLiveData<Boolean>()
  /** Contains strings resource id for amount error message. Not contained in the fragment Error state because an incorrect amount is not a fatal error. */
  val amountError = MutableLiveData<Exception?>()

  init {
    state.value = SendState.CheckingInvoice
    isAmountFieldPristine.value = true
    amountError.value = null
  }

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
    state !is SendState.CheckingInvoice && state !is SendState.InvalidInvoice && state !is SendState.Onchain.SwapOutServiceDisabled
  }

  // ---- end of computed values

  @UiThread
  fun checkAndSetPaymentRequest(service: EclairNodeService?, input: String, swapOutSettings: SwapOutSettings) {
    viewModelScope.launch((Dispatchers.Default)) {
      try {
        val extract = Wallet.parseLNObject(input)
        when (extract) {
          is BitcoinURI -> {
            if (swapOutSettings.status is ServiceStatus.Disabled) {
              state.postValue(SendState.Onchain.SwapOutServiceDisabled(extract))
            } else {
              state.postValue(SendState.Onchain.SwapRequired(extract))
            }
          }
          is PaymentRequest -> {
            val matchingPaid = (service?.getSentPaymentsFromPaymentHash(extract.paymentHash()) ?: emptyList()).filter { it.status() is OutgoingPaymentStatus.Succeeded }
            if (matchingPaid.isNotEmpty()) {
              state.postValue(SendState.InvalidInvoice.AlreadyPaid(matchingPaid.first().parentId()))
            } else {
              state.postValue(SendState.Lightning.Ready(extract))
            }
          }
          else -> throw RuntimeException("unhandled invoice type")
        }
      } catch (e: Exception) {
        log.error("invalid invoice for input=$input: ${e.message}")
        state.postValue(SendState.InvalidInvoice.Generic)
      }
    }
  }
}
