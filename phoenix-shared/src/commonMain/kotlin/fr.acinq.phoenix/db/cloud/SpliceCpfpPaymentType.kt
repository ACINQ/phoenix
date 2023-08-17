package fr.acinq.phoenix.db.cloud

import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
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
    constructor(payment: SpliceCpfpOutgoingPayment) : this(
        id = payment.id,
        miningFeeSat = payment.miningFees.sat,
        channelId = payment.channelId.toByteArray(),
        txId = payment.txId.toByteArray(),
        createdAt = payment.createdAt,
        confirmedAt = payment.confirmedAt,
        lockedAt = payment.lockedAt
    )

    @Throws(Exception::class)
    fun unwrap() = SpliceCpfpOutgoingPayment(
        id = id,
        miningFees = miningFeeSat.sat,
        channelId = channelId.toByteVector32(),
        txId = txId.toByteVector32(),
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        lockedAt = lockedAt,
    )

    companion object
}

@OptIn(ExperimentalSerializationApi::class)
fun SpliceCpfpOutgoingPayment.cborSerialize(): ByteArray {
    val wrapper = SpliceCpfpPaymentWrapper(payment = this)
    return Cbor.encodeToByteArray(wrapper)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun SpliceCpfpPaymentWrapper.cborDeserialize(
    blob: ByteArray
): SpliceCpfpPaymentWrapper {
    return cborSerializer().decodeFromByteArray(blob)
}