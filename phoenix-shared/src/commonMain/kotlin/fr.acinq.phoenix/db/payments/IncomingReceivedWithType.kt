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
    MilliSatoshiSerializer::class,
    ByteVector32Serializer::class,
    UUIDSerializer::class,
)

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.phoenix.db.serializers.v1.MilliSatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.UUIDSerializer
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.db.serializers.v1.SatoshiSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.SetSerializer


enum class IncomingReceivedWithTypeVersion {
    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    NEW_CHANNEL_V0,
    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    LIGHTNING_PAYMENT_V0,
    // multiparts payments are when receivedWith is a set of parts (new channel and htlcs)
    @Deprecated("MULTIPARTS_V0 had an issue where the incoming amount of pay-to-open (new channels over LN) contained the fee, " +
            "instead of only the pushed amount. V1 fixes this by convention, when deserializing the object. No new [IncomingReceivedWithData.Part.xxx.V1] is needed.")
    MULTIPARTS_V0,
    MULTIPARTS_V1,
}

sealed class IncomingReceivedWithData {

    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    sealed class NewChannel : IncomingReceivedWithData() {
        @Serializable
        @Suppress("DEPRECATION")
        data class V0(
            @Serializable
            val fees: MilliSatoshi,
            @Serializable
            val channelId: ByteVector32?
        ) : NewChannel()
    }

    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    sealed class LightningPayment : IncomingReceivedWithData() {
        @Serializable
        @SerialName("LIGHTNING_PAYMENT_V0")
        @Suppress("DEPRECATION")
        object V0 : LightningPayment()
    }

    @Serializable
    sealed class Part : IncomingReceivedWithData() {
        sealed class Htlc : Part() {
            @Serializable
            data class V0(
                @Serializable
                val amount: MilliSatoshi,
                @Serializable
                val channelId: ByteVector32,
                val htlcId: Long
            ) : Htlc()
        }
        sealed class NewChannel : Part() {
            @Deprecated("Legacy type. Use V1 instead for new parts, with the new `id` field.")
            @Serializable
            data class V0(
                @Serializable
                val amount: MilliSatoshi,
                @Serializable
                val fees: MilliSatoshi,
                @Serializable
                val channelId: ByteVector32?
            ) : NewChannel()

            /** V1 contains a new `id` field that ensure that each [NewChannel] is unique. Old V0 data will use a random UUID to respect the [IncomingPayment.ReceivedWith.NewChannel] interface. */
            @Serializable
            data class V1(
                @Serializable
                val id: UUID,
                @Serializable
                val amount: MilliSatoshi,
                @Serializable
                @SerialName("fees")
                val serviceFee: MilliSatoshi,
                @Serializable
                val fundingFee: Satoshi = 0.sat,
                @Serializable
                val channelId: ByteVector32?,
                // Added for dual-funding flow; Defaults to true for older serialized instances.
                val confirmed: Boolean = true
            ) : NewChannel()
        }
    }

    companion object {
        /**
         * Deserializes a received-with blob from the database using the given typeversion.
         *
         * @param amount This parameter is only used if the typeversion is [IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0] or
         *               [IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0]. In that case we make a list of parts made of only one
         *               part and containing this amount.
         * @param originTypeVersion This parameter is only used if the typeversion is [IncomingReceivedWithTypeVersion.MULTIPARTS_V0],
         *               in which case if a part is [Part.NewChannel] then the fee must be subtracted from the amount.
         */
        fun deserialize(
            typeVersion: IncomingReceivedWithTypeVersion,
            blob: ByteArray,
            amount: MilliSatoshi?,
            originTypeVersion: IncomingOriginTypeVersion
        ) = DbTypesHelper.decodeBlob(blob) { json, format ->
            @Suppress("DEPRECATION")
            when (typeVersion) {
                IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0 -> setOf(
                    IncomingPayment.ReceivedWith.LightningPayment(amount ?: 0.msat, ByteVector32.Zeroes, 0L)
                )
                IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0 -> setOf(format.decodeFromString<NewChannel.V0>(json).let {
                    IncomingPayment.ReceivedWith.NewChannel(UUID.randomUUID(), amount ?: 0.msat, it.fees, 0.sat, it.channelId, confirmed = true)
                })
                IncomingReceivedWithTypeVersion.MULTIPARTS_V0 -> DbTypesHelper.polymorphicFormat.decodeFromString(SetSerializer(PolymorphicSerializer(Part::class)), json).map {
                    when (it) {
                        is Part.Htlc.V0 -> IncomingPayment.ReceivedWith.LightningPayment(it.amount, it.channelId, it.htlcId)
                        is Part.NewChannel.V0 -> if (originTypeVersion == IncomingOriginTypeVersion.SWAPIN_V0) {
                            IncomingPayment.ReceivedWith.NewChannel(UUID.randomUUID(), it.amount, it.fees, 0.sat, it.channelId, confirmed = true)
                        } else {
                            IncomingPayment.ReceivedWith.NewChannel(UUID.randomUUID(),it.amount - it.fees, it.fees, 0.sat, it.channelId, confirmed = true)
                        }
                        is Part.NewChannel.V1 -> IncomingPayment.ReceivedWith.NewChannel(it.id, it.amount, it.serviceFee, it.fundingFee, it.channelId, it.confirmed)
                    }
                }.toSet()
                IncomingReceivedWithTypeVersion.MULTIPARTS_V1 -> DbTypesHelper.polymorphicFormat.decodeFromString(SetSerializer(PolymorphicSerializer(Part::class)), json).map {
                    when (it) {
                        is Part.Htlc.V0 -> IncomingPayment.ReceivedWith.LightningPayment(it.amount, it.channelId, it.htlcId)
                        is Part.NewChannel.V0 -> IncomingPayment.ReceivedWith.NewChannel(UUID.randomUUID(), it.amount, it.fees, 0.sat, it.channelId, confirmed = true)
                        is Part.NewChannel.V1 -> IncomingPayment.ReceivedWith.NewChannel(it.id, it.amount, it.serviceFee, it.fundingFee, it.channelId, it.confirmed)
                    }
                }.toSet()
            }
        }
    }
}

/** Only serialize received_with into the [IncomingReceivedWithTypeVersion.MULTIPARTS_V1] type. */
fun Set<IncomingPayment.ReceivedWith>.mapToDb(): Pair<IncomingReceivedWithTypeVersion, ByteArray>? = map {
    when (it) {
        is IncomingPayment.ReceivedWith.LightningPayment -> IncomingReceivedWithData.Part.Htlc.V0(it.amount, it.channelId, it.htlcId)
        is IncomingPayment.ReceivedWith.NewChannel -> IncomingReceivedWithData.Part.NewChannel.V1(it.id, it.amount, it.serviceFee, it.fundingFee, it.channelId, it.confirmed)
    }
}.takeIf { it.isNotEmpty() }?.toSet()?.let {
    IncomingReceivedWithTypeVersion.MULTIPARTS_V1 to DbTypesHelper.polymorphicFormat.encodeToString(
        SetSerializer(PolymorphicSerializer(IncomingReceivedWithData.Part::class)), it).toByteArray(Charsets.UTF_8)
}
