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

@file:UseSerializers(
    ByteVector32Serializer::class,
)

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


enum class OutgoingPartStatusTypeVersion {
    SUCCEEDED_V0,
    FAILED_V0,
}

sealed class OutgoingPartStatusData {

    sealed class Succeeded : OutgoingPartStatusData() {
        @Serializable
        data class V0(@Serializable val preimage: ByteVector32) : Succeeded()
    }

    sealed class Failed : OutgoingPartStatusData() {
        @Serializable
        data class V0(val remoteFailureCode: Int?, val details: String) : Failed()
    }

    companion object {
        fun deserialize(
            typeVersion: OutgoingPartStatusTypeVersion,
            blob: ByteArray, completedAt: Long
        ): LightningOutgoingPayment.Part.Status = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                OutgoingPartStatusTypeVersion.SUCCEEDED_V0 -> format.decodeFromString<Succeeded.V0>(json).let {
                    LightningOutgoingPayment.Part.Status.Succeeded(it.preimage, completedAt)
                }
                OutgoingPartStatusTypeVersion.FAILED_V0 -> format.decodeFromString<Failed.V0>(json).let {
                    LightningOutgoingPayment.Part.Status.Failed(it.remoteFailureCode, it.details, completedAt)
                }
            }
        }
    }
}

fun LightningOutgoingPayment.Part.Status.Succeeded.mapToDb() = OutgoingPartStatusTypeVersion.SUCCEEDED_V0 to
        Json.encodeToString(OutgoingPartStatusData.Succeeded.V0(preimage)).toByteArray(Charsets.UTF_8)

fun LightningOutgoingPayment.Part.Status.Failed.mapToDb() = OutgoingPartStatusTypeVersion.FAILED_V0 to
        Json.encodeToString(OutgoingPartStatusData.Failed.V0(remoteFailureCode, details)).toByteArray(Charsets.UTF_8)
