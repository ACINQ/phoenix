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

package fr.acinq.phoenix.legacy.receive

import android.graphics.Bitmap
import androidx.annotation.UiThread
import androidx.lifecycle.*
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.legacy.utils.QRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

interface ReceiveState

// possible states when generating a lightning payment request
enum class PaymentGenerationState : ReceiveState {
  INIT, EDITING_REQUEST, IN_PROGRESS, ERROR, DONE
}

// possible states when swapping to on-chain tx
enum class SwapInState : ReceiveState {
  DISABLED, IN_PROGRESS, ERROR, DONE
}

class ReceiveViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(ReceiveViewModel::class.java)

  val invoice = MutableLiveData<Pair<PaymentRequest, String?>?>()
  val bitmap = MutableLiveData<Bitmap?>()
  val state = MutableLiveData<ReceiveState>()
  val payToOpenDisabled = MutableLiveData(false)
  val showMinFundingPayToOpen = MutableLiveData(false)

  init {
    invoice.value = null
    bitmap.value = null
    state.value = PaymentGenerationState.INIT
  }

  @UiThread
  fun generateQrCodeBitmap() {
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        try {
          val source = invoice.value!!.second ?: PaymentRequest.write(invoice.value!!.first)
          bitmap.postValue(QRCode.generateBitmap(source))
        } catch (e: Exception) {
          log.error("error when generating bitmap QR for invoice=${invoice.value}")
          state.postValue(PaymentGenerationState.ERROR)
        }
      }
    }
  }
}
