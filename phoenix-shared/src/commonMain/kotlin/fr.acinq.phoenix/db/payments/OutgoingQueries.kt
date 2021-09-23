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
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.ShortChannelId
import fr.acinq.lightning.channel.ChannelException
import fr.acinq.lightning.db.HopDesc
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.OutgoingPaymentFailure
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.FailureMessage
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didCompleteWalletPayment
import fracinqphoenixdb.OutgoingPaymentsQueries

class OutgoingQueries(val database: PaymentsDatabase) {

    private val queries = database.outgoingPaymentsQueries

    fun addOutgoingParts(parentId: UUID, parts: List<OutgoingPayment.Part>) {
        if (parts.isEmpty()) return
        database.transaction {
            parts.map {
                // This will throw an exception if the sqlite foreign-key-constraint is violated.
                queries.addOutgoingPart(
                    part_id = it.id.toString(),
                    part_parent_id = parentId.toString(),
                    part_amount_msat = it.amount.msat,
                    part_route = it.route,
                    part_created_at = it.createdAt
                )
            }
        }
    }

    fun addOutgoingPayment(outgoingPayment: OutgoingPayment) {
        val (detailsTypeVersion, detailsData) = outgoingPayment.details.mapToDb()
        database.transaction(noEnclosing = false) {
            queries.addOutgoingPayment(
                id = outgoingPayment.id.toString(),
                recipient_amount_msat = outgoingPayment.recipientAmount.msat,
                recipient_node_id = outgoingPayment.recipient.toString(),
                payment_hash = outgoingPayment.details.paymentHash.toByteArray(),
                created_at = outgoingPayment.createdAt,
                details_type = detailsTypeVersion,
                details_blob = detailsData
            )
            outgoingPayment.parts.map {
                queries.addOutgoingPart(
                    part_id = it.id.toString(),
                    part_parent_id = outgoingPayment.id.toString(),
                    part_amount_msat = it.amount.msat,
                    part_route = it.route,
                    part_created_at = it.createdAt
                )
            }
        }
    }

