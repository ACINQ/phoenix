package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.byteVector32
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import fr.acinq.phoenix.db.cloud.cborSerializer
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingDetailsData
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingDetailsTypeVersion
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingStatusData
import fr.acinq.phoenix.db.migrations.v11.types.OutgoingStatusTypeVersion
import fr.acinq.phoenix.utils.migrations.LegacyChannelCloseHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.decodeFromByteArray

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
                    partsAmount = closingTxsParts.takeIf { it.isNotEmpty() }?.sumOf { it.sat }?.sat,
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

        fun unwrap(): LightningOutgoingPayment.Status {
            return OutgoingStatusData.deserialize(
                typeVersion = OutgoingStatusTypeVersion.valueOf(type),
                blob = blob,
                completedAt = ts
            )
        }

    } // </StatusWrapper>
} // </OutgoingPaymentWrapper>
