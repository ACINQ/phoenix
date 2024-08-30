package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

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
    constructor(payment: ChannelCloseOutgoingPayment) : this(
        id = payment.id,
        amountSat = payment.recipientAmount.sat,
        address = payment.address,
        isSentToDefaultAddress = payment.isSentToDefaultAddress,
        miningFeeSat = payment.miningFees.sat,
        txId = payment.txId.value.toByteArray(),
        createdAt = payment.createdAt,
        confirmedAt = payment.confirmedAt,
        lockedAt = payment.lockedAt,
        channelId = payment.channelId.toByteArray(),
        closingType = payment.closingType,
    )

    @Throws(Exception::class)
    fun unwrap() = ChannelCloseOutgoingPayment(
        id = id,
        recipientAmount = amountSat.sat,
        address = address,
        isSentToDefaultAddress = isSentToDefaultAddress,
        miningFees = miningFeeSat.sat,
        txId = TxId(txId),
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        lockedAt = lockedAt,
        channelId = channelId.toByteVector32(),
        closingType = closingType
    )

    companion object
}

@OptIn(ExperimentalSerializationApi::class)
fun ChannelCloseOutgoingPayment.cborSerialize(): ByteArray {
    val wrapper = ChannelClosePaymentWrapper(payment = this)
    return Cbor.encodeToByteArray(wrapper)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun ChannelClosePaymentWrapper.cborDeserialize(
    blob: ByteArray
): ChannelClosePaymentWrapper {
    return cborSerializer().decodeFromByteArray(blob)
}
