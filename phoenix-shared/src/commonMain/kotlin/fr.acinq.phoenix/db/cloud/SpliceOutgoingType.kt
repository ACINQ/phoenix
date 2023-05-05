package fr.acinq.phoenix.db.cloud

import fr.acinq.lightning.db.SpliceOutgoingPayment
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
data class SpliceOutgoingPaymentWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val amountSat: Long,
    val address: String,
    val miningFeeSat: Long,
    @ByteString val txId: ByteArray,
    @ByteString val channelId: ByteArray,
    val createdAt: Long,
    val confirmedAt: Long?
) {
    constructor(payment: SpliceOutgoingPayment) : this(
        id = payment.id,
        amountSat = payment.amountSatoshi.sat,
        address = payment.address,
        miningFeeSat = payment.miningFees.sat,
        txId = payment.txId.toByteArray(),
        channelId = payment.channelId.toByteArray(),
        createdAt = payment.createdAt,
        confirmedAt = payment.confirmedAt
    )

    @Throws(Exception::class)
    fun unwrap() = SpliceOutgoingPayment(
        id = id,
        amountSatoshi = amountSat.sat,
        address = address,
        miningFees = miningFeeSat.sat,
        txId = txId.toByteVector32(),
        channelId = channelId.toByteVector32(),
        createdAt = createdAt,
        confirmedAt = confirmedAt
    )

    companion object
}

@OptIn(ExperimentalSerializationApi::class)
fun SpliceOutgoingPayment.cborSerialize(): ByteArray {
    val wrapper = SpliceOutgoingPaymentWrapper(payment = this)
    return Cbor.encodeToByteArray(wrapper)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun SpliceOutgoingPaymentWrapper.cborDeserialize(
    blob: ByteArray
): SpliceOutgoingPaymentWrapper {
    return cborSerializer().decodeFromByteArray(blob)
}
