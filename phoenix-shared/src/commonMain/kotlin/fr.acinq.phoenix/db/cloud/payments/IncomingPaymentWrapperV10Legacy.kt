package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.bitcoin.byteVector32
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.phoenix.db.migrations.v10.types.mapIncomingPaymentFromV10
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class IncomingPaymentWrapperV10Legacy(
    @ByteString val preimage: ByteArray,
    val origin: OriginWrapper,
    val received: ReceivedWrapper?,
    val createdAt: Long
) {
    fun unwrap(): IncomingPayment {
        return mapIncomingPaymentFromV10(
            preimage = preimage,
            payment_hash = preimage.byteVector32().sha256().toByteArray(),
            created_at = createdAt,
            origin_type = origin.type,
            origin_blob = origin.blob,
            received_amount_msat = null,
            received_at = received?.ts,
            received_with_type = received?.type,
            received_with_blob = received?.blob,
        )
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class OriginWrapper(
        val type: String,
        @ByteString val blob: ByteArray
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class ReceivedWrapper(
        val ts: Long, // timestamp / receivedAt
        val type: String,
        @ByteString val blob: ByteArray
    )

    companion object
}
