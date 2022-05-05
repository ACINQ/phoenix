/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.db.payments

import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.OutgoingPayment
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


enum class OutgoingPartClosingInfoTypeVersion {
    // basic type, containing only a [ChannelClosingType] field
    CLOSING_INFO_V0,
}

sealed class OutgoingPartClosingInfoData {

    @Serializable
    data class V0(val closingType: ChannelClosingType)

    companion object {
        fun deserialize(typeVersion: OutgoingPartClosingInfoTypeVersion, blob: ByteArray): ChannelClosingType = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                OutgoingPartClosingInfoTypeVersion.CLOSING_INFO_V0 -> format.decodeFromString<OutgoingPartClosingInfoData.V0>(json).closingType
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun OutgoingPayment.ClosingTxPart.mapClosingTypeToDb() = OutgoingPartClosingInfoTypeVersion.CLOSING_INFO_V0 to
        Json.encodeToString(OutgoingPartClosingInfoData.V0(this.closingType)).toByteArray(Charsets.UTF_8)
