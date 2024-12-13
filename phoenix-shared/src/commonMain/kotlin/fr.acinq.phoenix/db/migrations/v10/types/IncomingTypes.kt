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
    MilliSatoshiSerializer::class,
    ByteVectorSerializer::class,
    ByteVector32Serializer::class,
    UUIDSerializer::class,
    OutpointSerializer::class,
)

package fr.acinq.phoenix.db.migrations.v10.types

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.SpliceInIncomingPayment
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.db.payments.DbTypesHelper
import fr.acinq.phoenix.db.payments.liquidityads.FundingFeeData
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.phoenix.db.serializers.v1.ByteVectorSerializer
import fr.acinq.phoenix.db.serializers.v1.MilliSatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.OutpointSerializer
import fr.acinq.phoenix.db.serializers.v1.SatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.UUIDSerializer
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.String
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json


private enum class IncomingReceivedWithTypeVersion {
    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    NEW_CHANNEL_V0,

    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    LIGHTNING_PAYMENT_V0,

    // multiparts payments are when receivedWith is a set of parts (new channel and htlcs)
    @Deprecated(
        "MULTIPARTS_V0 had an issue where the incoming amount of pay-to-open (new channels over LN) contained the fee, " +
                "instead of only the pushed amount. V1 fixes this by convention, when deserializing the object. No new [IncomingReceivedWithData.Part.xxx.V1] is needed."
    )
    MULTIPARTS_V0,
    MULTIPARTS_V1,
}

private sealed class IncomingReceivedWithData {

    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    sealed class NewChannel : IncomingReceivedWithData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.IncomingReceivedWithData.NewChannel.V0")
        @Suppress("DEPRECATION")
        data class V0(
            @Serializable val fees: MilliSatoshi,
            @Serializable val channelId: ByteVector32?
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
            @Deprecated("Replaced by [Htlc.V1], which supports the liquidity ads funding fee")
            @Serializable
            @SerialName("fr.acinq.phoenix.db.payments.IncomingReceivedWithData.Part.Htlc.V0")
            data class V0(
                @Serializable val amount: MilliSatoshi,
                @Serializable val channelId: ByteVector32,
                val htlcId: Long
            ) : Htlc()

            @Serializable
            @SerialName("fr.acinq.phoenix.db.payments.IncomingReceivedWithData.Part.Htlc.V1")
            data class V1(
                val amountReceived: MilliSatoshi,
                val channelId: ByteVector32,
                val htlcId: Long,
                val fundingFee: FundingFeeData?,
            ) : Htlc()
        }

        sealed class NewChannel : Part() {
            @Deprecated("Legacy type. Use V1 instead for new parts, with the new `id` field.")
            @Serializable
            @SerialName("fr.acinq.phoenix.db.payments.IncomingReceivedWithData.Part.NewChannel.V0")
            data class V0(
                @Serializable val amount: MilliSatoshi,
                @Serializable val fees: MilliSatoshi,
                @Serializable val channelId: ByteVector32?
            ) : NewChannel()

            /** V1 contains a new `id` field that ensure that each [NewChannel] is unique. Old V0 data will use a random UUID to respect the [IncomingPayment.ReceivedWith.NewChannel] interface. */
            @Serializable
            @SerialName("fr.acinq.phoenix.db.payments.IncomingReceivedWithData.Part.NewChannel.V1")
            data class V1(
                @Serializable val id: UUID,
                @Serializable val amount: MilliSatoshi,
                @Serializable val fees: MilliSatoshi,
                @Serializable val channelId: ByteVector32?
            ) : NewChannel()

            /** V2 supports dual funding. New fields: service/miningFees, channel id, funding tx id, and the confirmation/lock timestamps. Id is removed. */
            @Serializable
            @SerialName("fr.acinq.phoenix.db.payments.IncomingReceivedWithData.Part.NewChannel.V2")
            data class V2(
                @Serializable val amount: MilliSatoshi,
                @Serializable val serviceFee: MilliSatoshi,
                @Serializable val miningFee: Satoshi,
                @Serializable val channelId: ByteVector32,
                @Serializable val txId: ByteVector32,
                @Serializable val confirmedAt: Long?,
                @Serializable val lockedAt: Long?,
            ) : NewChannel()
        }

