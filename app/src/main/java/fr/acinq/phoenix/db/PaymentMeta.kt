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

import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.Transaction
import fr.acinq.eclair.`package$`


enum class ClosingType(val code: Long) {
  Mutual(0), Local(1), Remote(2), Other(3)
}

fun PaymentMeta.getSpendingTxs(): List<String>? = closing_spending_txs?.split(";")?.map { it.trim() }

class PaymentMetaRepository private constructor(private val queries: PaymentMetaQueries) {

  fun get(paymentId: String): PaymentMeta? = queries.get(paymentId).executeAsOneOrNull()

  fun insert(paymentId: String) = queries.insertEmpty(paymentId)

  fun insertClosing(paymentId: String, closingType: ClosingType, closingChannelId: String, closingSpendingTxs: List<Transaction>, closingMainOutput: String?) = queries.insertClosing(
    id = paymentId,
    closing_type = closingType.code,
    closing_channel_id = closingChannelId,
    closing_spending_txs = closingSpendingTxs.joinToString(";") { it.txid().toString() }.takeIf { it.isNotBlank() },
    closing_main_output_script = closingMainOutput)

  fun insertSwapIn(paymentId: String, swapInAddress: String) = queries.insertSwapIn(id = paymentId, swap_in_address = swapInAddress)

  fun setDesc(paymentId: String, description: String) {
    description.takeIf { !it.isBlank() }.let {
      get(paymentId) ?: insert(paymentId)
      queries.setDesc(it, paymentId)
    }
  }

  fun setChannelClosingError(channelId: String, message: String?) {
    queries.getByChannelId(channelId).executeAsOneOrNull()?.run {
      queries.setChannelClosingError(message, id)
    } ?: run {
      val id = Crypto.hash256(`package$`.`MODULE$`.randomBytes32().bytes()).toString()
      queries.insertEmpty(id)
      queries.setChannelClosingError(message, id)
    }
  }

  companion object {
    @Volatile
    private var instance: PaymentMetaRepository? = null

    fun getInstance(queries: PaymentMetaQueries) =
      instance ?: synchronized(this) {
        instance ?: PaymentMetaRepository(queries).also { instance = it }
      }
  }
}
