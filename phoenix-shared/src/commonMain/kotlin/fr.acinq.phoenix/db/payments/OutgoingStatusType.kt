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

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.serialization.ByteVector32KSerializer
import fr.acinq.lightning.serialization.SatoshiKSerializer
import fr.acinq.phoenix.db.payments.DbTypesHelper.decodeBlob
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class OutgoingStatusTypeVersion {
    SUCCEEDED_OFFCHAIN_V0,
    SUCCEEDED_ONCHAIN_V0,
    FAILED_V0,
}

sealed class OutgoingStatusData {

    sealed class SucceededOffChain : OutgoingStatusData() {
        @Serializable
        data class V0(@Serializable(with = ByteVector32KSerializer::class) val preimage: ByteVector32) : SucceededOffChain()
    }

    sealed class SucceededOnChain : OutgoingStatusData() {
        @Serializable
        data class V0(
            val txIds: List<@Serializable(with = ByteVector32KSerializer::class) ByteVector32>,
            @Serializable(with = SatoshiKSerializer::class) val claimed: Satoshi,
            val closingType: String
        ) : SucceededOnChain()
    }

    sealed class Failed : OutgoingStatusData() {
        @Serializable
        data class V0(val reason: String) : Failed()
    }

    companion object {
        fun deserialize(typeVersion: OutgoingStatusTypeVersion, blob: ByteArray, completedAt: Long): OutgoingPayment.Status = decodeBlob(blob) { json, format ->
            when (typeVersion) {
                OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 -> format.decodeFromString<SucceededOffChain.V0>(json).let {
                    OutgoingPayment.Status.Completed.Succeeded.OffChain(it.preimage, completedAt)
                }
                OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0 -> format.decodeFromString<SucceededOnChain.V0>(json).let {
                    OutgoingPayment.Status.Completed.Succeeded.OnChain(it.txIds, it.claimed, deserializeClosingType(it.closingType), completedAt)
                }
                OutgoingStatusTypeVersion.FAILED_V0 -> format.decodeFromString<Failed.V0>(json).let {
                    OutgoingPayment.Status.Completed.Failed(deserializeFinalFailure(it.reason), completedAt)
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

        internal fun serializeClosingType(closingType: ChannelClosingType) = closingType.name

        private fun deserializeClosingType(closingType: String): ChannelClosingType = try {
            ChannelClosingType.valueOf(closingType)
        } catch (e: Exception) {
            ChannelClosingType.Other
        }
    }
}

fun OutgoingPayment.Status.Completed.mapToDb(): Pair<OutgoingStatusTypeVersion, ByteArray> = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OffChain -> OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 to
            Json.encodeToString(OutgoingStatusData.SucceededOffChain.V0(preimage)).toByteArray(Charsets.UTF_8)
    is OutgoingPayment.Status.Completed.Succeeded.OnChain -> OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0 to
            Json.encodeToString(OutgoingStatusData.SucceededOnChain.V0(txids, claimed, OutgoingStatusData.serializeClosingType(closingType))).toByteArray(Charsets.UTF_8)
    is OutgoingPayment.Status.Completed.Failed -> OutgoingStatusTypeVersion.FAILED_V0 to
            Json.encodeToString(OutgoingStatusData.Failed.V0(OutgoingStatusData.serializeFinalFailure(reason))).toByteArray(Charsets.UTF_8)
}
