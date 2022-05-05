package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.payments.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor

@Serializable
data class OutgoingPaymentWrapper @OptIn(ExperimentalSerializationApi::class) constructor(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val msat: Long,
    @ByteString
    val recipient: ByteArray,
    val details: DetailsWrapper,
    val parts: List<OutgoingPartWrapper>,
    val status: StatusWrapper?,
    val createdAt: Long
) {
    constructor(payment: OutgoingPayment) : this(
        id = payment.id,
        msat = payment.recipientAmount.msat,
        recipient = payment.recipient.value.toByteArray(),
        details = DetailsWrapper(payment.details),
        parts = payment.parts.filterIsInstance<OutgoingPayment.LightningPart>().map { OutgoingPartWrapper(it) },
        status = StatusWrapper(payment.status),
        createdAt = payment.createdAt
    )

    fun unwrap() = OutgoingPayment(
        id = id,
        amount = MilliSatoshi(msat = msat),
        recipient = PublicKey(ByteVector(recipient)),
        details = details.unwrap()
    ).copy(
        parts = parts.map { it.unwrap() },
        status = status?.unwrap() ?: OutgoingPayment.Status.Pending,
        createdAt = createdAt
    )

    @Serializable
    data class DetailsWrapper @OptIn(ExperimentalSerializationApi::class) constructor(
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(details: OutgoingPayment.Details): DetailsWrapper {
                val (type, blob) = details.mapToDb()
                return DetailsWrapper(
                    type = type.name,
                    blob = blob
                )
            }
        }

        fun unwrap(): OutgoingPayment.Details {
            return OutgoingDetailsData.deserialize(
                typeVersion = OutgoingDetailsTypeVersion.valueOf(type),
                blob = blob
            )
        }
    } // </DetailsWrapper>

    @Serializable
    data class StatusWrapper @OptIn(ExperimentalSerializationApi::class) constructor(
        val ts: Long,
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(status: OutgoingPayment.Status): StatusWrapper? {
                return when (status) {
                    is OutgoingPayment.Status.Pending -> null
                    is OutgoingPayment.Status.Completed.Failed -> {
                        val (type, blob) = status.mapToDb()
                        StatusWrapper(
                            ts = status.completedAt,
                            type = type.name,
                            blob = blob
                        )
                    }
                    is OutgoingPayment.Status.Completed.Succeeded -> {
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

        fun unwrap(): OutgoingPayment.Status {
            return OutgoingStatusData.deserialize(
                typeVersion = OutgoingStatusTypeVersion.valueOf(type),
                blob = blob,
                completedAt = ts
            )
        }

    } // </StatusWrapper>

    companion object
} // </OutgoingPaymentWrapper>

@OptIn(ExperimentalSerializationApi::class)
fun OutgoingPayment.cborSerialize(): ByteArray {
    val wrapper = OutgoingPaymentWrapper(payment = this)
    return Cbor.encodeToByteArray(wrapper)
}

@OptIn(ExperimentalSerializationApi::class)
fun OutgoingPaymentWrapper.cborDeserialize(blob: ByteArray): OutgoingPayment? = try {
    Cbor.decodeFromByteArray<OutgoingPaymentWrapper>(blob).unwrap()
} catch (e: Throwable) {
    null
}
