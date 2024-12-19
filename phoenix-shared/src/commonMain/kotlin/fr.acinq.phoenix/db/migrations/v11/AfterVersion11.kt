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

package fr.acinq.phoenix.db.migrations.v11

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.OnChainOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.phoenix.db.migrations.v11.queries.ChannelCloseOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.queries.InboundLiquidityQueries
import fr.acinq.phoenix.db.migrations.v11.queries.LightningOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.queries.SpliceCpfpOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.queries.SpliceOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingDetailsTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartClosingInfoTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartStatusTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingStatusTypeVersion
import fr.acinq.phoenix.utils.extensions.toByteArray

val AfterVersion11 = AfterVersion(11) { driver ->

    val transacter = object : TransacterImpl(driver) {}

    fun insertPayment(payment: OutgoingPayment) {
        driver.execute(
            identifier = null,
            sql = "INSERT INTO payments_outgoing (id, payment_hash, tx_id, created_at, completed_at, sent_at, data) VALUES (?, ?, ?, ?, ?, ?, ?)",
            parameters = 7
        ) {
            println("migrating outgoing $payment")
            val (paymentHash, txId) = when (payment) {
                is LightningOutgoingPayment -> payment.paymentHash to null
                is OnChainOutgoingPayment -> null to payment.txId
            }
            val sentAt = when (payment) {
                is LightningOutgoingPayment -> if (payment.status is LightningOutgoingPayment.Status.Succeeded) payment.completedAt else null
                is OnChainOutgoingPayment -> payment.completedAt
            }
            bindBytes(0, payment.id.toByteArray())
            bindBytes(1, paymentHash?.toByteArray())
            bindBytes(2, txId?.value?.toByteArray())
            bindLong(3, payment.createdAt)
            bindLong(4, payment.completedAt)
            bindLong(5, sentAt)
            bindBytes(6, Serialization.serialize(payment))
        }
    }

    transacter.transaction {
        driver.executeQuery(
            identifier = null,
            sql = """
    |SELECT parent.id,
    |       parent.recipient_amount_msat,
    |       parent.recipient_node_id,
    |       parent.payment_hash,
    |       parent.details_type,
    |       parent.details_blob,
    |       parent.created_at,
    |       parent.completed_at,
    |       parent.status_type,
    |       parent.status_blob,
    |       -- lightning parts
    |       lightning_parts.part_id AS lightning_part_id,
    |       lightning_parts.part_amount_msat AS lightning_part_amount_msat,
    |       lightning_parts.part_route AS lightning_part_route,
    |       lightning_parts.part_created_at AS lightning_part_created_at,
    |       lightning_parts.part_completed_at AS lightning_part_completed_at,
    |       lightning_parts.part_status_type AS lightning_part_status_type,
    |       lightning_parts.part_status_blob AS lightning_part_status_blob
    |       -- closing tx parts
    |       closing_parts.part_id AS closingtx_part_id,
    |       closing_parts.part_tx_id AS closingtx_tx_id,
    |       closing_parts.part_amount_sat AS closingtx_amount_sat,
    |       closing_parts.part_closing_info_type AS closingtx_info_type,
    |       closing_parts.part_closing_info_blob AS closingtx_info_blob,
    |       closing_parts.part_created_at AS closingtx_created_at
    |       FROM outgoing_payments AS parent
    |       LEFT OUTER JOIN outgoing_payment_parts AS lightning_parts ON lightning_parts.part_parent_id = parent.id
    |       LEFT OUTER JOIN outgoing_payment_closing_tx_parts AS closing_parts ON closing_parts.part_parent_id = parent.id
    """.trimMargin(),
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    val payment = LightningOutgoingQueries.mapLightningOutgoingPayment(
                        cursor.getString(0)!!,
                        cursor.getLong(1)!!,
                        cursor.getString(2)!!,
                        cursor.getBytes(3)!!,
                        OutgoingDetailsTypeVersion.valueOf(cursor.getString(4)!!),
                        cursor.getBytes(5)!!,
                        cursor.getLong(6)!!,
                        cursor.getLong(7),
                        cursor.getString(8)?.let { OutgoingStatusTypeVersion.valueOf(it) },
                        cursor.getBytes(9),
                        cursor.getString(10),
                        cursor.getLong(11),
                        cursor.getString(12)?.let { LightningOutgoingQueries.hopDescAdapter.decode(it) },
                        cursor.getLong(13),
                        cursor.getLong(14),
                        cursor.getString(15)?.let { OutgoingPartStatusTypeVersion.valueOf(it) },
                        cursor.getBytes(16),
                        cursor.getString(17),
                        cursor.getBytes(18),
                        cursor.getLong(19),
                        cursor.getString(20)?.let { OutgoingPartClosingInfoTypeVersion.valueOf(it) },
                        cursor.getBytes(21),
                        cursor.getLong(22),
                    )
                    insertPayment(payment)
                }
                QueryResult.Unit
            }
        )

        driver.executeQuery(
            identifier = null,
            sql = "SELECT id, mining_fees_sat, channel_id, tx_id, lease_type, lease_blob, created_at, confirmed_at, locked_at FROM inbound_liquidity_outgoing_payments",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    val payment = InboundLiquidityQueries.mapPayment(
                        id = cursor.getString(0)!!,
                        mining_fees_sat = cursor.getLong(1)!!,
                        channel_id = cursor.getBytes(2)!!,
                        tx_id = cursor.getBytes(3)!!,
                        lease_type = cursor.getString(4)!!,
                        lease_blob = cursor.getBytes(5)!!,
//                        payment_details_type = cursor.getString(6),
                        created_at = cursor.getLong(6)!!,
                        confirmed_at = cursor.getLong(7),
                        locked_at = cursor.getLong(8)
                    )
                    insertPayment(payment)
                }
                QueryResult.Unit
            }
        )

        driver.executeQuery(
            identifier = null,
            sql = "SELECT id, recipient_amount_sat, address, mining_fees_sat, tx_id, channel_id, created_at, confirmed_at, locked_at FROM splice_outgoing_payments",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    val payment = SpliceOutgoingQueries.mapSpliceOutgoingPayment(
                        id = cursor.getString(0)!!,
                        recipient_amount_sat = cursor.getLong(1)!!,
                        address = cursor.getString(2)!!,
                        mining_fees_sat = cursor.getLong(3)!!,
                        tx_id = cursor.getBytes(4)!!,
                        channel_id = cursor.getBytes(5)!!,
                        created_at = cursor.getLong(6)!!,
                        confirmed_at = cursor.getLong(7),
                        locked_at = cursor.getLong(8)
                    )
                    insertPayment(payment)
                }
                QueryResult.Unit
            }
        )

        driver.executeQuery(
            identifier = null,
            sql = "SELECT id, mining_fees_sat, channel_id, tx_id, created_at, confirmed_at, locked_at FROM splice_cpfp_outgoing_payments",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    val payment = SpliceCpfpOutgoingQueries.mapCpfp(
                        id = cursor.getString(0)!!,
                        mining_fees_sat = cursor.getLong(1)!!,
                        channel_id = cursor.getBytes(2)!!,
                        tx_id = cursor.getBytes(3)!!,
                        created_at = cursor.getLong(4)!!,
                        confirmed_at = cursor.getLong(5),
                        locked_at = cursor.getLong(6)
                    )
                    insertPayment(payment)
                }
                QueryResult.Unit
            }
        )

        driver.executeQuery(
            identifier = null,
            sql = "SELECT id, recipient_amount_sat, address, is_default_address, mining_fees_sat, tx_id, created_at, confirmed_at, locked_at, channel_id, closing_info_type, closing_info_blob FROM channel_close_outgoing_payments",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    val payment = ChannelCloseOutgoingQueries.mapChannelCloseOutgoingPayment(
                        id = cursor.getString(0)!!,
                        amount_sat = cursor.getLong(1)!!,
                        address = cursor.getString(2)!!,
                        mining_fees_sat = cursor.getLong(3)!!,
                        is_default_address = cursor.getLong(4)!!,
                        tx_id = cursor.getBytes(5)!!,
                        created_at = cursor.getLong(6)!!,
                        confirmed_at = cursor.getLong(7),
                        locked_at = cursor.getLong(8),
                        channel_id = cursor.getBytes(9)!!,
                        closing_info_type = OutgoingPartClosingInfoTypeVersion.valueOf(cursor.getString(10)!!),
                        closing_info_blob = cursor.getBytes(11)!!
                    )
                    insertPayment(payment)
                }
                QueryResult.Unit
            }
        )

        TODO("migrate or drop/recreate table cloud_kit_payments")

        listOf(
            "DROP TABLE lightning_outgoing_payments",
            "DROP TABLE lightning_outgoing_payment_parts",
            "DROP TABLE inbound_liquidity_outgoing_payments",
            "DROP TABLE splice_outgoing_payments",
            "DROP TABLE splice_cpfp_outgoing_payments",
            "DROP TABLE channel_close_outgoing_payments",
        ).forEach { sql ->
            driver.execute(identifier = null, sql = sql, parameters = 0)
        }
    }
}