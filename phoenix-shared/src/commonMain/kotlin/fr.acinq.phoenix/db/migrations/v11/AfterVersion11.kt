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

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.OnChainIncomingPayment
import fr.acinq.lightning.db.OnChainOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.serialization.payment.Serialization
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.db.migrations.v11.queries.ChannelCloseOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.queries.InboundLiquidityQueries
import fr.acinq.phoenix.db.migrations.v11.queries.LightningOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.queries.SpliceCpfpOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.queries.SpliceOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingDetailsTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartClosingInfoTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartStatusTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingStatusTypeVersion
import fr.acinq.phoenix.db.payments.LnurlBase
import fr.acinq.phoenix.db.payments.LnurlMetadata
import fr.acinq.phoenix.db.payments.LnurlSuccessAction
import fr.acinq.phoenix.db.payments.WalletPaymentMetadataRow
import fr.acinq.phoenix.utils.extensions.deriveUUID
import fr.acinq.phoenix.utils.extensions.toByteArray

val AfterVersion11 = AfterVersion(11) { driver ->

    fun insertPayment(payment: OutgoingPayment) {
        driver.execute(
            identifier = null,
            sql = "INSERT INTO payments_outgoing (id, payment_hash, tx_id, created_at, completed_at, succeeded_at, data) VALUES (?, ?, ?, ?, ?, ?, ?)",
            parameters = 7
        ) {
            val (paymentHash, txId) = when (payment) {
                is LightningOutgoingPayment -> payment.paymentHash to null
                is OnChainOutgoingPayment -> null to payment.txId
            }
            bindBytes(0, payment.id.toByteArray())
            bindBytes(1, paymentHash?.toByteArray())
            bindBytes(2, txId?.value?.toByteArray())
            bindLong(3, payment.createdAt)
            bindLong(4, payment.completedAt)
            bindLong(5, payment.succeededAt)
            bindBytes(6, Serialization.serialize(payment))
        }
    }

    val (lightningOutgoingPayments, channelCloseOutgoingPayments) = driver.executeQuery(
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
|       lightning_parts.part_status_blob AS lightning_part_status_blob,
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
            val lightningOutgoingPayments = mutableListOf<LightningOutgoingPayment>()
            val channelCloseOutgoingPayments = mutableListOf<ChannelCloseOutgoingPayment>()
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
                    cursor.getString(12)
                        ?.let { LightningOutgoingQueries.hopDescAdapter.decode(it) },
                    cursor.getLong(13),
                    cursor.getLong(14),
                    cursor.getString(15)?.let { OutgoingPartStatusTypeVersion.valueOf(it) },
                    cursor.getBytes(16),
                    cursor.getString(17),
                    cursor.getBytes(18),
                    cursor.getLong(19),
                    cursor.getString(20)
                        ?.let { OutgoingPartClosingInfoTypeVersion.valueOf(it) },
                    cursor.getBytes(21),
                    cursor.getLong(22),
                )

                when (payment) {
                    is LightningOutgoingPayment -> lightningOutgoingPayments.add(payment)
                    is ChannelCloseOutgoingPayment -> channelCloseOutgoingPayments.add(payment)
                    else -> error("impossible")
                }
            }
            QueryResult.Value(lightningOutgoingPayments.toList() to channelCloseOutgoingPayments.toList())
        }
    ).value

    /** Group a list of lightning outgoing payments by parent id and parts. */
    fun groupByRawLightningOutgoing(payments: List<LightningOutgoingPayment>) = payments
        .takeIf { it.isNotEmpty() }
        ?.groupBy { it.id }
        ?.values
        ?.map { group -> group.first().copy(parts = group.flatMap { it.parts }) }
        ?: emptyList()

    groupByRawLightningOutgoing(lightningOutgoingPayments)
        .map { insertPayment(it) }

    /** Group a list of channel close outgoing payments by parent id and parts. */
    fun groupByRawChannelCloseOutgoing(payments: List<ChannelCloseOutgoingPayment>) = payments
        .groupBy { it.id }
        .values
        .map {
            it.reduce { close1, close2 ->
                close1.copy(
                    recipientAmount = close1.recipientAmount + close2.recipientAmount,
                    miningFee = close1.miningFee + close2.miningFee
                )
            }
        }

    groupByRawChannelCloseOutgoing(channelCloseOutgoingPayments)
        .map { insertPayment(it) }

    val incomingPayments = driver.executeQuery(
        identifier = null,
        sql = "SELECT data FROM payments_incoming",
        parameters = 0,
        mapper = { cursor ->
            val result = buildMap<TxId, IncomingPayment> {
                while (cursor.next().value) {
                    val data = cursor.getBytes(0)!!
                    when(val incomingPayment = Serialization.deserialize(data).getOrThrow()) {
                        is LightningIncomingPayment ->
                            when (val txId = incomingPayment.parts
                                .filterIsInstance<LightningIncomingPayment.Part.Htlc>()
                                .firstNotNullOfOrNull { it.fundingFee?.fundingTxId }) {
                                is TxId -> put(txId, incomingPayment)
                                else -> {}
                            }
                        is NewChannelIncomingPayment -> put(incomingPayment.txId, incomingPayment)
                        else -> {}
                    }
                }
            }
            QueryResult.Value(result)
        }
    ).value

    driver.executeQuery(
        identifier = null,
        sql = "SELECT id, mining_fees_sat, channel_id, tx_id, lease_type, lease_blob, created_at, confirmed_at, locked_at FROM inbound_liquidity_outgoing_payments",
        parameters = 0,
        mapper = { cursor ->
            while (cursor.next().value) {

                val txId = TxId(cursor.getBytes(3)!!.toByteVector32())
                val (updatedIncomingPayment, liquidityPayment) = InboundLiquidityQueries.mapPayment(
                    id = cursor.getString(0)!!,
                    mining_fees_sat = cursor.getLong(1)!!,
                    channel_id = cursor.getBytes(2)!!,
                    tx_id = cursor.getBytes(3)!!,
                    lease_type = cursor.getString(4)!!,
                    lease_blob = cursor.getBytes(5)!!,
                    created_at = cursor.getLong(6)!!,
                    confirmed_at = cursor.getLong(7),
                    locked_at = cursor.getLong(8),
                    incomingPayment = incomingPayments[txId]
                )

                updatedIncomingPayment?.let {
                    driver.execute(
                        identifier = null,
                        sql = "UPDATE payments_incoming SET data=?, tx_id=? WHERE id=?",
                        parameters = 3
                    ) {
                        bindBytes(0, Serialization.serialize(updatedIncomingPayment))
                        bindBytes(1, when (updatedIncomingPayment) {
                            is LightningIncomingPayment -> updatedIncomingPayment.liquidityPurchaseDetails?.txId
                            is OnChainIncomingPayment -> updatedIncomingPayment.txId
                            else -> null
                        }?.value?.toByteArray())
                        bindBytes(2, updatedIncomingPayment.id.toByteArray())
                    }
                }

                liquidityPayment?.let { insertPayment(liquidityPayment) }
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

    val metadataLinks = driver.executeQuery(
        identifier = null,
        sql = """
            SELECT type, id, lnurl_base_type, lnurl_base_blob, lnurl_description, lnurl_metadata_type, lnurl_metadata_blob, lnurl_successAction_type, lnurl_successAction_blob, user_description, user_notes, modified_at, original_fiat_type, original_fiat_rate
            FROM payments_metadata_old
        """.trimIndent(),
        parameters = 0,
        mapper = { cursor ->
            val result = buildList {
                while (cursor.next().value) {
                    val type = cursor.getLong(0)!!
                    val id = cursor.getString(1)!!.let { if (type == 1L) ByteVector32(it).deriveUUID() else UUID.fromString(it) }
                    val lnurlBase = cursor.getString(2)?.let { t -> cursor.getBytes(3)?.let { LnurlBase.TypeVersion.valueOf(t) to it } }
                    val lnurlDesc = cursor.getString(4)
                    val lnurlMetadata = cursor.getString(5)?.let { t -> cursor.getBytes(6)?.let { LnurlMetadata.TypeVersion.valueOf(t) to it } }
                    val lnurlSuccessAction = cursor.getString(7)?.let { t -> cursor.getBytes(8)?.let { LnurlSuccessAction.TypeVersion.valueOf(t) to it } }
                    val userDesc = cursor.getString(9)
                    val userNotes = cursor.getString(10)
                    val modifiedAt = cursor.getLong(11)
                    val originalFiat = cursor.getString(12)?.let { t -> cursor.getDouble(13)?.let { t to it }}

                    add(id to WalletPaymentMetadataRow(lnurl_base = lnurlBase, lnurl_metadata = lnurlMetadata, lnurl_successAction =  lnurlSuccessAction, lnurl_description = lnurlDesc,
                        original_fiat = originalFiat, user_description = userDesc, user_notes = userNotes, modified_at = modifiedAt))
                }
            }
            QueryResult.Value(result)
        }
    ).value

    metadataLinks
        .forEach { (paymentId, metadata) ->
            driver.execute(
                identifier = null,
                sql = """
                    INSERT INTO payments_metadata (
                                payment_id,
                                lnurl_base_type, lnurl_base_blob,
                                lnurl_description,
                                lnurl_metadata_type, lnurl_metadata_blob,
                                lnurl_successAction_type, lnurl_successAction_blob,
                                user_description, user_notes,
                                modified_at,
                                original_fiat_type, original_fiat_rate)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                parameters = 13
            ) {
                bindBytes(0, paymentId.toByteArray())
                bindString(1, metadata.lnurl_base?.first?.let { EnumColumnAdapter<LnurlBase.TypeVersion>().encode(it) })
                bindBytes(2, metadata.lnurl_base?.second)
                bindString(3, metadata.lnurl_description)
                bindString(4, metadata.lnurl_metadata?.first?.let { EnumColumnAdapter<LnurlMetadata.TypeVersion>().encode(it) })
                bindBytes(5, metadata.lnurl_metadata?.second)
                bindString(6, metadata.lnurl_successAction?.first?.let { EnumColumnAdapter<LnurlSuccessAction.TypeVersion>().encode(it) })
                bindBytes(7, metadata.lnurl_successAction?.second)
                bindString(8, metadata.user_description)
                bindString(9, metadata.user_notes)
                bindLong(10, metadata.modified_at)
                bindString(11, metadata.original_fiat?.first)
                bindDouble(12, metadata.original_fiat?.second)
            }
        }

    data class OnChainLink(val txId: ByteArray, val paymentId: UUID, val confirmedAt: Long?, val lockedAt: Long?)

    val onChainTxLinks = driver.executeQuery(
        identifier = null,
        sql = """
            SELECT tx_id, type, id, confirmed_at, locked_at
            FROM link_tx_to_payments
        """.trimIndent(),
        parameters = 0,
        mapper = { cursor ->
            val result = buildList {
                while (cursor.next().value) {
                    val txId = cursor.getBytes(0)!!
                    val type = cursor.getLong(1)!!
                    val id = cursor.getString(2)!!.let { if (type == 1L) ByteVector32(it).deriveUUID() else UUID.fromString(it) }
                    val confirmedAt = cursor.getLong(3)
                    val lockedAt = cursor.getLong(4)
                    add(OnChainLink(txId = txId, paymentId = id, confirmedAt = confirmedAt, lockedAt = lockedAt))
                }
            }
            QueryResult.Value(result)
        }
    ).value

    onChainTxLinks
        .forEach { onChainTxLink ->
            driver.execute(
                identifier = null,
                sql = """
                    INSERT INTO on_chain_txs (payment_id, tx_id, confirmed_at, locked_at) VALUES (?, ?, ?, ?)
                """.trimIndent(),
                parameters = 4
            ) {
                bindBytes(0, onChainTxLink.paymentId.toByteArray())
                bindBytes(1, onChainTxLink.txId)
                bindLong(2, onChainTxLink.confirmedAt)
                bindLong(3, onChainTxLink.lockedAt)
            }
        }

    data class MetadataRow(
        val unpaddedSize: Long?,
        val recordCreation: Long?,
        val recordBlob: ByteArray?,
    )

    val cloudMetadata = driver.executeQuery(
        identifier = null,
        sql = """
            SELECT type, id, unpadded_size, record_creation, record_blob
            FROM cloudkit_payments_metadata_old
        """.trimIndent(),
        parameters = 0,
        mapper = { cursor ->
            val result = buildList {
                while (cursor.next().value) {
                    val type = cursor.getLong(0)!!
                    val id = cursor.getString(1)!!.let { if (type == 1L) ByteVector32(it).deriveUUID() else UUID.fromString(it) }
                    val unpaddedSize = cursor.getLong(2)
                    val recordCreation = cursor.getLong(3)
                    val recordBlob = cursor.getBytes(4)

                    add(id to MetadataRow(unpaddedSize, recordCreation, recordBlob))
                }
            }
            QueryResult.Value(result)
        }
    ).value

    cloudMetadata
        .forEach { (paymentId, metadata) ->
            driver.execute(
                identifier = null,
                sql = "INSERT INTO cloudkit_payments_metadata (id, unpadded_size, record_creation, record_blob) VALUES (?, ?, ?, ?)",
                parameters = 4
            ) {
                bindBytes(0, paymentId.toByteArray())
                bindLong(1, metadata.unpaddedSize)
                bindLong(2, metadata.recordCreation)
                bindBytes(3, metadata.recordBlob)
            }
        }

    listOf(
        "DROP TABLE outgoing_payment_parts",            // Foreign key constraint: must be before `outgoing_payments`
        "DROP TABLE outgoing_payment_closing_tx_parts", // Foreign key constraint: must be before `outgoing_payments`
        "DROP TABLE outgoing_payments",
        "DROP TABLE inbound_liquidity_outgoing_payments",
        "DROP TABLE splice_outgoing_payments",
        "DROP TABLE splice_cpfp_outgoing_payments",
        "DROP TABLE channel_close_outgoing_payments",
        "DROP TABLE payments_metadata_old",
        "DROP TABLE cloudkit_payments_metadata_old",
        "DROP TABLE cloudkit_payments_queue_old",
        "DROP TABLE link_tx_to_payments"
    ).forEach { sql ->
        driver.execute(identifier = null, sql = sql, parameters = 0)
    }
}