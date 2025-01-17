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

package fr.acinq.phoenix.db.migrations.v11.types

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.phoenix.db.migrations.v10.json.ByteVector32Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json


enum class OutgoingPartStatusTypeVersion {
    SUCCEEDED_V0,
    // Obsolete, do not use anymore. Failed parts are now typed, with a code and an option string message.
    FAILED_V0,
    FAILED_V1,
}

sealed class OutgoingPartStatusData {

    sealed class Succeeded : OutgoingPartStatusData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingPartStatusData.Succeeded.V0")
        data class V0(@Serializable val preimage: ByteVector32) : Succeeded()
    }

    sealed class Failed : OutgoingPartStatusData() {
        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingPartStatusData.Failed.V0")
        data class V0(val remoteFailureCode: Int?, val details: String) : Failed()

        @Serializable
        @SerialName("fr.acinq.phoenix.db.payments.OutgoingPartStatusData.Failed.V1")
        data class V1(val code: Int, val details: String?) : Failed()
    }

    companion object {
        fun deserialize(
            typeVersion: OutgoingPartStatusTypeVersion,
            blob: ByteArray,
            completedAt: Long
        ): LightningOutgoingPayment.Part.Status =
            when (typeVersion) {
                OutgoingPartStatusTypeVersion.SUCCEEDED_V0 -> Json.decodeFromString<Succeeded.V0>(blob.decodeToString()).let {
                    LightningOutgoingPayment.Part.Status.Succeeded(it.preimage, completedAt)
                }
                OutgoingPartStatusTypeVersion.FAILED_V0 -> Json.decodeFromString<Failed.V0>(blob.decodeToString()).let {
                    LightningOutgoingPayment.Part.Status.Failed(
                        failure = LightningOutgoingPayment.Part.Status.Failed.Failure.Uninterpretable(message = it.details),
                        completedAt = completedAt,
                    )
                }
                OutgoingPartStatusTypeVersion.FAILED_V1 -> Json.decodeFromString<Failed.V1>(blob.decodeToString()).let {
                    LightningOutgoingPayment.Part.Status.Failed(
                        failure = when (it.code) {
                            0 -> LightningOutgoingPayment.Part.Status.Failed.Failure.Uninterpretable(it.details ?: "n/a")
                            1 -> LightningOutgoingPayment.Part.Status.Failed.Failure.PaymentAmountTooSmall
                            2 -> LightningOutgoingPayment.Part.Status.Failed.Failure.PaymentAmountTooBig
                            3 -> LightningOutgoingPayment.Part.Status.Failed.Failure.NotEnoughFunds
                            4 -> LightningOutgoingPayment.Part.Status.Failed.Failure.NotEnoughFees
                            5 -> LightningOutgoingPayment.Part.Status.Failed.Failure.PaymentExpiryTooBig
                            6 -> LightningOutgoingPayment.Part.Status.Failed.Failure.TooManyPendingPayments
                            7 -> LightningOutgoingPayment.Part.Status.Failed.Failure.ChannelIsSplicing
                            8 -> LightningOutgoingPayment.Part.Status.Failed.Failure.ChannelIsClosing
                            9 -> LightningOutgoingPayment.Part.Status.Failed.Failure.TemporaryRemoteFailure
                            10 -> LightningOutgoingPayment.Part.Status.Failed.Failure.RecipientLiquidityIssue
                            11 -> LightningOutgoingPayment.Part.Status.Failed.Failure.RecipientIsOffline
                            12 -> LightningOutgoingPayment.Part.Status.Failed.Failure.RecipientRejectedPayment
                            else -> LightningOutgoingPayment.Part.Status.Failed.Failure.Uninterpretable(it.details ?: "n/a")
                        },
                        completedAt = completedAt,
                    )
                }
            }
    }
}
