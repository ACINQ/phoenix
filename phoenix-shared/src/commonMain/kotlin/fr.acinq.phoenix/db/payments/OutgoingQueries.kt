/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.db.payments

import com.squareup.sqldelight.ColumnAdapter
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.ShortChannelId
import fr.acinq.lightning.channel.ChannelException
import fr.acinq.lightning.db.HopDesc
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.OutgoingPaymentFailure
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.lightning.wire.FailureMessage
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didCompleteWalletPayment

class OutgoingQueries(val database: PaymentsDatabase) {

    private val queries = database.outgoingPaymentsQueries

    fun addLightningParts(parentId: UUID, parts: List<OutgoingPayment.LightningPart>) {
        if (parts.isEmpty()) return
        database.transaction {
            parts.map {
                // This will throw an exception if the sqlite foreign-key-constraint is violated.
                queries.insertLightningPart(
                    part_id = it.id.toString(),
                    part_parent_id = parentId.toString(),
                    part_amount_msat = it.amount.msat,
                    part_route = it.route,
                    part_created_at = it.createdAt
                )
            }
        }
    }

    fun addPayment(payment: OutgoingPayment) {
        val (detailsTypeVersion, detailsData) = payment.details.mapToDb()
        database.transaction(noEnclosing = false) {
            queries.insertPayment(
                id = payment.id.toString(),
                recipient_amount_msat = payment.recipientAmount.msat,
                recipient_node_id = payment.recipient.toString(),
                payment_hash = payment.details.paymentHash.toByteArray(),
                created_at = payment.createdAt,
                details_type = detailsTypeVersion,
                details_blob = detailsData
            )
            payment.parts.map {
                when (it) {
                    is OutgoingPayment.LightningPart -> {
                        queries.insertLightningPart(
                            part_id = it.id.toString(),
                            part_parent_id = payment.id.toString(),
                            part_amount_msat = it.amount.msat,
                            part_route = it.route,
                            part_created_at = it.createdAt
                        )
                    }
                    is OutgoingPayment.ClosingTxPart -> {
                        val (closingInfoType, closingInfoBlob) = it.mapClosingTypeToDb()
                        queries.insertClosingTxPart(
                            id = it.id.toString(),
                            parent_id = payment.id.toString(),
                            tx_id = it.txId.toByteArray(),
                            amount_msat = it.claimed.sat,
                            closing_info_type = closingInfoType,
                            closing_info_blob = closingInfoBlob,
                            created_at = it.createdAt
                        )
                    }
                }
            }
        }
    }

    fun completePayment(id: UUID, completed: OutgoingPayment.Status.Completed): Boolean {
        var result = true
        database.transaction {
            val (statusType, statusBlob) = completed.mapToDb()
            queries.updatePayment(
                id = id.toString(),
                completed_at = completed.completedAt,
                status_type = statusType,
                status_blob = statusBlob
            )
            if (queries.changes().executeAsOne() != 1L) {
                result = false
            } else {
                didCompleteWalletPayment(WalletPaymentId.OutgoingPaymentId(id), database)
            }
        }
        return result
    }

    fun completePaymentForClosing(id: UUID, parts: List<OutgoingPayment.ClosingTxPart>, completed: OutgoingPayment.Status.Completed): Boolean {
        var result = true
        database.transaction {
            val (statusType, statusBlob) = completed.mapToDb()
            queries.updatePayment(
                id = id.toString(),
                completed_at = completed.completedAt,
                status_type = statusType,
                status_blob = statusBlob
            )
            parts.map {
                val (closingInfoType, closingInfoBlob) = it.mapClosingTypeToDb()
                queries.insertClosingTxPart(
                    id = it.id.toString(),
                    parent_id = id.toString(),
                    tx_id = it.txId.toByteArray(),
                    amount_msat = it.claimed.sat,
                    closing_info_type = closingInfoType,
                    closing_info_blob = closingInfoBlob,
                    created_at = it.createdAt
                )
            }
            if (queries.changes().executeAsOne() != 1L) {
                result = false
            } else {
                didCompleteWalletPayment(WalletPaymentId.OutgoingPaymentId(id), database)
            }
        }
        return result
    }

    fun updateLightningPart(
        partId: UUID,
        preimage: ByteVector32,
        completedAt: Long
    ): Boolean {
        var result = true
        val (statusTypeVersion, statusData) = OutgoingPayment.LightningPart.Status.Succeeded(preimage).mapToDb()
        database.transaction {
            queries.updateLightningPart(
                part_id = partId.toString(),
                part_status_type = statusTypeVersion,
                part_status_blob = statusData,
                part_completed_at = completedAt
            )
            if (queries.changes().executeAsOne() != 1L) {
                result = false
            }
        }
        return result
    }

    fun updateLightningPart(
        partId: UUID,
        failure: Either<ChannelException, FailureMessage>,
        completedAt: Long
    ): Boolean {
        var result = true
        val (statusTypeVersion, statusData) = OutgoingPaymentFailure.convertFailure(failure).mapToDb()
        database.transaction {
            queries.updateLightningPart(
                part_id = partId.toString(),
                part_status_type = statusTypeVersion,
                part_status_blob = statusData,
                part_completed_at = completedAt
            )
            if (queries.changes().executeAsOne() != 1L) {
                result = false
            }
        }
        return result
    }

