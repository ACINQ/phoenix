package fr.acinq.phoenix.db.cloud.payments

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import fr.acinq.phoenix.db.payments.liquidityads.PurchaseData
import fr.acinq.phoenix.db.payments.liquidityads.PurchaseData.Companion.encodeAsDb
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString


/** New inbound liquidity wrapper that uses the [LiquidityAds.Purchase] object. */
@Suppress("ArrayInDataClass")
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class InboundLiquidityPaymentWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @ByteString val channelId: ByteArray,
    @ByteString val txId: ByteArray,
    val miningFeesSat: Long,
    val purchase: LiquidityAdsPurchaseWrapper,
    val createdAt: Long,
    val confirmedAt: Long?,
    val lockedAt: Long?,
) {
    constructor(src: InboundLiquidityOutgoingPayment) : this(
        id = src.id,
        channelId = src.channelId.toByteArray(),
        txId = src.txId.value.toByteArray(),
        // see lightning-kmp#710 and comment in InboundLiquidityQueries mapper
        // miningFeesSat contains the local fee + the purchase fee
        miningFeesSat = src.miningFees.sat,
        purchase = LiquidityAdsPurchaseWrapper(src.purchase),
        createdAt = src.createdAt,
        confirmedAt = src.confirmedAt,
        lockedAt = src.lockedAt
    )

    @Throws(Exception::class)
    fun unwrap(): InboundLiquidityOutgoingPayment {
        val purchase = this.purchase.unwrap()
        return InboundLiquidityOutgoingPayment(
            id = this.id,
            channelId = this.channelId.toByteVector32(),
            txId = TxId(this.txId),
            // see lightning-kmp#710 and comment in InboundLiquidityQueries mapper
            // miningFeesSat contains the local fee + the purchase fee
            localMiningFees = this.miningFeesSat.sat - purchase.fees.miningFee,
            purchase = purchase,
            createdAt = this.createdAt,
            confirmedAt = this.confirmedAt,
            lockedAt = this.lockedAt,
        )
    }

    @Serializable
    data class LiquidityAdsPurchaseWrapper(@ByteString val blob: ByteArray) {
        companion object {
            operator fun invoke(purchase: LiquidityAds.Purchase): LiquidityAdsPurchaseWrapper {
                return LiquidityAdsPurchaseWrapper(purchase.encodeAsDb())
            }
        }
        fun unwrap(): LiquidityAds.Purchase {
            return PurchaseData.decodeAsCanonical("", blob)
        }
    }
}

/** This is the legacy wrapper for inbound liquidity, that used a Lease object to represent the liquidity purchase. Used only for deserialization now. */
@Serializable
@Suppress("ArrayInDataClass")
@OptIn(ExperimentalSerializationApi::class)
data class InboundLiquidityLegacyWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @ByteString val channelId: ByteArray,
    @ByteString val txId: ByteArray,
    val miningFeesSat: Long,
    val lease: LiquidityAdsLeaseWrapper,
    val createdAt: Long,
    val confirmedAt: Long?,
    val lockedAt: Long?,
) {
    @Throws(Exception::class)
    fun unwrap(): InboundLiquidityOutgoingPayment {
        val lease = this.lease.unwrap()
        return InboundLiquidityOutgoingPayment(
            id = this.id,
            channelId = this.channelId.toByteVector32(),
            txId = TxId(this.txId),
            // see lightning-kmp#710 and comment in InboundLiquidityQueries mapper
            // miningFeesSat contains the local fee + the purchase fee (even for legacy data)
            localMiningFees = this.miningFeesSat.sat - lease.fees.miningFee,
            purchase = lease,
            createdAt = this.createdAt,
            confirmedAt = this.confirmedAt,
            lockedAt = this.lockedAt,
        )
    }

    @Serializable
    data class LiquidityAdsLeaseWrapper(
        val amountSat: Long,
        val fees: LiquidityAdsLeaseFeesWrapper,
    ) {
        @Throws(Exception::class)
        fun unwrap(): LiquidityAds.Purchase{
            return LiquidityAds.Purchase.Standard(
                amount = this.amountSat.sat,
                fees = this.fees.unwrap().let { LiquidityAds.Fees(miningFee = it.miningFee, serviceFee = it.serviceFee) },
                paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance
            )
        }
    }

    @Serializable
    data class LiquidityAdsLeaseFeesWrapper(
        val miningFeeSat: Long,
        val serviceFeeSat: Long
    ) {
        @Throws(Exception::class)
        fun unwrap() = LiquidityAds.Fees(
            miningFee = this.miningFeeSat.sat,
            serviceFee = this.serviceFeeSat.sat
        )
    }
}