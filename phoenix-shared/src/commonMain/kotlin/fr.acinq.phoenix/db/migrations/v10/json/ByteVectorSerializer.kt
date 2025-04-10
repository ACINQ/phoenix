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

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.ByteVector64


object ByteVector32Serializer : AbstractStringSerializer<ByteVector32>(
    name = "ByteVector32",
    toString = ByteVector32::toHex,
    fromString = ::ByteVector32
)

object ByteVector64Serializer : AbstractStringSerializer<ByteVector64>(
    name = "ByteVector64",
    toString = ByteVector64::toHex,
    fromString = ::ByteVector64
)

object ByteVectorSerializer : AbstractStringSerializer<ByteVector>(
    name = "ByteVector",
    toString = ByteVector::toHex,
    fromString = ::ByteVector
)
