/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.db.ChannelCloseOutgoingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.data.FiatCurrency.Companion.valueOfOrNull
import fr.acinq.phoenix.db.payments.DbTypesHelper.decodeBlob
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.phoenix.db.serializers.v1.SatoshiSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
        data class V0(@Serializable val preimage: ByteVector32) : SucceededOffChain()
    }

    sealed class SucceededOnChain : OutgoingStatusData() {
        @Serializable
        data class V0(
            val txIds: List<@Serializable ByteVector32>,
            @Serializable val claimed: Satoshi,
            val closingType: String
        ) : SucceededOnChain()

        @Serializable
        object V1 : SucceededOnChain()
    }

    sealed class Failed : OutgoingStatusData() {
        @Serializable
        data class V0(val reason: String) : Failed()
    }

    companion object {

        /** Extract valuable data from old outgoing payments status that represent closing transactions. */
        fun deserializeLegacyClosingStatus(blob: ByteArray): SucceededOnChain.V0 = decodeBlob(blob) { json, format ->
            val data = format.decodeFromString<SucceededOnChain.V0>(json)
            data
        }

        fun deserialize(typeVersion: OutgoingStatusTypeVersion, blob: ByteArray, completedAt: Long): LightningOutgoingPayment.Status = decodeBlob(blob) { json, format ->
            @Suppress("DEPRECATION")
            when (typeVersion) {
                OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 -> format.decodeFromString<SucceededOffChain.V0>(json).let {
                    LightningOutgoingPayment.Status.Completed.Succeeded.OffChain(it.preimage, completedAt)
                }
                OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0, OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V1 -> {
                    TODO("impossible scenario")
                }
                OutgoingStatusTypeVersion.FAILED_V0 -> format.decodeFromString<Failed.V0>(json).let {
                    LightningOutgoingPayment.Status.Completed.Failed(deserializeFinalFailure(it.reason), completedAt)
                }
            }
        }

        internal fun serializeFinalFailure(failure: FinalFailure): String = failure::class.simpleName ?: "UnknownError"

        private fun deserializeFinalFailure(failure: String): FinalFailure = when (failure) {
            FinalFailure.InvalidPaymentAmount::class.simpleName -> FinalFailure.InvalidPaymentAmount
            FinalFailure.InvalidPaymentId::class.simpleName -> FinalFailure.InvalidPaymentId
            FinalFailure.NoAvailableChannels::class.simpleName -> FinalFailure.NoAvailableChannels
            FinalFailure.InsufficientBalance::class.simpleName -> FinalFailure.InsufficientBalance
            FinalFailure.NoRouteToRecipient::class.simpleName -> FinalFailure.NoRouteToRecipient
            FinalFailure.RecipientUnreachable::class.simpleName -> FinalFailure.RecipientUnreachable
            FinalFailure.RetryExhausted::class.simpleName -> FinalFailure.RetryExhausted
            FinalFailure.WalletRestarted::class.simpleName -> FinalFailure.WalletRestarted
            else -> FinalFailure.UnknownError
        }
    }
}

fun LightningOutgoingPayment.Status.Completed.mapToDb(): Pair<OutgoingStatusTypeVersion, ByteArray> = when (this) {
    is LightningOutgoingPayment.Status.Completed.Succeeded.OffChain -> OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 to
            Json.encodeToString(OutgoingStatusData.SucceededOffChain.V0(preimage)).toByteArray(Charsets.UTF_8)
    is LightningOutgoingPayment.Status.Completed.Failed -> OutgoingStatusTypeVersion.FAILED_V0 to
            Json.encodeToString(OutgoingStatusData.Failed.V0(OutgoingStatusData.serializeFinalFailure(reason))).toByteArray(Charsets.UTF_8)
}

