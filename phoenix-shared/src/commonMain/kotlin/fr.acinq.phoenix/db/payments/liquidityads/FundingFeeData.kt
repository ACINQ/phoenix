@file:UseSerializers(
    MilliSatoshiSerializer::class,
    TxIdSerializer::class,
)

package fr.acinq.phoenix.db.payments.liquidityads

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.db.serializers.v1.MilliSatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.TxIdSerializer
import fr.acinq.lightning.wire.LiquidityAds
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
sealed class FundingFeeData {

    @Serializable
    data class V0(val amount: MilliSatoshi, val fundingTxId: TxId) : FundingFeeData()

    companion object {
        fun FundingFeeData.asCanonical(): LiquidityAds.FundingFee = when (this) {
            is V0 -> LiquidityAds.FundingFee(amount = amount, fundingTxId = fundingTxId)
        }
        fun LiquidityAds.FundingFee.asDb(): FundingFeeData = V0(amount = amount, fundingTxId = fundingTxId)
    }
}