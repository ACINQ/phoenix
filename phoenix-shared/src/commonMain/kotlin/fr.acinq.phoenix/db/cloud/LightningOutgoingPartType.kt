package fr.acinq.phoenix.db.cloud

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.payments.OutgoingPartStatusData
import fr.acinq.phoenix.db.payments.OutgoingPartStatusTypeVersion
import fr.acinq.phoenix.db.payments.OutgoingQueries
import fr.acinq.phoenix.db.payments.mapToDb
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
    constructor(part: LightningOutgoingPayment.Part) : this(
        id = part.id,
        msat = part.amount.msat,
        route = OutgoingQueries.hopDescAdapter.encode(part.route),
        status = StatusWrapper(part.status),
        createdAt = part.createdAt
    )

    fun unwrap() = LightningOutgoingPayment.Part(
        id = id,
        amount = MilliSatoshi(msat = msat),
        route = OutgoingQueries.hopDescAdapter.decode(route),
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
        companion object {
            // constructor
            operator fun invoke(status: LightningOutgoingPayment.Part.Status): StatusWrapper? {
                return when (status) {
                    is LightningOutgoingPayment.Part.Status.Pending -> null
                    is LightningOutgoingPayment.Part.Status.Failed -> {
                        val (type, blob) = status.mapToDb()
                        StatusWrapper(
                            ts = status.completedAt,
                            type = type.name,
                            blob = blob
                        )
                    }
                    is LightningOutgoingPayment.Part.Status.Succeeded -> {
                        val (type, blob) = status.mapToDb()
                        StatusWrapper(
                            ts = status.completedAt,
                            type = type.name,
                            blob = blob
                        )
                    }
                }
            }
        } // </companion object>

        fun unwrap(): LightningOutgoingPayment.Part.Status {
            return OutgoingPartStatusData.deserialize(
                typeVersion = OutgoingPartStatusTypeVersion.valueOf(type),
                blob = blob,
                completedAt = ts
            )
        }
    } // </StatusWrapper>

} // </OutgoingPartData>
