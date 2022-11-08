package fr.acinq.phoenix.db.serializers.v1

import fr.acinq.bitcoin.Satoshi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object SatoshiKSerializer : KSerializer<Satoshi> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Satoshi", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Satoshi) {
        encoder.encodeLong(value.toLong())
    }

    override fun deserialize(decoder: Decoder): Satoshi {
        return Satoshi(decoder.decodeLong())
    }
}
