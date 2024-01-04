package fr.acinq.phoenix.db.cloud

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.lightning.utils.toByteVector64
import fr.acinq.lightning.wire.LiquidityAds
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class InboundLiquidityPaymentWrapper(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @ByteString
    val channelId: ByteArray,
    @ByteString
    val txId: ByteArray,
    val miningFeesSat: Long,
    val lease: LiquidityAdsLeaseWrapper,
    val createdAt: Long,
    val confirmedAt: Long?,
    val lockedAt: Long?,
) {
    constructor(src: InboundLiquidityOutgoingPayment) : this(
        id = src.id,
        channelId = src.channelId.toByteArray(),
        txId = src.txId.value.toByteArray(),
        miningFeesSat = src.miningFees.sat,
        lease = LiquidityAdsLeaseWrapper(src.lease),
        createdAt = src.createdAt,
        confirmedAt = src.confirmedAt,
        lockedAt = src.lockedAt
    )

    @Throws(Exception::class)
    fun unwrap() = InboundLiquidityOutgoingPayment(
        id = this.id,
        channelId = this.channelId.toByteVector32(),
        txId = TxId(this.txId),
        miningFees = this.miningFeesSat.sat,
        lease = this.lease.unwrap(),
        createdAt = this.createdAt,
        confirmedAt = this.confirmedAt,
        lockedAt = this.lockedAt
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class LiquidityAdsLeaseWrapper(
        val amountSat: Long,
        val fees: LiquidityAdsLeaseFeesWrapper,
        @ByteString
        val sellerSig: ByteArray,
        val witness: LiquidityAdsLeaseWitnessWrapper
    ) {
        constructor(src: LiquidityAds.Lease) : this(
            amountSat = src.amount.sat,
            fees = LiquidityAdsLeaseFeesWrapper(src.fees),
            sellerSig = src.sellerSig.toByteArray(),
            witness = LiquidityAdsLeaseWitnessWrapper(src.witness)
        )

        @Throws(Exception::class)
        fun unwrap() = LiquidityAds.Lease(
            amount = this.amountSat.sat,
            fees = this.fees.unwrap(),
            sellerSig = this.sellerSig.toByteVector64(),
            witness = this.witness.unwrap()
        )
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class LiquidityAdsLeaseFeesWrapper(
        val miningFeeSat: Long,
        val serviceFeeSat: Long
    ) {
        constructor(src: LiquidityAds.LeaseFees) : this(
            miningFeeSat = src.miningFee.sat,
            serviceFeeSat = src.serviceFee.sat
        )

        @Throws(Exception::class)
        fun unwrap() = LiquidityAds.LeaseFees(
            miningFee = this.miningFeeSat.sat,
            serviceFee = this.serviceFeeSat.sat
        )
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    data class LiquidityAdsLeaseWitnessWrapper(
        @ByteString
        val fundingScript: ByteArray,
        val leaseDuration: Int,
        val leaseEnd: Int,
        val maxRelayFeeProportional: Int,
        val maxRelayFeeBaseMsat: Long
    ) {
        constructor(src: LiquidityAds.LeaseWitness) : this(
            fundingScript = src.fundingScript.toByteArray(),
            leaseDuration = src.leaseDuration,
            leaseEnd = src.leaseEnd,
            maxRelayFeeProportional = src.maxRelayFeeProportional,
            maxRelayFeeBaseMsat = src.maxRelayFeeBase.msat
        )

        @Throws(Exception::class)
        fun unwrap() = LiquidityAds.LeaseWitness(
            fundingScript = this.fundingScript.toByteVector(),
            leaseDuration = this.leaseDuration,
            leaseEnd = this.leaseEnd,
            maxRelayFeeProportional = this.maxRelayFeeProportional,
            maxRelayFeeBase = this.maxRelayFeeBaseMsat.msat
        )
    }
}