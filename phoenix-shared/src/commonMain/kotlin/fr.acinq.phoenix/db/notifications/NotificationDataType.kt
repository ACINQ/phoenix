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
    MilliSatoshiSerializer::class,
)

package fr.acinq.phoenix.db.notifications

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.db.payments.DbTypesHelper
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.phoenix.db.serializers.v1.MilliSatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.SatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.UUIDSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class NotificationTypeVersion {
    PAYMENT_REJECTED_BY_USER_V0,
    PAYMENT_REJECTED_TOO_EXPENSIVE_V0,
    PAYMENT_REJECTED_DISABLED_V0,
    PAYMENT_REJECTED_CHANNELS_INIT_V0,
}

sealed class NotificationData {
    sealed class PaymentRejected : NotificationData() {
        sealed class TooExpensive : PaymentRejected() {
            @Serializable
            data class V0(
                @Serializable val amount: MilliSatoshi,
                @Serializable val expectedFee: MilliSatoshi,
                @Serializable val maxAllowedFee: MilliSatoshi,
            ) : TooExpensive()
        }

        sealed class ByUser : PaymentRejected() {
            @Serializable
            data class V0(@Serializable val amount: MilliSatoshi) : ByUser()
        }

        sealed class Disabled : PaymentRejected() {
            @Serializable
            data class V0(@Serializable val amount: MilliSatoshi) : Disabled()
        }

        sealed class ChannelsInitializing : PaymentRejected() {
            @Serializable
            data class V0(@Serializable val amount: MilliSatoshi) : ChannelsInitializing()
        }
    }

    companion object {
        fun deserialize(typeVersion: NotificationTypeVersion, blob: ByteArray): NotificationData = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                NotificationTypeVersion.PAYMENT_REJECTED_TOO_EXPENSIVE_V0 -> format.decodeFromString<PaymentRejected.TooExpensive.V0>(json)
                NotificationTypeVersion.PAYMENT_REJECTED_BY_USER_V0 -> format.decodeFromString<PaymentRejected.ByUser.V0>(json)
                NotificationTypeVersion.PAYMENT_REJECTED_DISABLED_V0 -> format.decodeFromString<PaymentRejected.Disabled.V0>(json)
                NotificationTypeVersion.PAYMENT_REJECTED_CHANNELS_INIT_V0 -> format.decodeFromString<PaymentRejected.ChannelsInitializing.V0>(json)
            }
        }
    }
}

fun Notification.mapToDb(): Pair<NotificationTypeVersion, ByteArray> = when (this) {
    is Notification.FeeTooExpensive -> NotificationTypeVersion.PAYMENT_REJECTED_TOO_EXPENSIVE_V0 to Json.encodeToString(NotificationData.PaymentRejected.TooExpensive.V0(amount, expectedFee, maxAllowedFee)).toByteArray(Charsets.UTF_8)
    is Notification.RejectedManually -> NotificationTypeVersion.PAYMENT_REJECTED_BY_USER_V0 to Json.encodeToString(NotificationData.PaymentRejected.ByUser.V0(amount)).toByteArray(Charsets.UTF_8)
    is Notification.FeePolicyDisabled -> NotificationTypeVersion.PAYMENT_REJECTED_DISABLED_V0 to Json.encodeToString(NotificationData.PaymentRejected.Disabled.V0(amount)).toByteArray(Charsets.UTF_8)
    is Notification.ChannelsInitializing -> NotificationTypeVersion.PAYMENT_REJECTED_CHANNELS_INIT_V0 to Json.encodeToString(NotificationData.PaymentRejected.ChannelsInitializing.V0(amount)).toByteArray(Charsets.UTF_8)
}