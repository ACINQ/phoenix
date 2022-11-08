package fr.acinq.phoenix.db.serializers.v1

import fr.acinq.bitcoin.ByteVector32
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

abstract class AbstractStringKSerializer<T>(
    name: String,
    private val toString: (T) -> String,
    private val fromString: (String) -> T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(toString(value))
    }

    override fun deserialize(decoder: Decoder): T {
        return fromString(decoder.decodeString())
    }
}

object ByteVector32KSerializer : AbstractStringKSerializer<ByteVector32>(
    name = "ByteVector32",
    toString = ByteVector32::toHex,
    fromString = ::ByteVector32
)