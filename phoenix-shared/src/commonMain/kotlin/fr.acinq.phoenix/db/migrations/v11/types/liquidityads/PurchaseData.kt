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
    MilliSatoshiSerializer::class
)

package fr.acinq.phoenix.db.migrations.v11.types.liquidityads

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.db.migrations.v11.types.liquidityads.PaymentDetailsData.Companion.asCanonical
import fr.acinq.phoenix.db.migrations.v10.json.SatoshiSerializer
import fr.acinq.phoenix.db.migrations.v10.json.MilliSatoshiSerializer
import fr.acinq.lightning.wire.LiquidityAds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json

@Serializable
sealed class PurchaseData {
    sealed class Standard : PurchaseData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.liquidityads.PurchaseData.Standard.V0")
        data class V0(
            val amount: Satoshi,
            val miningFees: Satoshi,
            val serviceFee: Satoshi,
            val paymentDetails: PaymentDetailsData,
        ) : Standard()
    }
    sealed class WithFeeCredit : PurchaseData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.liquidityads.PurchaseData.WithFeeCredit.V0")
        data class V0(
            val amount: Satoshi,
            val miningFees: Satoshi,
            val serviceFee: Satoshi,
            val feeCreditUsed: MilliSatoshi,
            val paymentDetails: PaymentDetailsData,
        ) : WithFeeCredit()
    }

    companion object {
        private fun PurchaseData.asCanonical(): LiquidityAds.Purchase = when (this) {
            is Standard.V0 -> LiquidityAds.Purchase.Standard(
                amount = amount,
                fees = LiquidityAds.Fees(miningFee = miningFees, serviceFee = serviceFee),
                paymentDetails = paymentDetails.asCanonical()
            )
            is WithFeeCredit.V0 -> LiquidityAds.Purchase.WithFeeCredit(
                amount = amount,
                fees = LiquidityAds.Fees(miningFee = miningFees, serviceFee = serviceFee),
                feeCreditUsed = feeCreditUsed,
                paymentDetails = paymentDetails.asCanonical()
            )
        }

        /**
         * Deserializes a json-encoded blob into a [LiquidityAds.Purchase] object.
         *
         * @param typeVersion only used for the legacy leased data, where the blob did not contain the type of the object.
         */
        @Suppress("DEPRECATION")
        fun decodeAsCanonical(
            typeVersion: String,
            blob: ByteArray,
        ): LiquidityAds.Purchase =
            when (typeVersion) {
                InboundLiquidityLeaseType.LEASE_V0.name -> Json.decodeFromString<LeaseV0>(blob.decodeToString()).toLiquidityAdsPurchase()
                else -> Json.decodeFromString<PurchaseData>(blob.decodeToString()).asCanonical()
            }
    }
}
