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
    constructor(part: OutgoingPayment.Part): this(
        id = part.id,
        msat = part.amount.msat,
        route = OutgoingQueries.hopDescAdapter.encode(part.route),
        status = StatusWrapper(part.status),
        createdAt = part.createdAt
    )

    fun unwrap() = OutgoingPayment.Part(
        id = id,
        amount = MilliSatoshi(msat = msat),
        route = OutgoingQueries.hopDescAdapter.decode(route),
        status = status?.unwrap() ?: OutgoingPayment.Part.Status.Pending,
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
            operator fun invoke(status: OutgoingPayment.Part.Status): StatusWrapper? {
                return when (status) {
                    is OutgoingPayment.Part.Status.Pending -> null
                    is OutgoingPayment.Part.Status.Failed -> {
                        val (type, blob) = status.mapToDb()
                        StatusWrapper(
                            ts = status.completedAt,
                            type = type.name,
                            blob = blob
                        )
                    }
                    is OutgoingPayment.Part.Status.Succeeded -> {
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

        fun unwrap(): OutgoingPayment.Part.Status {
            return OutgoingPartStatusData.deserialize(
                typeVersion = OutgoingPartStatusTypeVersion.valueOf(type),
                blob = blob,
                completedAt = ts
            )
        }
    } // </StatusWrapper>

} // </OutgoingPartData>