        sealed class SpliceIn : Part() {
            @Serializable
            @SerialName("fr.acinq.phoenix.db.payments.IncomingReceivedWithData.Part.SpliceIn.V0")
            data class V0(
                @Serializable val amount: MilliSatoshi,
                @Serializable val serviceFee: MilliSatoshi,
                @Serializable val miningFee: Satoshi,
                @Serializable val channelId: ByteVector32,
                @Serializable val txId: ByteVector32,
                @Serializable val confirmedAt: Long?,
                @Serializable val lockedAt: Long?,
            ) : SpliceIn()
        }

        sealed class FeeCredit : Part() {
            @Serializable
            @SerialName("fr.acinq.phoenix.db.payments.IncomingReceivedWithData.Part.FeeCredit.V0")
            data class V0(
                val amount: MilliSatoshi
            ) : FeeCredit()
        }
    }

    companion object {
        /** Deserializes a received-with blob from the database using the given typeversion. */
        fun deserialize(
            typeVersion: IncomingReceivedWithTypeVersion,
            blob: ByteArray,
        ): List<IncomingReceivedWithData> = @Suppress("DEPRECATION") when (typeVersion) {
            IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0 -> listOf(LightningPayment.V0)
            IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0 -> listOf(Json.decodeFromString(NewChannel.V0.serializer(), String(bytes = blob, charset = Charsets.UTF_8)))
            IncomingReceivedWithTypeVersion.MULTIPARTS_V0 -> Json.decodeFromString(SetSerializer(Part.serializer()), String(bytes = blob, charset = Charsets.UTF_8)).toList()
            IncomingReceivedWithTypeVersion.MULTIPARTS_V1 -> Json.decodeFromString(SetSerializer(Part.serializer()), String(bytes = blob, charset = Charsets.UTF_8)).toList()
        }
    }
}

private enum class IncomingOriginTypeVersion {
    INVOICE_V0,
    SWAPIN_V0,
    ONCHAIN_V0,
    OFFER_V0,
}

private sealed class IncomingOriginData {

    sealed class Invoice : IncomingOriginData() {
        @Serializable
        data class V0(val paymentRequest: String) : Invoice()
    }

    /** used for the old trusted swap-in mechanism */
    sealed class SwapIn : IncomingOriginData() {
        @Serializable
        data class V0(val address: String?) : SwapIn()
    }

    /** Used for trustless swap-ins */
    sealed class OnChain : IncomingOriginData() {
        @Serializable
        data class V0(val txId: ByteVector32, val outpoints: List<OutPoint>) : SwapIn()
    }

    sealed class Offer : IncomingOriginData() {
        @Serializable
        data class V0(val encodedMetadata: ByteVector) : Offer()
    }

    companion object {
        fun deserialize(typeVersion: IncomingOriginTypeVersion, blob: ByteArray): IncomingOriginData = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                IncomingOriginTypeVersion.INVOICE_V0 -> format.decodeFromString<Invoice.V0>(json)
                IncomingOriginTypeVersion.SWAPIN_V0 -> format.decodeFromString<SwapIn.V0>(json)
                IncomingOriginTypeVersion.ONCHAIN_V0 -> format.decodeFromString<OnChain.V0>(json)
                IncomingOriginTypeVersion.OFFER_V0 -> format.decodeFromString<Offer.V0>(json)
            }
        }
    }
}

