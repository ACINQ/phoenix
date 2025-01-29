/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.db.migrations.v10

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.OnChainIncomingPayment
import fr.acinq.phoenix.db.migrations.v10.types.mapIncomingPaymentFromV10
import fr.acinq.phoenix.utils.extensions.deriveUUID
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.phoenix.utils.extensions.toByteArray

val AfterVersion10 = AfterVersion(10) { driver ->
    val transacter = object : TransacterImpl(driver) {}

    transacter.transaction {
        val payments = driver.executeQuery(
            identifier = null,
            sql = "SELECT * FROM incoming_payments",
            parameters = 0,
            mapper = { cursor ->
                val result = buildList {
                    while (cursor.next().value) {
                        val o = mapIncomingPaymentFromV10(
                            payment_hash = cursor.getBytes(0)!!,
                            preimage = cursor.getBytes(1)!!,
                            created_at = cursor.getLong(2)!!,
                            origin_type = cursor.getString(3)!!,
                            origin_blob = cursor.getBytes(4)!!,
                            received_amount_msat = cursor.getLong(5),
                            received_at = cursor.getLong(6),
                            received_with_type = cursor.getString(7),
                            received_with_blob = cursor.getBytes(8),
                        )
                        add(o)
                    }
                }
                QueryResult.Value(result)
            }
        ).value

        driver.execute(identifier = null, sql = "DROP TABLE incoming_payments", parameters = 0)

        payments
            .forEach { payment ->
                driver.execute(
                    identifier = null,
                    sql = "INSERT INTO payments_incoming (id, payment_hash, tx_id, created_at, received_at, data) VALUES (?, ?, ?, ?, ?, ?)",
                    parameters = 6
                ) {
                    when (payment) {
                        is LightningIncomingPayment -> {
                            bindBytes(0, payment.paymentHash.deriveUUID().toByteArray())
                            bindBytes(1, payment.paymentHash.toByteArray())
                            bindBytes(2, null)
                        }
                        is @Suppress("DEPRECATION") LegacyPayToOpenIncomingPayment -> {
                            bindBytes(0, payment.paymentHash.deriveUUID().toByteArray())
                            bindBytes(1, payment.paymentHash.toByteArray())
                            bindBytes(2, null)
                        }
                        is @Suppress("DEPRECATION") LegacySwapInIncomingPayment -> {
                            bindBytes(0, payment.id.toByteArray())
                            bindBytes(1, null)
                            bindBytes(2, null)
                        }
                        is OnChainIncomingPayment -> {
                            bindBytes(0, payment.id.toByteArray())
                            bindBytes(1, null)
                            bindBytes(2, payment.txId.value.toByteArray())
                        }
                    }
                    bindLong(3, payment.createdAt)
                    bindLong(4, payment.completedAt)
                    bindBytes(5, Serialization.serialize(payment))
                }
            }
    }
}