package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector32
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.utils.migrations.LegacyChannelCloseHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

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
    // these parts are now obsolete, we now use a dedicated object for channels closing
    val closingTxsParts: List<LightningOutgoingClosingTxPartWrapper> = emptyList(),
    val status: StatusWrapper?,
    val createdAt: Long
) {
    constructor(payment: LightningOutgoingPayment) : this(
        id = payment.id,
        msat = payment.recipientAmount.msat,
        recipient = payment.recipient.value.toByteArray(),
        details = DetailsWrapper(payment.details),
        parts = payment.parts.map { LightningOutgoingPartWrapper(it) },
        status = StatusWrapper(payment.status),
        createdAt = payment.createdAt
    )

    /**
     * Unwraps a cbor-serialized outgoing payment. Should return a [LightningOutgoingPayment], but on may also return a
     * [ChannelCloseOutgoingPayment] in case the data are legacy and actually contain data for a channel closing.
     */
    @Throws(Exception::class)
    fun unwrap(): OutgoingPayment? {
        val details = details.unwrap()
        return if (details != null) {
            val status = status?.unwrap() ?: LightningOutgoingPayment.Status.Pending
            val parts = parts.map { it.unwrap() }
            LightningOutgoingPayment(
                id = id,
                recipientAmount = msat.msat,
                recipient = PublicKey.parse(recipient),
                status = status,
                parts = parts,
                details = details,
                createdAt = createdAt
            )
        } else {
            try {
                LegacyChannelCloseHelper.convertLegacyToChannelClose(
                    id = id,
                    recipientAmount = msat.msat,
                    partsAmount = closingTxsParts.sumOf { it.sat }.sat,
                    partsTxId = closingTxsParts.firstOrNull()?.txId?.byteVector32(),
                    detailsBlob = this.details.blob,
                    statusBlob = this.status?.blob,
                    partsClosingTypeBlob = closingTxsParts.firstOrNull()?.info?.blob,
                    createdAt = createdAt,
                    confirmedAt = this.status?.ts ?: createdAt,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

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

        fun unwrap(): LightningOutgoingPayment.Details? {
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
