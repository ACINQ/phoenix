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