    fun getPaymentFromPartId(partId: UUID): OutgoingPayment? {
        return queries.getLightningPart(part_id = partId.toString()).executeAsOneOrNull()?.run {
            queries.getPayment(id = part_parent_id, ::mapOutgoingPayment).executeAsList()
        }?.run {
            groupByRawOutgoing(this).firstOrNull()
        }?.run {
            filterUselessParts(this)
                // resulting payment must contain the request part id, or should be null
                .takeIf { p -> p.parts.map { it.id }.contains(partId) }
        }
    }

    fun getPayment(id: UUID): OutgoingPayment? {
        return queries.getPayment(
            id = id.toString(),
            mapper = ::mapOutgoingPayment
        ).executeAsList().run {
            groupByRawOutgoing(this).firstOrNull()
        }?.run {
            filterUselessParts(this)
        }
    }

    fun listPayments(paymentHash: ByteVector32): List<OutgoingPayment> {
        return queries.listPaymentsForPaymentHash(paymentHash.toByteArray(), ::mapOutgoingPayment).executeAsList()
            .run { groupByRawOutgoing(this) }
    }

    fun listPayments(count: Int, skip: Int): List<OutgoingPayment> {
        return queries.listPaymentsInOffset(
            limit = count.toLong(),
            offset = skip.toLong(),
            mapper = ::mapOutgoingPayment
        )
            .executeAsList()
            .run { groupByRawOutgoing(this) }
    }

    /** Group a list of outgoing payments by parent id and parts. */
    private fun groupByRawOutgoing(payments: List<OutgoingPayment>) = payments
        .takeIf { it.isNotEmpty() }
        ?.groupBy { it.id }
        ?.values
        ?.map { group -> group.first().copy(parts = group.flatMap { it.parts }) }
        ?: emptyList()