    fun completeOutgoingPayment(id: UUID, completed: OutgoingPayment.Status.Completed): Boolean {
        var result = true
        database.transaction {
            val (statusType, statusBlob) = completed.mapToDb()
            queries.updateOutgoingPayment(
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

    fun updateOutgoingPart(
        partId: UUID,
        preimage: ByteVector32,
        completedAt: Long
    ): Boolean {
        var result = true
        val (statusTypeVersion, statusData) = OutgoingPayment.Part.Status.Succeeded(preimage).mapToDb()
        database.transaction {
            queries.updateOutgoingPart(
                part_id = partId.toString(),
                part_status_type = statusTypeVersion,
                part_status_blob = statusData,
                part_completed_at = completedAt
            )
            if (queries.changes().executeAsOne() != 1L) {
                result = false
            } else {
                queries.getOutgoingPart(
                    part_id = partId.toString()
                ).executeAsOneOrNull()?.let {
                    val parentId = UUID.fromString(it.part_parent_id)
                    didCompleteWalletPayment(WalletPaymentId.OutgoingPaymentId(parentId), database)
                }
            }
        }
        return result
    }

    fun updateOutgoingPart(
        partId: UUID,
        failure: Either<ChannelException, FailureMessage>,
        completedAt: Long
    ): Boolean {
        var result = true
        val (statusTypeVersion, statusData) = OutgoingPaymentFailure.convertFailure(failure).mapToDb()
        database.transaction {
            queries.updateOutgoingPart(
                part_id = partId.toString(),
                part_status_type = statusTypeVersion,
                part_status_blob = statusData,
                part_completed_at = completedAt
            )
            if (queries.changes().executeAsOne() != 1L) {
                result = false
            } else {
                queries.getOutgoingPart(
                    part_id = partId.toString()
                ).executeAsOneOrNull()?.let {
                    val parentId = UUID.fromString(it.part_parent_id)
                    didCompleteWalletPayment(WalletPaymentId.OutgoingPaymentId(parentId), database)
                }
            }
        }
        return result
    }

    fun getOutgoingPart(partId: UUID): OutgoingPayment? {
        return queries.getOutgoingPart(part_id = partId.toString()).executeAsOneOrNull()?.run {
            queries.getOutgoingPayment(id = part_parent_id, ::mapOutgoingPayment).executeAsList()
        }?.run {
            groupByRawOutgoing(this).firstOrNull()
        }?.run {
            filterUselessParts(this)
                // resulting payment must contain the request part id, or should be null
                .takeIf { p -> p.parts.map { it.id }.contains(partId) }
        }
    }

    fun getOutgoingPaymentWithoutParts(id: UUID): OutgoingPayment? {
        return queries.getOutgoingPaymentWithoutParts(
            id = id.toString(),
            mapper = ::mapOutgoingPaymentWithoutParts
        ).executeAsOneOrNull()
    }

    fun getOutgoingPayment(id: UUID): OutgoingPayment? {
        return queries.getOutgoingPayment(
            id = id.toString(),
            mapper = ::mapOutgoingPayment
        ).executeAsList().run {
            groupByRawOutgoing(this).firstOrNull()
        }?.run {
            filterUselessParts(this)
        }
    }

    fun listOutgoingPayments(paymentHash: ByteVector32): List<OutgoingPayment> {
        return queries.listOutgoingForPaymentHash(paymentHash.toByteArray(), ::mapOutgoingPayment).executeAsList()
            .run { groupByRawOutgoing(this) }
    }

    fun listOutgoingPayments(count: Int, skip: Int): List<OutgoingPayment> {
        // LIMIT ?, ? : "the first expression is used as the OFFSET expression and the second as the LIMIT expression."
        return queries.listOutgoingInOffset(skip.toLong(), count.toLong(), ::mapOutgoingPayment).executeAsList()
            .run { groupByRawOutgoing(this) }
    }

    /** Group a list of outgoing payments by parent id and parts. */
    private fun groupByRawOutgoing(payments: List<OutgoingPayment>) = payments
        .takeIf { it.isNotEmpty() }
        ?.groupBy { it.id }
        ?.values
        ?.map { group -> group.first().copy(parts = group.flatMap { it.parts }) }
        ?: listOf()

    /** Filter failed/pending parts of successful outgoing payments. */
    private fun filterUselessParts(payment: OutgoingPayment): OutgoingPayment = when (payment.status) {
        is OutgoingPayment.Status.Completed.Succeeded.OffChain -> {
            payment.copy(parts = payment.parts.filter { it.status is OutgoingPayment.Part.Status.Succeeded })
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
                status = mapOutgoingPaymentStatus(status_type, status_blob, completed_at),
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
            part_id: String?,
            part_amount_msat: Long?,
            part_route: List<HopDesc>?,
            part_created_at: Long?,
            part_completed_at: Long?,
            part_status_type: OutgoingPartStatusTypeVersion?,
            part_status_blob: ByteArray?
        ): OutgoingPayment {
            val parts = if (part_id != null && part_amount_msat != null && part_route != null && part_created_at != null) {
                listOf(
                    mapOutgoingPaymentPart(
                        part_id,
                        id,
                        part_amount_msat,
                        part_route,
                        part_created_at,
                        part_completed_at,
                        part_status_type,
                        part_status_blob
                    )
                )
            } else emptyList()

            return mapOutgoingPaymentWithoutParts(
                id,
                recipient_amount_msat,
                recipient_node_id,
                payment_hash,
                details_type,
                details_blob,
                created_at,
                completed_at,
                status_type,
                status_blob
            ).copy(
                parts = parts
            )
        }

        @Suppress("UNUSED_PARAMETER")
        fun mapOutgoingPaymentPart(
            part_id: String,
            part_parent_id: String,
            part_amount_msat: Long,
            part_route: List<HopDesc>,
            part_created_at: Long,
            part_completed_at: Long?,
            part_status_type: OutgoingPartStatusTypeVersion?,
            part_status_blob: ByteArray?
        ): OutgoingPayment.Part {
            return OutgoingPayment.Part(
                id = UUID.fromString(part_id),
                amount = MilliSatoshi(part_amount_msat),
                route = part_route,
                status = mapOutgoingPartStatus(
                    statusType = part_status_type,
                    statusBlob = part_status_blob,
                    completedAt = part_completed_at
                ),
                createdAt = part_created_at
            )
        }

        fun mapOutgoingPaymentStatus(
            status_type: OutgoingStatusTypeVersion?,
            status: ByteArray?,
            completed_at: Long?,
        ): OutgoingPayment.Status = when {
            completed_at == null && status_type == null && status == null -> OutgoingPayment.Status.Pending
            completed_at != null && status_type != null && status != null ->  OutgoingStatusData.deserialize(status_type, status, completed_at)
            else -> throw UnhandledOutgoingStatus(completed_at, status_type, status)
        }

        private fun mapOutgoingPartStatus(
            statusType: OutgoingPartStatusTypeVersion?,
            statusBlob: ByteArray?,
            completedAt: Long?,
        ): OutgoingPayment.Part.Status = when {
            completedAt == null && statusType == null && statusBlob == null -> OutgoingPayment.Part.Status.Pending
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