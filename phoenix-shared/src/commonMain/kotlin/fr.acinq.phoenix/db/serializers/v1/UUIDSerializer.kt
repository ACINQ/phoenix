package fr.acinq.phoenix.db.serializers.v1

import fr.acinq.lightning.utils.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UUIDSerializer : KSerializer<UUID> {
    @Serializable
    private data class UUIDSurrogate(val mostSignificantBits: Long, val leastSignificantBits: Long)

    override val descriptor: SerialDescriptor = UUIDSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: UUID) {
        val surrogate = UUIDSurrogate(value.mostSignificantBits, value.leastSignificantBits)
        return encoder.encodeSerializableValue(UUIDSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): UUID {
        val surrogate = decoder.decodeSerializableValue(UUIDSurrogate.serializer())
        return UUID(surrogate.mostSignificantBits, surrogate.leastSignificantBits)
    }
}