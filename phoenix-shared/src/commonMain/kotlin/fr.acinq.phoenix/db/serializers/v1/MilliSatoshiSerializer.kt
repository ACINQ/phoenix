package fr.acinq.phoenix.db.serializers.v1

import fr.acinq.lightning.MilliSatoshi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object MilliSatoshiSerializer : KSerializer<MilliSatoshi> {
    @Serializable
    private data class MilliSatoshiSurrogate(val msat: Long)

    override val descriptor: SerialDescriptor = MilliSatoshiSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MilliSatoshi) {
        val surrogate = MilliSatoshiSurrogate(msat = value.msat)
        return encoder.encodeSerializableValue(MilliSatoshiSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): MilliSatoshi {
        val surrogate = decoder.decodeSerializableValue(MilliSatoshiSurrogate.serializer())
        return MilliSatoshi(msat = surrogate.msat)
    }
}