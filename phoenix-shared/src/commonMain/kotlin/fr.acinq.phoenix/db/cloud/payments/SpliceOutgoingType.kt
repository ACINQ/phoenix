package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SpliceOutgoingPaymentWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val amountSat: Long,
    val address: String,
    val miningFeeSat: Long,
    @ByteString val txId: ByteArray,
    @ByteString val channelId: ByteArray,
    val createdAt: Long,
    val confirmedAt: Long?,
    val lockedAt: Long?,
) {
    @Throws(Exception::class)
    fun unwrap() = SpliceOutgoingPayment(
        id = id,
        recipientAmount = amountSat.sat,
        address = address,
        miningFee = miningFeeSat.sat,
        channelId = channelId.toByteVector32(),
        txId = TxId(txId),
        liquidityPurchase = null,
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        lockedAt = lockedAt,
    )
}
