/*
 * Copyright 2023 ACINQ SAS
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
    ByteVectorSerializer::class,
    ByteVector32Serializer::class,
    ByteVector64Serializer::class,
    SatoshiSerializer::class,
    MilliSatoshiSerializer::class
)

package fr.acinq.phoenix.db.migrations.v11.types.liquidityads

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector64
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.db.migrations.v10.json.ByteVector32Serializer
import fr.acinq.phoenix.db.migrations.v10.json.ByteVector64Serializer
import fr.acinq.phoenix.db.migrations.v10.json.ByteVectorSerializer
import fr.acinq.phoenix.db.migrations.v10.json.MilliSatoshiSerializer
import fr.acinq.phoenix.db.migrations.v10.json.SatoshiSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

enum class InboundLiquidityLeaseType {
    @Deprecated("obsolete with the new on-the-fly channel funding that replaces lease -> purchase")
    LEASE_V0
}

@Suppress("DEPRECATION_WARNING")
@Deprecated("obsolete with the new on-the-fly channel funding that replaces lease with purchase")
@Serializable
@SerialName("fr.acinq.phoenix.db.payments.liquidityads.LeaseV0")
data class LeaseV0(
    val amount: Satoshi,
    val miningFees: Satoshi,
    val serviceFee: Satoshi,
    val sellerSig: ByteVector64,
    val witnessFundingScript: ByteVector,
    val witnessLeaseDuration: Int,
    val witnessLeaseEnd: Int,
    val witnessMaxRelayFeeProportional: Int,
    val witnessMaxRelayFeeBase: MilliSatoshi
) {
    /** Maps a legacy lease data into the modern [LiquidityAds.Purchase] object using fake payment details data. */
    fun toLiquidityAdsPurchase(): LiquidityAds.Purchase = LiquidityAds.Purchase.Standard(
        amount = amount,
        fees = LiquidityAds.Fees(miningFee = miningFees, serviceFee = serviceFee),
        paymentDetails = LiquidityAds.PaymentDetails.FromChannelBalance
    )
}