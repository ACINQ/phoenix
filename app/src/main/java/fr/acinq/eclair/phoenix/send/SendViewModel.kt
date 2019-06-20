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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import fr.acinq.eclair.payment.PaymentRequest
import org.slf4j.LoggerFactory

class SendViewModel : ViewModel() {
  private val log = LoggerFactory.getLogger(SendViewModel::class.java)
  val paymentRequest = MutableLiveData<PaymentRequest>(null)
  val paymentHash: LiveData<String> = Transformations.map(paymentRequest) { pr ->
    "${pr?.paymentHash() ?: ""}"
  }

}
