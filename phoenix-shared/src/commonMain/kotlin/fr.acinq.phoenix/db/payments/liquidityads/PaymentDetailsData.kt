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
    ByteVector32Serializer::class,
)

package fr.acinq.phoenix.db.payments.liquidityads

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.lightning.wire.LiquidityAds
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers


@Serializable
sealed class PaymentDetailsData {
    sealed class ChannelBalance : PaymentDetailsData() {
        @Serializable
        data object V0 : ChannelBalance()
    }

    sealed class FutureHtlc : PaymentDetailsData() {
        @Serializable
        data class V0(val paymentHashes: List<ByteVector32>) : FutureHtlc()
    }

    sealed class FutureHtlcWithPreimage : PaymentDetailsData() {
        @Serializable
        data class V0(val preimages: List<ByteVector32>) : FutureHtlcWithPreimage()
    }

    sealed class ChannelBalanceForFutureHtlc : PaymentDetailsData() {
        @Serializable
        data class V0(val paymentHashes: List<ByteVector32>) : ChannelBalanceForFutureHtlc()
    }

    companion object {
        fun PaymentDetailsData.asCanonical(): LiquidityAds.PaymentDetails = when (this) {
            is ChannelBalance.V0 -> LiquidityAds.PaymentDetails.FromChannelBalance
            is FutureHtlc.V0 -> LiquidityAds.PaymentDetails.FromFutureHtlc(this.paymentHashes)
            is FutureHtlcWithPreimage.V0 -> LiquidityAds.PaymentDetails.FromFutureHtlcWithPreimage(this.preimages)
            is ChannelBalanceForFutureHtlc.V0 -> LiquidityAds.PaymentDetails.FromChannelBalanceForFutureHtlc(this.paymentHashes)
        }

        fun LiquidityAds.PaymentDetails.asDb(): PaymentDetailsData = when (this) {
            is LiquidityAds.PaymentDetails.FromChannelBalance -> ChannelBalance.V0
            is LiquidityAds.PaymentDetails.FromFutureHtlc -> FutureHtlc.V0(this.paymentHashes)
            is LiquidityAds.PaymentDetails.FromFutureHtlcWithPreimage -> FutureHtlcWithPreimage.V0(this.preimages)
            is LiquidityAds.PaymentDetails.FromChannelBalanceForFutureHtlc -> ChannelBalanceForFutureHtlc.V0(this.paymentHashes)
        }
    }
}