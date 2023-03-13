package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.payments.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LightningOutgoingPaymentWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val msat: Long,
    @ByteString
    val recipient: ByteArray,
    val details: DetailsWrapper,
    val parts: List<LightningOutgoingPartWrapper>,
    val closingTxsParts: List<LightningOutgoingClosingTxPartWrapper> = emptyList(),
    val status: StatusWrapper?,
    val createdAt: Long
) {
    constructor(payment: LightningOutgoingPayment) : this(
        id = payment.id,
        msat = payment.recipientAmount.msat,
        recipient = payment.recipient.value.toByteArray(),
        details = DetailsWrapper(payment.details),
        parts = payment.parts.filterIsInstance<LightningOutgoingPayment.LightningPart>().map { LightningOutgoingPartWrapper(it) },
        closingTxsParts = payment.parts.filterIsInstance<LightningOutgoingPayment.ClosingTxPart>().map { LightningOutgoingClosingTxPartWrapper(it) },
        status = StatusWrapper(payment.status),
        createdAt = payment.createdAt
    )

    @Throws(Exception::class)
    fun unwrap() = LightningOutgoingPayment(
        id = id,
        amount = MilliSatoshi(msat = msat),
        recipient = PublicKey.parse(recipient),
        details = details.unwrap()
    ).copy(
        parts = parts.map { it.unwrap() } + closingTxsParts.map { it.unwrap() } + (status?.getClosingPartsFromV0OnchainStatus() ?: emptyList()),
        status = status?.unwrap() ?: LightningOutgoingPayment.Status.Pending,
        createdAt = createdAt
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class DetailsWrapper(
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(details: LightningOutgoingPayment.Details): DetailsWrapper {
                val (type, blob) = details.mapToDb()
                return DetailsWrapper(
                    type = type.name,
                    blob = blob
                )
            }
        }

        fun unwrap(): LightningOutgoingPayment.Details {
            return OutgoingDetailsData.deserialize(
                typeVersion = OutgoingDetailsTypeVersion.valueOf(type),
                blob = blob
            )
        }
    } // </DetailsWrapper>

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class StatusWrapper(
        val ts: Long,
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(status: LightningOutgoingPayment.Status): StatusWrapper? {
                return when (status) {
                    is LightningOutgoingPayment.Status.Pending -> null
                    is LightningOutgoingPayment.Status.Completed.Failed -> {
                        val (type, blob) = status.mapToDb()
                        StatusWrapper(
                            ts = status.completedAt,
                            type = type.name,
                            blob = blob
                        )
                    }
                    is LightningOutgoingPayment.Status.Completed.Succeeded -> {
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

        fun unwrap(): LightningOutgoingPayment.Status {
            return OutgoingStatusData.deserialize(
                typeVersion = OutgoingStatusTypeVersion.valueOf(type),
                blob = blob,
                completedAt = ts
            )
        }

        /** The status blob may contain closing transaction data, when type is [OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0]. */
        fun getClosingPartsFromV0OnchainStatus(): List<LightningOutgoingPayment.ClosingTxPart> {
            @Suppress("DEPRECATION")
            return if (OutgoingStatusTypeVersion.valueOf(type) == OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0) {
                OutgoingStatusData.getClosingPartsFromV0Status(blob, ts)
            } else {
                emptyList()
            }
        }

    } // </StatusWrapper>

    companion object
} // </OutgoingPaymentWrapper>

@OptIn(ExperimentalSerializationApi::class)
fun LightningOutgoingPayment.cborSerialize(): ByteArray {
    val wrapper = LightningOutgoingPaymentWrapper(payment = this)
    return Cbor.encodeToByteArray(wrapper)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun LightningOutgoingPaymentWrapper.cborDeserialize(
    blob: ByteArray
): LightningOutgoingPaymentWrapper {
    return cborSerializer().decodeFromByteArray(blob)
}
