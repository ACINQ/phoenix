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

package fr.acinq.phoenix.db.migrations.v11.queries

import app.cash.sqldelight.ColumnAdapter
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.ShortChannelId
import fr.acinq.lightning.db.HopDesc
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingDetailsData
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingDetailsTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartClosingInfoTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartStatusData
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartStatusTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingStatusData
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingStatusTypeVersion
import fr.acinq.phoenix.utils.migrations.LegacyChannelCloseHelper
import fr.acinq.secp256k1.Hex

class LightningOutgoingQueries(val database: PaymentsDatabase) {

//    /**
//     * Returns a [LightningOutgoingPayment] for this id - if instead we find legacy converted to a new type (such as
//     * [ChannelCloseOutgoingPayment], this payment is ignored and we return null instead.
//     */
//    fun getPaymentStrict(id: UUID): LightningOutgoingPayment? = queries.getPayment(
//        id = id.toString(),
//        mapper = Companion::mapLightningOutgoingPayment
//    ).executeAsList().let { parts ->
//        // only take regular LN payments parts, and group them
//        parts.filterIsInstance<LightningOutgoingPayment>().let {
//            groupByRawLightningOutgoing(it).firstOrNull()
//        }?.let {
//            filterUselessParts(it)
//        }
//    }

//    /**
//     * May return a [ChannelCloseOutgoingPayment] instead of the expected [LightningOutgoingPayment]. That's because
//     * channel closing used to be stored as [LightningOutgoingPayment] with special closing parts. We convert those to
//     * the propert object type.
//     */
//    fun getPaymentRelaxed(id: UUID): OutgoingPayment? = queries.getPayment(
//        id = id.toString(),
//        mapper = Companion::mapLightningOutgoingPayment
//    ).executeAsList().let { parts ->
//        // this payment may be a legacy channel closing - otherwise, only take regular LN payment parts, and group them
//        parts.firstOrNull { it is ChannelCloseOutgoingPayment } ?: parts.filterIsInstance<LightningOutgoingPayment>().let {
//            groupByRawLightningOutgoing(it).firstOrNull()
//        }?.let {
//            filterUselessParts(it)
//        }
//    }

    /** Group a list of outgoing payments by parent id and parts. */
//    private fun groupByRawLightningOutgoing(payments: List<LightningOutgoingPayment>) = payments
//        .takeIf { it.isNotEmpty() }
//        ?.groupBy { it.id }
//        ?.values
//        ?.map { group -> group.first().copy(parts = group.flatMap { it.parts }) }
//        ?: emptyList()
//
//    /** Get a payment without its failed/pending parts. */
//    private fun filterUselessParts(payment: LightningOutgoingPayment): LightningOutgoingPayment = when (payment.status) {
//        is LightningOutgoingPayment.Status.Completed.Succeeded -> {
//            payment.copy(parts = payment.parts.filter {
//                it.status is LightningOutgoingPayment.Part.Status.Succeeded
//            })
//        }
//        else -> payment
//    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun mapLightningOutgoingPaymentWithoutParts(
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
        ): LightningOutgoingPayment {
            val details = OutgoingDetailsData.deserialize(details_type, details_blob)
            return if (details != null) {
                LightningOutgoingPayment(
                    id = UUID.fromString(id),
                    recipientAmount = MilliSatoshi(recipient_amount_msat),
                    recipient = PublicKey.parse(Hex.decode(recipient_node_id)),
                    details = details,
                    parts = listOf(),
                    status = mapPaymentStatus(status_type, status_blob, completed_at),
                    createdAt = created_at
                )
            } else throw IllegalArgumentException("cannot handle closing payment at this stage, use LegacyChannelCloseHelper")
        }

        @Suppress("UNUSED_PARAMETER")
        fun mapLightningOutgoingPayment(
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

            // handle legacy cases where the outgoing_payments tables would contain the details for channel closing.
            // we map these legacy data to the new ChannelCloseOutgoingPayment object, using placeholders when needed.
            if (details_type == OutgoingDetailsTypeVersion.CLOSING_V0 || closingtx_part_id != null) {
                 try {
                     return LegacyChannelCloseHelper.convertLegacyToChannelClose(
                         id = UUID.fromString(id),
                         recipientAmount = recipient_amount_msat.msat,
                         detailsBlob = if (details_type == OutgoingDetailsTypeVersion.CLOSING_V0) details_blob else null,
                         statusBlob = if (status_type == OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0) status_blob else null,
                         partsAmount = closingtx_part_amount_sat?.sat,
                         partsTxId = closingtx_part_tx_id?.toByteVector32(),
                         partsClosingTypeBlob = closingtx_part_closing_info_blob,
                         confirmedAt = completed_at ?: created_at,
                         createdAt = created_at,
                     )
                 } catch (_: Exception) { }
            }

            val parts = if (lightning_part_id != null && lightning_part_amount_msat != null && lightning_part_route != null && lightning_part_created_at != null) {
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

            return mapLightningOutgoingPaymentWithoutParts(
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
                parts = parts
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
        ): LightningOutgoingPayment.Part {
            return LightningOutgoingPayment.Part(
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

        private fun mapPaymentStatus(
            statusType: OutgoingStatusTypeVersion?,
            statusBlob: ByteArray?,
            completedAt: Long?,
        ): LightningOutgoingPayment.Status = when {
            completedAt == null && statusType == null && statusBlob == null -> LightningOutgoingPayment.Status.Pending
            completedAt != null && statusType != null && statusBlob != null -> OutgoingStatusData.deserialize(statusType, statusBlob, completedAt)
            else -> throw UnhandledOutgoingStatus(completedAt, statusType, statusBlob)
        }

        private fun mapLightningPartStatus(
            statusType: OutgoingPartStatusTypeVersion?,
            statusBlob: ByteArray?,
            completedAt: Long?,
        ): LightningOutgoingPayment.Part.Status = when {
            completedAt == null && statusType == null && statusBlob == null -> LightningOutgoingPayment.Part.Status.Pending
            completedAt != null && statusType != null && statusBlob != null -> OutgoingPartStatusData.deserialize(statusType, statusBlob, completedAt)
            else -> throw UnhandledOutgoingPartStatus(statusType, statusBlob, completedAt)
        }

        val hopDescAdapter: ColumnAdapter<List<HopDesc>, String> = object : ColumnAdapter<List<HopDesc>, String> {
            override fun decode(databaseValue: String): List<HopDesc> = when {
                databaseValue.isEmpty() -> listOf()
                else -> databaseValue.split(";").map { hop ->
                    val els = hop.split(":")
                    val n1 = PublicKey.parse(Hex.decode(els[0]))
                    val n2 = PublicKey.parse(Hex.decode(els[1]))
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