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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl

enum class LNUrlPayActionTypeVersion {
  URL_V0,
  MESSAGE_V0,
  AES_V0
}

@Serializable
sealed class LNUrlPayActionData {
  @Serializable
  sealed class Url : LNUrlPayActionData() {
    @Serializable
    data class V0(val description: String, val url: String) : Url()
  }

  @Serializable
  sealed class Message : LNUrlPayActionData() {
    @Serializable
    data class V0(val message: String) : Message()
  }

  @Serializable
  sealed class Aes : LNUrlPayActionData() {
    @Serializable
    data class V0(val description: String, val cipherText: String, val iv: String) : Aes()
  }
}

enum class ClosingType(val code: Long) {
  Mutual(0), Local(1), Remote(2), Other(3)
}

fun PaymentMeta.getSpendingTxs(): List<String>? = closing_spending_txs?.split(";")?.map { it.trim() }

fun PaymentMeta.getSuccessAction(): LNUrlPayActionData? = if (lnurlpay_action_typeversion != null && lnurlpay_action_data != null) {
  when (lnurlpay_action_typeversion) {
    LNUrlPayActionTypeVersion.MESSAGE_V0 -> Json.decodeFromString<LNUrlPayActionData.Message.V0>(lnurlpay_action_data).let { LNUrlPayActionData.Message.V0(it.message) }
    LNUrlPayActionTypeVersion.URL_V0 -> Json.decodeFromString<LNUrlPayActionData.Url.V0>(lnurlpay_action_data).let { LNUrlPayActionData.Url.V0(it.description, it.url) }
    LNUrlPayActionTypeVersion.AES_V0 -> Json.decodeFromString<LNUrlPayActionData.Aes.V0>(lnurlpay_action_data).let { LNUrlPayActionData.Aes.V0(it.description, it.cipherText, it.iv) }
  }
} else null

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
    description.takeIf { it.isNotBlank() }.let {
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

  fun saveLNUrlPayInfo(paymentId: String, url: HttpUrl, action: LNUrlPayActionData?) {
    queries.transaction {
      get(paymentId) ?: insert(paymentId)
      queries.setLNUrlPayUrl(url.toString(), paymentId)
      val (typeversion, data) = when (action) {
        is LNUrlPayActionData.Message.V0 -> LNUrlPayActionTypeVersion.MESSAGE_V0 to Json.encodeToString(action)
        is LNUrlPayActionData.Url.V0 -> LNUrlPayActionTypeVersion.URL_V0 to Json.encodeToString(action)
        is LNUrlPayActionData.Aes.V0 -> LNUrlPayActionTypeVersion.AES_V0 to Json.encodeToString(action)
        else -> null to null
      }
      queries.setLNUrlPayAction(typeversion, data, paymentId)
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
