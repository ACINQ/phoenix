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
    ByteVector32Serializer::class,
)

package fr.acinq.phoenix.db.migrations.v11.types

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.phoenix.db.migrations.v10.json.ByteVector32Serializer
import fr.acinq.phoenix.db.migrations.v10.json.SatoshiSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json

enum class OutgoingStatusTypeVersion {
    SUCCEEDED_OFFCHAIN_V0,
    @Deprecated("Use the new SUCCEEDED_ONCHAIN_V1 format. This status was used to store data about channel closing transactions.")
    SUCCEEDED_ONCHAIN_V0,
    @Deprecated("Starting with splices, we now use SpliceOut or ChannelClose outgoing payments type for on-chain payments")
    SUCCEEDED_ONCHAIN_V1,
    FAILED_V0,
}

sealed class OutgoingStatusData {

    sealed class SucceededOffChain : OutgoingStatusData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingStatusData.SucceededOffChain.V0")
        data class V0(@Serializable val preimage: ByteVector32) : SucceededOffChain()
    }

    sealed class SucceededOnChain : OutgoingStatusData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingStatusData.SucceededOnChain.V0")
        data class V0(
            val txIds: List<@Serializable ByteVector32>,
            @Serializable val claimed: Satoshi,
            val closingType: String
        ) : SucceededOnChain()

        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingStatusData.SucceededOnChain.V1")
        data object V1 : SucceededOnChain()
    }

    sealed class Failed : OutgoingStatusData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingStatusData.Failed.V0")
        data class V0(val reason: String) : Failed()
    }

    companion object {

        /** Extract valuable data from old outgoing payments status that represent closing transactions. */
        fun deserializeLegacyClosingStatus(blob: ByteArray): SucceededOnChain.V0 = Json.decodeFromString<SucceededOnChain.V0>(blob.decodeToString())

        fun deserialize(typeVersion: OutgoingStatusTypeVersion, blob: ByteArray, completedAt: Long): LightningOutgoingPayment.Status =
            @Suppress("DEPRECATION")
            when (typeVersion) {
                OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 -> Json.decodeFromString<SucceededOffChain.V0>(blob.decodeToString()).let {
                    LightningOutgoingPayment.Status.Succeeded(it.preimage, completedAt)
                }
                OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0, OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V1 -> {
                    TODO("impossible scenario")
                }
                OutgoingStatusTypeVersion.FAILED_V0 -> Json.decodeFromString<Failed.V0>(blob.decodeToString()).let {
                    LightningOutgoingPayment.Status.Failed(deserializeFinalFailure(it.reason), completedAt)
                }
            }

        private fun deserializeFinalFailure(failure: String): FinalFailure = when (failure) {
            FinalFailure.InvalidPaymentAmount::class.simpleName -> FinalFailure.InvalidPaymentAmount
            FinalFailure.InvalidPaymentId::class.simpleName -> FinalFailure.InvalidPaymentId
            FinalFailure.NoAvailableChannels::class.simpleName -> FinalFailure.NoAvailableChannels
            FinalFailure.InsufficientBalance::class.simpleName -> FinalFailure.InsufficientBalance
            FinalFailure.RecipientUnreachable::class.simpleName -> FinalFailure.RecipientUnreachable
            FinalFailure.RetryExhausted::class.simpleName -> FinalFailure.RetryExhausted
            FinalFailure.WalletRestarted::class.simpleName -> FinalFailure.WalletRestarted
            FinalFailure.AlreadyPaid::class.simpleName -> FinalFailure.AlreadyPaid
            FinalFailure.ChannelClosing::class.simpleName -> FinalFailure.ChannelClosing
            FinalFailure.ChannelOpening::class.simpleName -> FinalFailure.ChannelOpening
            FinalFailure.ChannelNotConnected::class.simpleName -> FinalFailure.ChannelNotConnected
            FinalFailure.FeaturesNotSupported::class.simpleName -> FinalFailure.FeaturesNotSupported
            else -> FinalFailure.UnknownError
        }
    }
}