private fun mapLightningIncomingPaymentPart(part: IncomingReceivedWithData, receivedAt: Long, receivedAmountFallback: MilliSatoshi?): LightningIncomingPayment.Part = when (part) {
    is IncomingReceivedWithData.LightningPayment.V0 -> LightningIncomingPayment.Part.Htlc(
        amountReceived = receivedAmountFallback ?: 0.msat,
        channelId = ByteVector32.Zeroes,
        htlcId = 0L,
        fundingFee = null,
        receivedAt = receivedAt
    )
    is IncomingReceivedWithData.Part.Htlc.V0 -> LightningIncomingPayment.Part.Htlc(
        amountReceived = part.amount,
        channelId = part.channelId,
        htlcId = part.htlcId,
        fundingFee = null,
        receivedAt = receivedAt
    )
    is IncomingReceivedWithData.Part.Htlc.V1 -> LightningIncomingPayment.Part.Htlc(
        amountReceived = part.amountReceived,
        channelId = part.channelId,
        htlcId = part.htlcId,
        fundingFee = when (part.fundingFee) {
            is FundingFeeData.V0 -> LiquidityAds.FundingFee(part.fundingFee.amount, part.fundingFee.fundingTxId)
            null -> null
        },
        receivedAt = receivedAt
    )
    is IncomingReceivedWithData.Part.FeeCredit.V0 -> LightningIncomingPayment.Part.FeeCredit(
        amountReceived = part.amount,
        receivedAt = receivedAt
    )
    else -> error("unexpected part=$part")
}


