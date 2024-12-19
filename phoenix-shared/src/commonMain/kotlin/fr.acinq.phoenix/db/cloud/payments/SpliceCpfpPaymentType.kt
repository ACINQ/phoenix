package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SpliceCpfpPaymentWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val miningFeeSat: Long,
    @ByteString
    val channelId: ByteArray,
    @ByteString
    val txId: ByteArray,
    val createdAt: Long,
    val confirmedAt: Long?,
    val lockedAt: Long?
) {
    @Throws(Exception::class)
    fun unwrap() = SpliceCpfpOutgoingPayment(
        id = id,
        miningFees = miningFeeSat.sat,
        channelId = channelId.toByteVector32(),
        txId = TxId(txId),
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        lockedAt = lockedAt,
    )
}
