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
    SatoshiSerializer::class,
    ByteVector32Serializer::class,
)

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.phoenix.db.serializers.v1.SatoshiSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class OutgoingDetailsTypeVersion {
    NORMAL_V0,
    SWAPOUT_V0,
    @Deprecated("channel close are now stored in their own table")
    CLOSING_V0,
    BLINDED_V0,
}

sealed class OutgoingDetailsData {

    sealed class Normal : OutgoingDetailsData() {
        @Serializable
        data class V0(val paymentRequest: String) : Normal()
    }

    sealed class SwapOut : OutgoingDetailsData() {
        @Serializable
        data class V0(val address: String, val paymentRequest: String, @Serializable val swapOutFee: Satoshi) : SwapOut()
    }

    sealed class Blinded : OutgoingDetailsData() {
        @Serializable
        data class V0(val paymentRequest: String, val payerKey: String) : Blinded()
    }

    @Deprecated("channel close are now stored in their own table")
    sealed class Closing : OutgoingDetailsData() {
        @Serializable
        data class V0(
            @Serializable val channelId: ByteVector32,
            val closingAddress: String,
            val isSentToDefaultAddress: Boolean
        ) : Closing()
    }

    companion object {
        /** Deserialize the details of an outgoing payment. Return null if the details is for a legacy channel closing payment (see [deserializeLegacyClosingDetails]). */
        fun deserialize(typeVersion: OutgoingDetailsTypeVersion, blob: ByteArray): LightningOutgoingPayment.Details? = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                OutgoingDetailsTypeVersion.NORMAL_V0 -> format.decodeFromString<Normal.V0>(json).let {
                    LightningOutgoingPayment.Details.Normal(Bolt11Invoice.read(it.paymentRequest).get())
                }
                OutgoingDetailsTypeVersion.SWAPOUT_V0 -> format.decodeFromString<SwapOut.V0>(json).let {
                    LightningOutgoingPayment.Details.SwapOut(it.address, Bolt11Invoice.read(it.paymentRequest).get(), it.swapOutFee)
                }
                OutgoingDetailsTypeVersion.CLOSING_V0 -> null
                OutgoingDetailsTypeVersion.BLINDED_V0 -> format.decodeFromString<Blinded.V0>(json).let {
                    LightningOutgoingPayment.Details.Blinded(
                        paymentRequest = Bolt12Invoice.fromString(it.paymentRequest).get(),
                        payerKey = PrivateKey.fromHex(it.payerKey),
                    )
                }
            }
        }

        /** Returns the channel closing details from a blob, for backward-compatibility purposes. */
        fun deserializeLegacyClosingDetails(blob: ByteArray): Closing.V0 = DbTypesHelper.decodeBlob(blob) { json, format ->
            format.decodeFromString(json)
        }
    }
}

fun LightningOutgoingPayment.Details.mapToDb(): Pair<OutgoingDetailsTypeVersion, ByteArray> = when (this) {
    is LightningOutgoingPayment.Details.Normal -> OutgoingDetailsTypeVersion.NORMAL_V0 to
            Json.encodeToString(OutgoingDetailsData.Normal.V0(paymentRequest.write())).toByteArray(Charsets.UTF_8)
    is LightningOutgoingPayment.Details.SwapOut -> OutgoingDetailsTypeVersion.SWAPOUT_V0 to
            Json.encodeToString(OutgoingDetailsData.SwapOut.V0(address, paymentRequest.write(), swapOutFee)).toByteArray(Charsets.UTF_8)
    is LightningOutgoingPayment.Details.Blinded -> OutgoingDetailsTypeVersion.BLINDED_V0 to
            Json.encodeToString(OutgoingDetailsData.Blinded.V0(paymentRequest.write(), payerKey.toHex())).toByteArray(Charsets.UTF_8)
}