@Suppress("DEPRECATION")
fun mapIncomingPaymentFromV10(
    @Suppress("UNUSED_PARAMETER") payment_hash: ByteArray,
    preimage: ByteArray,
    created_at: Long,
    origin_type: String,
    origin_blob: ByteArray,
    received_amount_msat: Long?,
    received_at: Long?,
    received_with_type: String?,
    received_with_blob: ByteArray?,
): IncomingPayment {
    val origin = IncomingOriginData.deserialize(IncomingOriginTypeVersion.valueOf(origin_type), origin_blob)
    val parts = when {
        received_with_type != null && received_with_blob != null -> IncomingReceivedWithData.deserialize(IncomingReceivedWithTypeVersion.valueOf(received_with_type), received_with_blob)
        else -> emptyList()
    }
    return when {
        received_at == null && origin is IncomingOriginData.Invoice.V0 ->
            Bolt11IncomingPayment(
                preimage = ByteVector32(preimage),
                paymentRequest = Bolt11Invoice.read(origin.paymentRequest).get(),
                parts = emptyList(),
                createdAt = created_at
            )
        received_at == null && origin is IncomingOriginData.Offer.V0 ->
            Bolt12IncomingPayment(
                preimage = ByteVector32(preimage),
                metadata = OfferPaymentMetadata.decode(origin.encodedMetadata),
                parts = emptyList(),
                createdAt = created_at
            )
        received_at != null && origin is IncomingOriginData.Invoice.V0 && parts.all { it is IncomingReceivedWithData.LightningPayment } ->
            Bolt11IncomingPayment(
                preimage = ByteVector32(preimage),
                paymentRequest = Bolt11Invoice.read(origin.paymentRequest).get(),
                parts = parts.map { mapLightningIncomingPaymentPart(it, received_at, received_amount_msat?.msat) }
            )
        received_at != null && origin is IncomingOriginData.Invoice.V0 && parts.all { it is IncomingReceivedWithData.Part.Htlc || it is IncomingReceivedWithData.Part.FeeCredit } ->
            Bolt11IncomingPayment(
                preimage = ByteVector32(preimage),
                paymentRequest = Bolt11Invoice.read(origin.paymentRequest).get(),
                parts = parts.map { mapLightningIncomingPaymentPart(it, received_at, received_amount_msat?.msat) }
            )
        received_at != null && origin is IncomingOriginData.Offer.V0 && parts.all { it is IncomingReceivedWithData.Part.Htlc || it is IncomingReceivedWithData.Part.FeeCredit } ->
            Bolt12IncomingPayment(
                preimage = ByteVector32(preimage),
                metadata = OfferPaymentMetadata.decode(origin.encodedMetadata),
                parts = parts.map { mapLightningIncomingPaymentPart(it, received_at, received_amount_msat?.msat) }
            )
        received_at != null && (origin is IncomingOriginData.Invoice || origin is IncomingOriginData.Offer) && parts.any { it is IncomingReceivedWithData.Part.SpliceIn || it is IncomingReceivedWithData.Part.NewChannel || it is IncomingReceivedWithData.NewChannel } ->
            LegacyPayToOpenIncomingPayment(
                paymentPreimage = ByteVector32(preimage),
                origin = when (origin) {
                    is IncomingOriginData.Invoice.V0 -> LegacyPayToOpenIncomingPayment.Origin.Invoice(Bolt11Invoice.read(origin.paymentRequest).get())
                    is IncomingOriginData.Offer.V0 -> LegacyPayToOpenIncomingPayment.Origin.Offer(OfferPaymentMetadata.decode(origin.encodedMetadata))
                    else -> error("impossible")
                },
                parts = parts.map {
                    when (it) {
                        is IncomingReceivedWithData.Part.Htlc.V0 -> LegacyPayToOpenIncomingPayment.Part.Lightning(
                            amountReceived = it.amount,
                            channelId = it.channelId,
                            htlcId = it.htlcId
                        )
                        is IncomingReceivedWithData.Part.Htlc.V1 -> LegacyPayToOpenIncomingPayment.Part.Lightning(
                            amountReceived = it.amountReceived,
                            channelId = it.channelId,
                            htlcId = it.htlcId
                        )
                        is IncomingReceivedWithData.NewChannel.V0 -> LegacyPayToOpenIncomingPayment.Part.OnChain(
                            amountReceived = received_amount_msat?.msat ?: 0.msat,
                            serviceFee = it.fees,
                            miningFee = 0.sat,
                            channelId = it.channelId ?: ByteVector32.Zeroes,
                            txId = TxId(ByteVector32.Zeroes),
                            confirmedAt = 0,
                            lockedAt = 0,
                        )
                        is IncomingReceivedWithData.Part.NewChannel.V0 -> LegacyPayToOpenIncomingPayment.Part.OnChain(
                            amountReceived = when {
                                origin_type == IncomingOriginTypeVersion.SWAPIN_V0.name -> it.amount
                                received_with_type == IncomingReceivedWithTypeVersion.MULTIPARTS_V0.name -> it.amount - it.fees
                                else -> it.amount
                            },
                            serviceFee = it.fees,
                            miningFee = 0.sat,
                            channelId = it.channelId ?: ByteVector32.Zeroes,
                            txId = TxId(ByteVector32.Zeroes),
                            confirmedAt = 0,
                            lockedAt = 0,
                        )
                        is IncomingReceivedWithData.Part.NewChannel.V1 -> LegacyPayToOpenIncomingPayment.Part.OnChain(
                            amountReceived = it.amount,
                            serviceFee = it.fees,
                            miningFee = 0.sat,
                            channelId = it.channelId ?: ByteVector32.Zeroes,
                            txId = TxId(ByteVector32.Zeroes),
                            confirmedAt = 0,
                            lockedAt = 0,
                        )
                        is IncomingReceivedWithData.Part.NewChannel.V2 -> LegacyPayToOpenIncomingPayment.Part.OnChain(
                            amountReceived = it.amount,
                            serviceFee = it.serviceFee,
                            miningFee = it.miningFee,
                            channelId = it.channelId,
                            txId = TxId(it.txId),
                            confirmedAt = it.confirmedAt,
                            lockedAt = it.lockedAt,
                        )
                        is IncomingReceivedWithData.Part.SpliceIn.V0 -> LegacyPayToOpenIncomingPayment.Part.OnChain(
                            amountReceived = it.amount,
                            serviceFee = it.serviceFee,
                            miningFee = it.miningFee,
                            channelId = it.channelId,
                            txId = TxId(it.txId),
                            confirmedAt = it.confirmedAt,
                            lockedAt = it.lockedAt,
                        )
                        else -> error("unexpected part=$it")
                    }
                },
                createdAt = created_at,
                completedAt = received_at
            )
        received_at != null && origin is IncomingOriginData.SwapIn.V0 && parts.any { it is IncomingReceivedWithData.NewChannel || it is IncomingReceivedWithData.Part.NewChannel.V0  } ->
            LegacySwapInIncomingPayment(
                id = UUID.randomUUID(),
                amountReceived = parts.mapNotNull { part ->
                    when (part) {
                        is IncomingReceivedWithData.NewChannel.V0 -> received_amount_msat?.msat
                        is IncomingReceivedWithData.Part.NewChannel.V0 -> part.amount
                        else -> null
                    }
                }.sum(),
                fees = parts.mapNotNull { part ->
                    when (part) {
                        is IncomingReceivedWithData.NewChannel.V0 -> part.fees
                        is IncomingReceivedWithData.Part.NewChannel.V0 -> part.fees
                        else -> null
                    }
                }.sum(),
                address = origin.address,
                createdAt = created_at,
                completedAt = received_at,
            )
        received_at != null && origin is IncomingOriginData.OnChain.V0 && parts.any { it is IncomingReceivedWithData.Part.SpliceIn.V0 || it is IncomingReceivedWithData.Part.NewChannel.V1 || it is IncomingReceivedWithData.Part.NewChannel.V2 } ->
            SpliceInIncomingPayment(
                id = UUID.randomUUID(),
                amountReceived = parts.mapNotNull { part ->
                    when (part) {
                        is IncomingReceivedWithData.Part.SpliceIn.V0 -> part.amount
                        is IncomingReceivedWithData.Part.NewChannel.V1 -> part.amount
                        is IncomingReceivedWithData.Part.NewChannel.V2 -> part.amount
                        else -> null
                    }
                }.sum(),
                miningFee = parts.mapNotNull { part ->
                    when (part) {
                        is IncomingReceivedWithData.Part.SpliceIn.V0 -> part.miningFee
                        is IncomingReceivedWithData.Part.NewChannel.V1 -> 0.sat
                        is IncomingReceivedWithData.Part.NewChannel.V2 -> part.miningFee
                        else -> null
                    }
                }.sum(),
                channelId = parts.firstOrNull().let {
                    when (it) {
                        is IncomingReceivedWithData.Part.SpliceIn.V0 -> it.channelId
                        is IncomingReceivedWithData.Part.NewChannel.V1 -> it.channelId
                        is IncomingReceivedWithData.Part.NewChannel.V2 -> it.channelId
                        else -> null
                    }
                } ?: ByteVector32.Zeroes,
                txId = parts.firstOrNull().let {
                    when (it) {
                        is IncomingReceivedWithData.Part.SpliceIn.V0 -> TxId(it.txId)
                        is IncomingReceivedWithData.Part.NewChannel.V2 -> TxId(it.txId)
                        else -> null
                    }
                } ?: TxId(ByteVector32.Zeroes),
                localInputs = origin.outpoints.toSet(),
                createdAt = created_at,
                confirmedAt = parts.firstOrNull().let {
                    when (it) {
                        is IncomingReceivedWithData.Part.SpliceIn.V0 -> it.confirmedAt
                        is IncomingReceivedWithData.Part.NewChannel.V2 -> it.confirmedAt
                        else -> null
                    }
                } ?: received_at,
                lockedAt = parts.firstOrNull().let {
                    when (it) {
                        is IncomingReceivedWithData.Part.SpliceIn.V0 -> it.lockedAt
                        is IncomingReceivedWithData.Part.NewChannel.V2 -> it.lockedAt
                        else -> null
                    }
                } ?: received_at,
            )

        else -> TODO("unsupported payment origin=($origin_type)$origin parts=($received_with_type)$parts")
    }
}