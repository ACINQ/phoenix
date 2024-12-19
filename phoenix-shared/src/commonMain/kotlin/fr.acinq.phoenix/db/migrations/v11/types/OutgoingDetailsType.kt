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

@file:UseSerializers(
    SatoshiSerializer::class,
    ByteVector32Serializer::class,
)

package fr.acinq.phoenix.db.migrations.v11.types

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.phoenix.db.payments.DbTypesHelper
import fr.acinq.phoenix.db.migrations.v10.json.ByteVector32Serializer
import fr.acinq.phoenix.db.migrations.v10.json.SatoshiSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
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
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingDetailsData.Normal.V0")
        data class V0(val paymentRequest: String) : Normal()
    }

    sealed class SwapOut : OutgoingDetailsData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingDetailsData.SwapOut.V0")
        data class V0(val address: String, val paymentRequest: String, @Serializable val swapOutFee: Satoshi) : SwapOut()
    }

    sealed class Blinded : OutgoingDetailsData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingDetailsData.Blinded.V0")
        data class V0(val paymentRequest: String, val payerKey: String) : Blinded()
    }

    // channel close are now stored in their own table
    sealed class Closing : OutgoingDetailsData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingDetailsData.Closing.V0")
        data class V0(
            @Serializable val channelId: ByteVector32,
            val closingAddress: String,
            val isSentToDefaultAddress: Boolean
        ) : Closing()
    }

    companion object {
        /** Deserialize the details of an outgoing payment. Return null if the details is for a legacy channel closing payment (see [deserializeLegacyClosingDetails]). */
        fun deserialize(typeVersion: OutgoingDetailsTypeVersion, blob: ByteArray): LightningOutgoingPayment.Details? =
            when (typeVersion) {
                OutgoingDetailsTypeVersion.NORMAL_V0 -> Json.decodeFromString<Normal.V0>(blob.decodeToString()).let {
                    LightningOutgoingPayment.Details.Normal(Bolt11Invoice.read(it.paymentRequest).get())
                }
                OutgoingDetailsTypeVersion.SWAPOUT_V0 -> Json.decodeFromString<SwapOut.V0>(blob.decodeToString()).let {
                    LightningOutgoingPayment.Details.SwapOut(it.address, Bolt11Invoice.read(it.paymentRequest).get(), it.swapOutFee)
                }
                OutgoingDetailsTypeVersion.CLOSING_V0 -> null
                OutgoingDetailsTypeVersion.BLINDED_V0 -> Json.decodeFromString<Blinded.V0>(blob.decodeToString()).let {
                    LightningOutgoingPayment.Details.Blinded(
                        paymentRequest = Bolt12Invoice.fromString(it.paymentRequest).get(),
                        payerKey = PrivateKey.fromHex(it.payerKey),
                    )
                }
            }

        /** Returns the channel closing details from a blob, for backward-compatibility purposes. */
        fun deserializeLegacyClosingDetails(blob: ByteArray): Closing.V0 = DbTypesHelper.decodeBlob(blob) { json, format ->
            format.decodeFromString(json)
        }
    }
}
