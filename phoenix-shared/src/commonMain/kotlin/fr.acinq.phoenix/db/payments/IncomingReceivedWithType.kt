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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.serialization.ByteVector32KSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


enum class IncomingReceivedWithTypeVersion {
    NEW_CHANNEL_V0,
    LIGHTNING_PAYMENT_V0,
}

sealed class IncomingReceivedWithData {

    sealed class NewChannel : IncomingReceivedWithData() {
        @Serializable
        data class V0(val fees: MilliSatoshi, @Serializable(with = ByteVector32KSerializer::class) val channelId: ByteVector32?) : NewChannel()
    }

    sealed class LightningPayment : IncomingReceivedWithData() {
        @Serializable
        @SerialName("LIGHTNING_PAYMENT_V0")
        object V0 : LightningPayment()
    }

    companion object {
        fun deserialize(typeVersion: IncomingReceivedWithTypeVersion, blob: ByteArray): IncomingPayment.ReceivedWith = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0 -> IncomingPayment.ReceivedWith.LightningPayment
                IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0 -> format.decodeFromString<NewChannel.V0>(json).let { IncomingPayment.ReceivedWith.NewChannel(it.fees, it.channelId) }
            }
        }
    }
}

fun IncomingPayment.ReceivedWith.mapToDb(): Pair<IncomingReceivedWithTypeVersion, ByteArray> = when (this) {
    is IncomingPayment.ReceivedWith.LightningPayment -> IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0 to
            Json.encodeToString(IncomingReceivedWithData.LightningPayment.V0).toByteArray(Charsets.UTF_8)
    is IncomingPayment.ReceivedWith.NewChannel -> IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0 to
            Json.encodeToString(IncomingReceivedWithData.NewChannel.V0(fees, channelId)).toByteArray(Charsets.UTF_8)
}
