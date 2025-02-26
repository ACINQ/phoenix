/*
 * Copyright 2024 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.db.migrations.v10.json

import fr.acinq.lightning.MilliSatoshi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object MilliSatoshiSerializer : KSerializer<MilliSatoshi> {
    // we are using  a surrogate for legacy reasons.
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