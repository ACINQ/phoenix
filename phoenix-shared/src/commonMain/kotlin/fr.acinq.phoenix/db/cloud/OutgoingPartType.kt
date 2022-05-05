package fr.acinq.phoenix.db.cloud

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.payments.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString

@Serializable
data class OutgoingPartWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val msat: Long,
    val route: String,
    val status: StatusWrapper?,
    val createdAt: Long
) {
    constructor(part: OutgoingPayment.LightningPart): this(
        id = part.id,
        msat = part.amount.msat,
        route = OutgoingQueries.hopDescAdapter.encode(part.route),
        status = StatusWrapper(part.status),
        createdAt = part.createdAt
    )

    fun unwrap() = OutgoingPayment.LightningPart(
        id = id,
        amount = MilliSatoshi(msat = msat),
        route = OutgoingQueries.hopDescAdapter.decode(route),
        status = status?.unwrap() ?: OutgoingPayment.LightningPart.Status.Pending,
        createdAt = createdAt
    )

    @Serializable
    data class StatusWrapper @OptIn(ExperimentalSerializationApi::class) constructor(
        val ts: Long, // timestamp: completedAt
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(status: OutgoingPayment.LightningPart.Status): StatusWrapper? {
                return when (status) {
                    is OutgoingPayment.LightningPart.Status.Pending -> null
                    is OutgoingPayment.LightningPart.Status.Failed -> {
                        val (type, blob) = status.mapToDb()
                        StatusWrapper(
                            ts = status.completedAt,
                            type = type.name,
                            blob = blob
                        )
                    }
                    is OutgoingPayment.LightningPart.Status.Succeeded -> {
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

        fun unwrap(): OutgoingPayment.LightningPart.Status {
            return OutgoingPartStatusData.deserialize(
                typeVersion = OutgoingPartStatusTypeVersion.valueOf(type),
                blob = blob,
                completedAt = ts
            )
        }
    } // </StatusWrapper>

} // </OutgoingPartData>
