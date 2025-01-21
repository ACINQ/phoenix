package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment.ChannelClosingType
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChannelClosePaymentWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val amountSat: Long,
    val address: String,
    val isSentToDefaultAddress: Boolean,
    val miningFeeSat: Long,
    @ByteString val txId: ByteArray,
    val createdAt: Long,
    val confirmedAt: Long?,
    val lockedAt: Long?,
    @ByteString val channelId: ByteArray,
    val closingType: ChannelClosingType,
) {

    @Throws(Exception::class)
    fun unwrap() = ChannelCloseOutgoingPayment(
        id = id,
        recipientAmount = amountSat.sat,
        address = address,
        isSentToDefaultAddress = isSentToDefaultAddress,
        miningFee = miningFeeSat.sat,
        txId = TxId(txId),
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        lockedAt = lockedAt,
        channelId = channelId.toByteVector32(),
        closingType = closingType
    )

    companion object
}
