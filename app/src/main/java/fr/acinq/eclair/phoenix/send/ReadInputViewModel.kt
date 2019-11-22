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
import fr.acinq.eclair.phoenix.utils.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

enum class ReadingState {
  SCANNING, READING, DONE, ERROR
}

class ReadInputViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(ReadInputViewModel::class.java)

  val hasCameraAccess = MutableLiveData(false)
  val invoice = MutableLiveData<Any>(null)
  val readingState = MutableLiveData<ReadingState>()

  init {
    readingState.value = ReadingState.SCANNING
  }

  @UiThread
  fun checkAndSetPaymentRequest(input: String) {
    log.debug("checking input=$input")
    if (readingState.value == ReadingState.SCANNING) {
      readingState.value = ReadingState.READING
      viewModelScope.launch {
        withContext(Dispatchers.Default) {
          try {
            invoice.postValue(Wallet.extractInvoice(input))
            readingState.postValue(ReadingState.DONE)
          } catch (e1: Exception) {
            invoice.postValue(null)
            readingState.postValue(ReadingState.ERROR)
          }
        }
      }
    }
  }

}
