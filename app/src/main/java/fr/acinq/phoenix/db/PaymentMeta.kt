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
import androidx.room.*
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.Transaction
import fr.acinq.eclair.`package$`


/**
 * Contain details/metadata that cannot be stored in the core eclair.sqlite payment database,
 * such as the final closing transaction id if the payment is a closing transaction...
 */
@Entity(tableName = "payment_meta")
data class PaymentMeta(
  /** id can be a UUID for outgoing payments or a payment hash for incoming payments */
  @PrimaryKey @ColumnInfo(name = "id") val id: String,
  @ColumnInfo(name = "swap_in_address") val swapInAddress: String? = null,
  @ColumnInfo(name = "swap_in_tx") val swapInTxId: String? = null,
  @ColumnInfo(name = "swap_out_address") val swapOutAddress: String? = null,
  @ColumnInfo(name = "swap_out_tx") val swapOutTxId: String? = null,
  @ColumnInfo(name = "swap_out_feerate_per_byte") val swapOutFeeratePerByte: Long? = null,
  @ColumnInfo(name = "swap_out_fee_sat") val swapOutFeesSat: Long? = null,
  @ColumnInfo(name = "swap_out_conf") val swapOutConf: Long? = null,
  @ColumnInfo(name = "funding_tx") val fundingTxId: String? = null,
  @ColumnInfo(name = "funding_fee_pct") val fundingFeePct: Double? = null,
  @ColumnInfo(name = "funding_fee_raw_sat") val fundingFeeRawSat: Long? = null,
  @ColumnInfo(name = "closing_txs_main") val closingTxsMain: String? = null,
  @ColumnInfo(name = "closing_txs_delayed") val closingTxsDelayed: String? = null,
  @ColumnInfo(name = "closing_type") val closingType: Int? = null,
  @ColumnInfo(name = "closing_cause") val closingCause: String? = null,
  @ColumnInfo(name = "closing_channel_id") val closingChannelId: String? = null,
  @ColumnInfo(name = "custom_desc") val customDescription: String? = null
) {

  fun getMainTxsList(): List<String>? = closingTxsMain?.split(";")
  fun getDelayedTxsList(): List<String>? = closingTxsDelayed?.split(";")

  companion object {
    fun serializeTxs(list: List<Transaction>): String? {
      return list.joinToString(";") { it.txid().toString() }.takeIf { it.isNotBlank() }
    }
  }
}

enum class ClosingType(val code: Int) {
  Mutual(0), Local(1), Remote(2), Other(3)
}

@Dao
interface PaymentMetaDao {
  @WorkerThread
  @Query("SELECT * from payment_meta WHERE id=:id")
  fun get(id: String): PaymentMeta?

  @WorkerThread
  @Query("SELECT * from payment_meta WHERE closing_channel_id=:channelId")
  fun getByChannelId(channelId: String): PaymentMeta?

  @WorkerThread
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insert(paymentMeta: PaymentMeta)

  @WorkerThread
  @Update
  fun update(paymentMeta: PaymentMeta)

  @WorkerThread
  @Query("UPDATE payment_meta SET custom_desc=:desc WHERE id=:id")
  fun updateDescription(id: String, desc: String)
}

class PaymentMetaRepository private constructor(private val dao: PaymentMetaDao) {

  suspend fun get(id: String): PaymentMeta? = dao.get(id)
  suspend fun update(meta: PaymentMeta) = dao.update(meta)
  suspend fun insert(meta: PaymentMeta) = dao.insert(meta)

  suspend fun updateDescription(id: String, description: String) {
    val desc = if (description.isBlank()) null else description
    return get(id)?.run {
      update(copy(customDescription = desc))
    } ?: run {
      insert(PaymentMeta(id, customDescription = desc))
    }
  }

  fun setChannelClosingError(channelId: String, message: String?) {
    dao.getByChannelId(channelId)?.run {
      dao.update(copy(closingCause = message))
    } ?: run {
      dao.insert(PaymentMeta(
        id = Crypto.hash256(`package$`.`MODULE$`.randomBytes32().bytes()).toString(),
        closingCause = message))
    }
  }

  companion object {
    @Volatile
    private var instance: PaymentMetaRepository? = null

    fun getInstance(dao: PaymentMetaDao) =
      instance ?: synchronized(this) {
        instance ?: PaymentMetaRepository(dao).also { instance = it }
      }
  }
}
