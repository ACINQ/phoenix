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
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.serialization.ByteVector32KSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


enum class OutgoingDetailsTypeVersion {
    NORMAL_V0,
    KEYSEND_V0,
    SWAPOUT_V0,
    CLOSING_V0,
}

sealed class OutgoingDetailsData {

    sealed class Normal : OutgoingDetailsData() {
        @Serializable
        data class V0(val paymentRequest: String) : Normal()
    }

    sealed class KeySend : OutgoingDetailsData() {
        @Serializable
        data class V0(@Serializable(with = ByteVector32KSerializer::class) val preimage: ByteVector32) : KeySend()
    }

    sealed class SwapOut : OutgoingDetailsData() {
        @Serializable
        data class V0(val address: String, @Serializable(with = ByteVector32KSerializer::class) val paymentHash: ByteVector32) : SwapOut()
    }

    sealed class Closing : OutgoingDetailsData() {
        @Serializable
        data class V0(
            @Serializable(with = ByteVector32KSerializer::class) val channelId: ByteVector32,
            val closingAddress: String,
            val isSentToDefaultAddress: Boolean
        ) : Closing()
    }

    companion object {
        fun deserialize(typeVersion: OutgoingDetailsTypeVersion, blob: ByteArray): OutgoingPayment.Details = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                OutgoingDetailsTypeVersion.NORMAL_V0 -> format.decodeFromString<Normal.V0>(json).let { OutgoingPayment.Details.Normal(PaymentRequest.read(it.paymentRequest)) }
                OutgoingDetailsTypeVersion.KEYSEND_V0 -> format.decodeFromString<KeySend.V0>(json).let { OutgoingPayment.Details.KeySend(it.preimage) }
                OutgoingDetailsTypeVersion.SWAPOUT_V0 -> format.decodeFromString<SwapOut.V0>(json).let { OutgoingPayment.Details.SwapOut(it.address, it.paymentHash) }
                OutgoingDetailsTypeVersion.CLOSING_V0 -> format.decodeFromString<Closing.V0>(json).let { OutgoingPayment.Details.ChannelClosing(it.channelId, it.closingAddress, it.isSentToDefaultAddress) }
            }
        }
    }
}

fun OutgoingPayment.Details.mapToDb(): Pair<OutgoingDetailsTypeVersion, ByteArray> = when (this) {
    is OutgoingPayment.Details.Normal -> OutgoingDetailsTypeVersion.NORMAL_V0 to
            Json.encodeToString(OutgoingDetailsData.Normal.V0(paymentRequest.write())).toByteArray(Charsets.UTF_8)
    is OutgoingPayment.Details.KeySend -> OutgoingDetailsTypeVersion.KEYSEND_V0 to
            Json.encodeToString(OutgoingDetailsData.KeySend.V0(preimage)).toByteArray(Charsets.UTF_8)
    is OutgoingPayment.Details.SwapOut -> OutgoingDetailsTypeVersion.SWAPOUT_V0 to
            Json.encodeToString(OutgoingDetailsData.SwapOut.V0(address, paymentHash)).toByteArray(Charsets.UTF_8)
    is OutgoingPayment.Details.ChannelClosing -> OutgoingDetailsTypeVersion.CLOSING_V0 to
            Json.encodeToString(OutgoingDetailsData.Closing.V0(channelId, closingAddress, isSentToDefaultAddress)).toByteArray(Charsets.UTF_8)
}
