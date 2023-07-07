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
    SatoshiSerializer::class,
    MilliSatoshiSerializer::class,
    ByteVector32Serializer::class,
)

package fr.acinq.phoenix.db.notifications

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.data.WatchTowerOutcome
import fr.acinq.phoenix.db.payments.DbTypesHelper
import fr.acinq.phoenix.db.serializers.v1.ByteVector32Serializer
import fr.acinq.phoenix.db.serializers.v1.MilliSatoshiSerializer
import fr.acinq.phoenix.db.serializers.v1.SatoshiSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class NotificationTypeVersion {
    PAYMENT_REJECTED_BY_USER_V0,
    PAYMENT_REJECTED_TOO_EXPENSIVE_V0,
    PAYMENT_REJECTED_OVER_ABSOLUTE_V0,
    PAYMENT_REJECTED_OVER_RELATIVE_V0,
    PAYMENT_REJECTED_DISABLED_V0,
    PAYMENT_REJECTED_CHANNELS_INIT_V0,

    WATCH_TOWER_NOMINAL_V0,
    WATCH_TOWER_UNKNOWN_V0,
    WATCH_TOWER_REVOKED_FOUND_V0,
}

internal sealed class NotificationData {
    sealed class PaymentRejected : NotificationData() {
        sealed class OverAbsoluteFee : PaymentRejected() {
            @Serializable
            data class V0(
                @Serializable val amount: MilliSatoshi,
                val source: LiquidityEvents.Source,
                @Serializable val fee: MilliSatoshi,
                @Serializable val maxAbsoluteFee: Satoshi,
            ) : OverAbsoluteFee()
        }

        sealed class OverRelativeFee : PaymentRejected() {
            @Serializable
            data class V0(
                @Serializable val amount: MilliSatoshi,
                val source: LiquidityEvents.Source,
                @Serializable val fee: MilliSatoshi,
                @Serializable val maxRelativeFeeBasisPoints: Int,
            ) : OverRelativeFee()
        }

        sealed class Disabled : PaymentRejected() {
            @Serializable
            data class V0(@Serializable val amount: MilliSatoshi, val source: LiquidityEvents.Source) : Disabled()
        }

        sealed class ChannelsInitializing : PaymentRejected() {
            @Serializable
            data class V0(@Serializable val amount: MilliSatoshi, val source: LiquidityEvents.Source) : ChannelsInitializing()
        }
    }

    sealed class WatchTowerOutcome: NotificationData() {
        sealed class Unknown : WatchTowerOutcome() {
            @Serializable
            object V0: Unknown()
        }
        sealed class Nominal : WatchTowerOutcome() {
            @Serializable
            data class V0(@Serializable val channelsWatchedCount: Int): Nominal()
        }
        sealed class RevokedFound : WatchTowerOutcome() {
            @Serializable
            data class V0(@Serializable val channels: Set<@Serializable ByteVector32>): RevokedFound()
        }
    }

    companion object {
        fun deserialize(typeVersion: NotificationTypeVersion, blob: ByteArray): NotificationData? = try {
            DbTypesHelper.decodeBlob(blob) { json, format ->
                when (typeVersion) {
                    NotificationTypeVersion.PAYMENT_REJECTED_OVER_ABSOLUTE_V0 -> format.decodeFromString<PaymentRejected.OverAbsoluteFee.V0>(json)
                    NotificationTypeVersion.PAYMENT_REJECTED_OVER_RELATIVE_V0 -> format.decodeFromString<PaymentRejected.OverRelativeFee.V0>(json)
                    NotificationTypeVersion.PAYMENT_REJECTED_DISABLED_V0 -> format.decodeFromString<PaymentRejected.Disabled.V0>(json)
                    NotificationTypeVersion.PAYMENT_REJECTED_CHANNELS_INIT_V0 -> format.decodeFromString<PaymentRejected.ChannelsInitializing.V0>(json)

                    NotificationTypeVersion.WATCH_TOWER_NOMINAL_V0 -> format.decodeFromString<WatchTowerOutcome.Nominal.V0>(json)
                    NotificationTypeVersion.WATCH_TOWER_UNKNOWN_V0 -> format.decodeFromString<WatchTowerOutcome.Unknown.V0>(json)
                    NotificationTypeVersion.WATCH_TOWER_REVOKED_FOUND_V0 -> format.decodeFromString<WatchTowerOutcome.RevokedFound.V0>(json)

                    // obsolete types
                    NotificationTypeVersion.PAYMENT_REJECTED_BY_USER_V0, NotificationTypeVersion.PAYMENT_REJECTED_TOO_EXPENSIVE_V0 -> {
                        throw UnsupportedOperationException()
                    }
                }
            }
        } catch (e: Exception) {
            // notifications are not critical data, can be ignored if malformed
            null
        }
    }
}

internal fun Notification.mapToDb(): Pair<NotificationTypeVersion, ByteArray> = when (this) {
    is Notification.OverAbsoluteFee -> NotificationTypeVersion.PAYMENT_REJECTED_OVER_ABSOLUTE_V0 to Json.encodeToString(NotificationData.PaymentRejected.OverAbsoluteFee.V0(amount, source, fee, maxAbsoluteFee)).toByteArray(Charsets.UTF_8)
    is Notification.OverRelativeFee -> NotificationTypeVersion.PAYMENT_REJECTED_OVER_RELATIVE_V0 to Json.encodeToString(NotificationData.PaymentRejected.OverRelativeFee.V0(amount, source, fee, maxRelativeFeeBasisPoints)).toByteArray(Charsets.UTF_8)
    is Notification.FeePolicyDisabled -> NotificationTypeVersion.PAYMENT_REJECTED_DISABLED_V0 to Json.encodeToString(NotificationData.PaymentRejected.Disabled.V0(amount, source)).toByteArray(Charsets.UTF_8)
    is Notification.ChannelsInitializing -> NotificationTypeVersion.PAYMENT_REJECTED_CHANNELS_INIT_V0 to Json.encodeToString(NotificationData.PaymentRejected.ChannelsInitializing.V0(amount, source)).toByteArray(Charsets.UTF_8)
    is WatchTowerOutcome.Nominal -> NotificationTypeVersion.WATCH_TOWER_NOMINAL_V0 to Json.encodeToString(NotificationData.WatchTowerOutcome.Nominal.V0(channelsWatchedCount)).toByteArray(Charsets.UTF_8)
    is WatchTowerOutcome.RevokedFound -> NotificationTypeVersion.WATCH_TOWER_REVOKED_FOUND_V0 to Json.encodeToString(NotificationData.WatchTowerOutcome.RevokedFound.V0(channels)).toByteArray(Charsets.UTF_8)
    is WatchTowerOutcome.Unknown -> NotificationTypeVersion.WATCH_TOWER_UNKNOWN_V0 to Json.encodeToString(NotificationData.WatchTowerOutcome.Unknown.V0).toByteArray(Charsets.UTF_8)
}