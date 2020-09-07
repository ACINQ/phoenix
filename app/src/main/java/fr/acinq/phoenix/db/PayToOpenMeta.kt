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

/**
 * Stores accepted pay to open requests. Let the app know when a incoming payment was received by opening a new channel.
 * Note that the payment hash is a primary key for `received_payments` in the core `eclair.sqlite` db.
 *
 * A line is created
 */
@Entity(tableName = "pay_to_open_meta")
data class PayToOpenMeta(
  @PrimaryKey(autoGenerate = true) val id: Long,
  @ColumnInfo(name = "payment_hash") val paymentHash: String,
  @ColumnInfo(name = "fee_sat") val feeSat: Long,
  @ColumnInfo(name = "amount_sat") val amountSat: Long,
  @ColumnInfo(name = "capacity_sat") val capacitySat: Long,
  @ColumnInfo(name = "timestamp") val timestamp: Long
) {
  constructor(paymentHash: String, feeSat: Long, amountSat: Long, capacitySat: Long, timestamp: Long)
    : this(0, paymentHash, feeSat, amountSat, capacitySat, timestamp)
}

@Dao
interface PayToOpenMetaDao {
  @WorkerThread
  @Query("SELECT * from pay_to_open_meta WHERE payment_hash=:paymentHash")
  fun get(paymentHash: String): PayToOpenMeta?

  @WorkerThread
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insert(meta: PayToOpenMeta)
}

class PayToOpenMetaRepository private constructor(private val dao: PayToOpenMetaDao) {

  @WorkerThread
  fun insert(meta: PayToOpenMeta) = dao.insert(meta)

  @WorkerThread
  fun get(paymentHash: String): PayToOpenMeta? = dao.get(paymentHash)

  companion object {
    @Volatile
    private var instance: PayToOpenMetaRepository? = null

    fun getInstance(dao: PayToOpenMetaDao) =
      instance ?: synchronized(this) {
        instance ?: PayToOpenMetaRepository(dao).also { instance = it }
      }
  }
}
