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

package fr.acinq.phoenix.db.payments.liquidityads

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.db.payments.liquidityads.PaymentDetailsData.Companion.asCanonical
import fr.acinq.phoenix.db.payments.liquidityads.PaymentDetailsData.Companion.asDb
import fr.acinq.phoenix.db.serializers.v1.SatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.MilliSatoshiSerializer
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.db.payments.DbTypesHelper
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class PurchaseData {
    sealed class Standard : PurchaseData() {
        @Serializable
        data class V0(
            val amount: Satoshi,
            val miningFees: Satoshi,
            val serviceFee: Satoshi,
            val paymentDetails: PaymentDetailsData,
        ) : Standard()
    }
    sealed class WithFeeCredit : PurchaseData() {
        @Serializable
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

        private fun LiquidityAds.Purchase.asDb(): PurchaseData = when (val value = this) {
            is LiquidityAds.Purchase.Standard -> Standard.V0(
                amount = value.amount,
                miningFees = value.fees.miningFee,
                serviceFee = value.fees.serviceFee,
                paymentDetails = value.paymentDetails.asDb()
            )
            is LiquidityAds.Purchase.WithFeeCredit -> WithFeeCredit.V0(
                amount = value.amount, value.fees.miningFee,
                serviceFee = value.fees.serviceFee,
                paymentDetails = value.paymentDetails.asDb(),
                feeCreditUsed = value.feeCreditUsed
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
        ): LiquidityAds.Purchase = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                InboundLiquidityLeaseType.LEASE_V0.name -> format.decodeFromString<LeaseV0>(json).toLiquidityAdsPurchase()
                else -> format.decodeFromString<PurchaseData>(json).asCanonical()
            }
        }

        fun LiquidityAds.Purchase.encodeAsDb(): ByteArray = Json.encodeToString(this.asDb()).toByteArray(Charsets.UTF_8)
    }
}
