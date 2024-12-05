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
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.adapters.IncomingPaymentJsonAdapter
import fr.acinq.phoenix.db.migrations.v10.types.mapIncomingPaymentFromV10
import fr.acinq.phoenix.utils.extensions.deriveUUID

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

        listOf(
            "DROP TABLE incoming_payments",
            """
                CREATE TABLE incoming_payments (
                    id TEXT NOT NULL PRIMARY KEY,
                    payment_hash BLOB,
                    created_at INTEGER NOT NULL,
                    received_at INTEGER DEFAULT NULL,
                    json TEXT NOT NULL
                )
            """.trimIndent(),
            "CREATE INDEX incoming_payments_payment_hash_idx ON incoming_payments(payment_hash)",
        ).forEach { sql ->
            driver.execute(identifier = null, sql = sql, parameters = 0)
        }

        payments
            .forEach { payment ->
                driver.execute(
                    identifier = null,
                    sql = "INSERT INTO incoming_payments (id, payment_hash, created_at, received_at, json) VALUES (?, ?, ?, ?, ?)",
                    parameters = 5
                ) {
                    when (payment) {
                        is LightningIncomingPayment -> {
                            println("migrating ${payment.paymentHash}")
                            bindString(0, payment.paymentHash.deriveUUID().toString())
                            bindBytes(1, payment.paymentHash.toByteArray())
                        }
                        is @Suppress("DEPRECATION") LegacyPayToOpenIncomingPayment -> {
                            println("migrating legacy ${payment.paymentHash}")
                            bindString(0, payment.paymentHash.deriveUUID().toString())
                            bindBytes(1, payment.paymentHash.toByteArray())
                        }
                        else -> TODO("unsupported payment=$payment")
                    }
                    bindLong(2, payment.createdAt)
                    bindLong(3, payment.completedAt)
                    bindString(4, IncomingPaymentJsonAdapter.encode(payment))
                }
            }

        val paymentHashLinks = driver.executeQuery(
            identifier = null,
            sql = """
                SELECT payments.id, lower(hex(payments.payment_hash))
                FROM link_tx_to_payments link
                JOIN incoming_payments payments ON link.id=lower(hex(payments.payment_hash))
                WHERE link.type=1
            """.trimIndent(),
            parameters = 0,
            mapper = { cursor ->
                val result = buildList {
                    while (cursor.next().value) {
                        val id = cursor.getString(0)!!
                        val payment_hash = cursor.getString(1)!!
                        add(payment_hash to id)
                    }
                }
                QueryResult.Value(result)
            }
        ).value

        paymentHashLinks
            .forEach { (paymentHash, id) ->
                driver.execute(
                    identifier = null,
                    sql = """
                        UPDATE link_tx_to_payments
                        SET id=?
                        WHERE id=?
                    """.trimIndent(),
                    parameters = 2
                ) {
                    bindString(0, id)
                    bindString(1, paymentHash)
                }
            }

        val metadataLinks = driver.executeQuery(
            identifier = null,
            sql = """
                SELECT payments.id, lower(hex(payments.payment_hash))
                FROM payments_metadata meta
                JOIN incoming_payments payments ON meta.id=lower(hex(payments.payment_hash))
                WHERE meta.type=1
            """.trimIndent(),
            parameters = 0,
            mapper = { cursor ->
                val result = buildList {
                    while (cursor.next().value) {
                        val id = cursor.getString(0)!!
                        val payment_hash = cursor.getString(1)!!
                        add(payment_hash to id)
                    }
                }
                QueryResult.Value(result)
            }
        ).value

        metadataLinks
            .forEach { (paymentHash, id) ->
                driver.execute(
                    identifier = null,
                    sql = """
                        UPDATE payments_metadata
                        SET id=?
                        WHERE id=?
                    """.trimIndent(),
                    parameters = 2
                ) {
                    bindString(0, id)
                    bindString(1, paymentHash)
                }
            }
    }
}