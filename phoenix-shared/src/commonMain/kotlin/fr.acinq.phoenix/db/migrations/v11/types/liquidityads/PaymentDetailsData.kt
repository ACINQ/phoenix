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

package fr.acinq.phoenix.db.migrations.v11.types.liquidityads

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.phoenix.db.migrations.v10.json.ByteVector32Serializer
import fr.acinq.lightning.wire.LiquidityAds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers


@Serializable
sealed class PaymentDetailsData {
    sealed class ChannelBalance : PaymentDetailsData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.liquidityads.PaymentDetailsData.ChannelBalance.V0")
        data object V0 : ChannelBalance()
    }

    sealed class FutureHtlc : PaymentDetailsData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.liquidityads.PaymentDetailsData.FutureHtlc.V0")
        data class V0(val paymentHashes: List<ByteVector32>) : FutureHtlc()
    }

    sealed class FutureHtlcWithPreimage : PaymentDetailsData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.liquidityads.PaymentDetailsData.FutureHtlcWithPreimage.V0")
        data class V0(val preimages: List<ByteVector32>) : FutureHtlcWithPreimage()
    }

    sealed class ChannelBalanceForFutureHtlc : PaymentDetailsData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.liquidityads.PaymentDetailsData.ChannelBalanceForFutureHtlc.V0")
        data class V0(val paymentHashes: List<ByteVector32>) : ChannelBalanceForFutureHtlc()
    }

    companion object {
        fun PaymentDetailsData.asCanonical(): LiquidityAds.PaymentDetails = when (this) {
            is ChannelBalance.V0 -> LiquidityAds.PaymentDetails.FromChannelBalance
            is FutureHtlc.V0 -> LiquidityAds.PaymentDetails.FromFutureHtlc(this.paymentHashes)
            is FutureHtlcWithPreimage.V0 -> LiquidityAds.PaymentDetails.FromFutureHtlcWithPreimage(this.preimages)
            is ChannelBalanceForFutureHtlc.V0 -> LiquidityAds.PaymentDetails.FromChannelBalanceForFutureHtlc(this.paymentHashes)
        }
    }
}