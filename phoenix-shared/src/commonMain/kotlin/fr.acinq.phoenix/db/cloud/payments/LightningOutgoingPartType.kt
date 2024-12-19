package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import fr.acinq.phoenix.db.migrations.v11.queries.LightningOutgoingQueries
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartStatusData
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingPartStatusTypeVersion
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString


/** Legacy object used when channel closing were stored as outgoing-payments parts. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LightningOutgoingClosingTxPartWrapper(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @ByteString val txId: ByteArray,
    val sat: Long,
    val info: ClosingInfoWrapper,
    val createdAt: Long
) {
    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class ClosingInfoWrapper(
        val type: String,
        @ByteString val blob: ByteArray
    )
}

@Serializable
data class LightningOutgoingPartWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val msat: Long,
    val route: String,
    val status: StatusWrapper?,
    val createdAt: Long
) {

    fun unwrap() = LightningOutgoingPayment.Part(
        id = id,
        amount = MilliSatoshi(msat = msat),
        route = LightningOutgoingQueries.hopDescAdapter.decode(route),
        status = status?.unwrap() ?: LightningOutgoingPayment.Part.Status.Pending,
        createdAt = createdAt
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class StatusWrapper(
        val ts: Long, // timestamp: completedAt
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        fun unwrap(): LightningOutgoingPayment.Part.Status {
            return OutgoingPartStatusData.deserialize(
                typeVersion = OutgoingPartStatusTypeVersion.valueOf(type),
                blob = blob,
                completedAt = ts
            )
        }
    } // </StatusWrapper>

} // </OutgoingPartData>
