/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.db

import androidx.annotation.WorkerThread
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi

class PayToOpenMetaRepository private constructor(private val queries: PayToOpenMetaQueries) {

  @WorkerThread
  fun get(paymentHash: String): PayToOpenMeta? = queries.get(paymentHash).executeAsOneOrNull()

  @WorkerThread
  fun insert(paymentHash: ByteVector32, fee: Satoshi, amount: Satoshi, capacity: Satoshi) = queries.insert(
    payment_hash = paymentHash.toString(),
    fee_sat = fee.toLong(),
    amount_sat = amount.toLong(),
    capacity_sat = capacity.toLong(),
    timestamp = System.currentTimeMillis())

  companion object {
    @Volatile
    private var instance: PayToOpenMetaRepository? = null

    fun getInstance(queries: PayToOpenMetaQueries) =
      instance ?: synchronized(this) {
        instance ?: PayToOpenMetaRepository(queries).also { instance = it }
      }
  }
}
