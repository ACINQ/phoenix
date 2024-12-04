package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.db.payments.types.IncomingReceivedWithData
import fr.acinq.phoenix.db.payments.types.IncomingReceivedWithTypeVersion
import fr.acinq.phoenix.db.payments.types.mapToDb
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class IncomingPaymentWrapper(
    @ByteString
    val preimage: ByteArray,
    val origin: OriginWrapper,
    val received: ReceivedWrapper?,
    val createdAt: Long
) {
    constructor(payment: IncomingPayment) : this(
        preimage = payment.preimage.toByteArray(),
        origin = OriginWrapper(payment.origin),
        received = ReceivedWrapper(payment.received),
        createdAt = payment.createdAt
    )

    fun unwrap(): IncomingPayment {
        val unwrappedReceived = received?.let {
            val originTypeVersion = IncomingOriginTypeVersion.valueOf(origin.type)
            it.unwrap(originTypeVersion)
        }
        return IncomingPayment(
            preimage = ByteVector32(preimage),
            origin = origin.unwrap(),
            received = unwrappedReceived,
            createdAt = createdAt
        )
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class OriginWrapper(
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(origin: IncomingPayment.Origin): OriginWrapper {
                val (type, blob) = origin.mapToDb()
                return OriginWrapper(
                    type = type.name,
                    blob = blob
                )
            }
        }

        fun unwrap(): IncomingPayment.Origin {
            return IncomingOriginData.deserialize(
                typeVersion = IncomingOriginTypeVersion.valueOf(type),
                blob = blob
            )
        }
    } // </OriginWrapper>

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class ReceivedWrapper(
        val ts: Long, // timestamp / receivedAt
        val type: String,
        @ByteString
        val blob: ByteArray
    ) {
        companion object {
            // constructor
            operator fun invoke(received: IncomingPayment.Received?): ReceivedWrapper? {
                return received?.receivedWith?.mapToDb()?.let { tuple ->
                    val (type, blob) = tuple
                    ReceivedWrapper(
                        ts = received.receivedAt,
                        type = type.name,
                        blob = blob
                    )
                }
            }
        }

        @Throws(Exception::class)
        fun unwrap(originTypeVersion: IncomingOriginTypeVersion): IncomingPayment.Received {
            val receivedWith = IncomingReceivedWithData.deserialize(
                typeVersion = IncomingReceivedWithTypeVersion.valueOf(type),
                blob = blob,
                amount = null, // deprecated: amount is now encoded in each part
                originTypeVersion = originTypeVersion
            )
            return IncomingPayment.Received(
                receivedWith = receivedWith,
                receivedAt = ts
            )
        }
    } // </ReceivedWrapper>

    companion object
}

@OptIn(ExperimentalSerializationApi::class)
fun IncomingPayment.cborSerialize(): ByteArray {
    val wrapper = IncomingPaymentWrapper(payment = this)
    return Cbor.encodeToByteArray(wrapper)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun IncomingPaymentWrapper.Companion.cborDeserialize(
    blob: ByteArray
): IncomingPaymentWrapper {
    return cborSerializer().decodeFromByteArray(blob)
}