    /** Get a payment without its failed/pending parts. */
    private fun filterUselessParts(payment: OutgoingPayment): OutgoingPayment = when (payment.status) {
        is OutgoingPayment.Status.Completed.Succeeded.OffChain -> {
            payment.copy(parts = payment.parts.filter {
                (it is OutgoingPayment.LightningPart && it.status is OutgoingPayment.LightningPart.Status.Succeeded)
                        || it is OutgoingPayment.ClosingTxPart
            })
        }
        else -> payment
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun mapOutgoingPaymentWithoutParts(
            id: String,
            recipient_amount_msat: Long,
            recipient_node_id: String,
            payment_hash: ByteArray,
            details_type: OutgoingDetailsTypeVersion,
            details_blob: ByteArray,
            created_at: Long,
            completed_at: Long?,
            status_type: OutgoingStatusTypeVersion?,
            status_blob: ByteArray?
        ): OutgoingPayment {
            return OutgoingPayment(
                id = UUID.fromString(id),
                recipientAmount = MilliSatoshi(recipient_amount_msat),
                recipient = PublicKey(ByteVector(recipient_node_id)),
                details = OutgoingDetailsData.deserialize(details_type, details_blob),
                parts = listOf(),
                status = mapPaymentStatus(status_type, status_blob, completed_at),
                createdAt = created_at
            )
        }

        @Suppress("UNUSED_PARAMETER")
        fun mapOutgoingPayment(
            id: String,
            recipient_amount_msat: Long,
            recipient_node_id: String,
            payment_hash: ByteArray,
            details_type: OutgoingDetailsTypeVersion,
            details_blob: ByteArray,
            created_at: Long,
            completed_at: Long?,
            status_type: OutgoingStatusTypeVersion?,
            status_blob: ByteArray?,
            // lightning parts data, may be null
            lightning_part_id: String?,
            lightning_part_amount_msat: Long?,
            lightning_part_route: List<HopDesc>?,
            lightning_part_created_at: Long?,
            lightning_part_completed_at: Long?,
            lightning_part_status_type: OutgoingPartStatusTypeVersion?,
            lightning_part_status_blob: ByteArray?,
            // closing tx parts data, may be null
            closingtx_part_id: String?,
            closingtx_part_tx_id: ByteArray?,
            closingtx_part_amount_sat: Long?,
            closingtx_part_closing_info_type: OutgoingPartClosingInfoTypeVersion?,
            closingtx_part_closing_info_blob: ByteArray?,
            closingtx_part_created_at: Long?
        ): OutgoingPayment {
            val lightningParts = if (lightning_part_id != null && lightning_part_amount_msat != null && lightning_part_route != null && lightning_part_created_at != null) {
                listOf(
                    mapLightningPart(
                        id = lightning_part_id,
                        amountMsat = lightning_part_amount_msat,
                        route = lightning_part_route,
                        createdAt = lightning_part_created_at,
                        completedAt = lightning_part_completed_at,
                        statusType = lightning_part_status_type,
                        statusBlob = lightning_part_status_blob
                    )
                )
            } else emptyList()

            val closingTxParts = if (closingtx_part_id != null && closingtx_part_tx_id != null && closingtx_part_amount_sat != null
                && closingtx_part_closing_info_type != null && closingtx_part_closing_info_blob != null && closingtx_part_created_at != null
            ) {
                listOf(
                    mapClosingTxPart(
                        id = closingtx_part_id,
                        txId = closingtx_part_tx_id,
                        claimedSat = closingtx_part_amount_sat,
                        closingInfoType = closingtx_part_closing_info_type,
                        closingInfoBlob = closingtx_part_closing_info_blob,
                        createdAt = closingtx_part_created_at
                    )
                )
            } else if (status_type == OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0 && status_blob != null && completed_at != null) {
                // we used to store closing txs data in the payment status blob
                OutgoingStatusData.getClosingPartsFromV0Status(status_blob, completed_at)
            } else emptyList()

            return mapOutgoingPaymentWithoutParts(
                id = id,
                recipient_amount_msat = recipient_amount_msat,
                recipient_node_id = recipient_node_id,
                payment_hash = payment_hash,
                details_type = details_type,
                details_blob = details_blob,
                created_at = created_at,
                completed_at = completed_at,
                status_type = status_type,
                status_blob = status_blob
            ).copy(
                parts = lightningParts + closingTxParts
            )
        }

        private fun mapLightningPart(
            id: String,
            amountMsat: Long,
            route: List<HopDesc>,
            createdAt: Long,
            completedAt: Long?,
            statusType: OutgoingPartStatusTypeVersion?,
            statusBlob: ByteArray?
        ): OutgoingPayment.LightningPart {
            return OutgoingPayment.LightningPart(
                id = UUID.fromString(id),
                amount = MilliSatoshi(amountMsat),
                route = route,
                status = mapLightningPartStatus(
                    statusType = statusType,
                    statusBlob = statusBlob,
                    completedAt = completedAt
                ),
                createdAt = createdAt
            )
        }

        private fun mapClosingTxPart(
            id: String,
            txId: ByteArray,
            claimedSat: Long,
            closingInfoType: OutgoingPartClosingInfoTypeVersion,
            closingInfoBlob: ByteArray,
            createdAt: Long,
        ): OutgoingPayment.ClosingTxPart {
            return OutgoingPayment.ClosingTxPart(
                id = UUID.fromString(id),
                txId = txId.toByteVector32(),
                claimed = Satoshi(claimedSat),
                closingType = OutgoingPartClosingInfoData.deserialize(typeVersion = closingInfoType, blob = closingInfoBlob),
                createdAt = createdAt
            )
        }

        fun mapPaymentStatus(
            statusType: OutgoingStatusTypeVersion?,
            statusBlob: ByteArray?,
            completedAt: Long?,
        ): OutgoingPayment.Status = when {
            completedAt == null && statusType == null && statusBlob == null -> OutgoingPayment.Status.Pending
            completedAt != null && statusType != null && statusBlob != null -> OutgoingStatusData.deserialize(statusType, statusBlob, completedAt)
            else -> throw UnhandledOutgoingStatus(completedAt, statusType, statusBlob)
        }

        private fun mapLightningPartStatus(
            statusType: OutgoingPartStatusTypeVersion?,
            statusBlob: ByteArray?,
            completedAt: Long?,
        ): OutgoingPayment.LightningPart.Status = when {
            completedAt == null && statusType == null && statusBlob == null -> OutgoingPayment.LightningPart.Status.Pending
            completedAt != null && statusType != null && statusBlob != null -> OutgoingPartStatusData.deserialize(statusType, statusBlob, completedAt)
            else -> throw UnhandledOutgoingPartStatus(statusType, statusBlob, completedAt)
        }

        val hopDescAdapter: ColumnAdapter<List<HopDesc>, String> = object : ColumnAdapter<List<HopDesc>, String> {
            override fun decode(databaseValue: String): List<HopDesc> = when {
                databaseValue.isEmpty() -> listOf()
                else -> databaseValue.split(";").map { hop ->
                    val els = hop.split(":")
                    val n1 = PublicKey(ByteVector(els[0]))
                    val n2 = PublicKey(ByteVector(els[1]))
                    val cid = els[2].takeIf { it.isNotBlank() }?.run { ShortChannelId(this) }
                    HopDesc(n1, n2, cid)
                }
            }

            override fun encode(value: List<HopDesc>): String = value.joinToString(";") {
                "${it.nodeId}:${it.nextNodeId}:${it.shortChannelId ?: ""}"
            }
        }
    }
}

data class UnhandledOutgoingStatus(val completedAt: Long?, val statusTypeVersion: OutgoingStatusTypeVersion?, val statusData: ByteArray?) :
    RuntimeException("cannot map outgoing payment status data with completed_at=$completedAt status_type=$statusTypeVersion status=$statusData")

data class UnhandledOutgoingPartStatus(val status_type: OutgoingPartStatusTypeVersion?, val status_blob: ByteArray?, val completedAt: Long?) :
    RuntimeException("cannot map outgoing part status data [ completed_at=$completedAt status_type=$status_type status_blob=$status_blob]")