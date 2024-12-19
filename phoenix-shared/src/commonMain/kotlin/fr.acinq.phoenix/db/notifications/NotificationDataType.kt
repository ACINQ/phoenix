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
import fr.acinq.phoenix.db.payments.DbTypesHelper
import fr.acinq.phoenix.db.migrations.v10.json.ByteVector32Serializer
import fr.acinq.phoenix.db.migrations.v10.json.MilliSatoshiSerializer
import fr.acinq.phoenix.db.migrations.v10.json.SatoshiSerializer
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
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

        sealed class MissingOffchainAmountTooLow : PaymentRejected() {
            @Serializable
            data class V0(@Serializable val amount: MilliSatoshi, val source: LiquidityEvents.Source) : MissingOffchainAmountTooLow()
        }

        sealed class GenericError : PaymentRejected() {
            @Serializable
            data class V0(@Serializable val amount: MilliSatoshi, val source: LiquidityEvents.Source) : GenericError()
        }
    }

    sealed class WatchTowerOutcome: NotificationData() {
        sealed class Unknown : WatchTowerOutcome() {
            @Serializable
            data object V0: Unknown()
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

        fun decode(blob: ByteArray): NotificationData? = try {
            DbTypesHelper.decodeBlob(blob) { json, format -> format.decodeFromString<NotificationData>(json) }
        } catch (e: Exception) {
            // notifications are not critical data, can be ignored if malformed
            null
        }

        fun Notification.encodeAsDb(): ByteArray = Json.encodeToString(this.asDb()).toByteArray(Charsets.UTF_8)

        private fun Notification.asDb(): NotificationData = when (this) {
            is Notification.OverAbsoluteFee -> PaymentRejected.OverAbsoluteFee.V0(amount, source, fee, maxAbsoluteFee)
            is Notification.OverRelativeFee -> PaymentRejected.OverRelativeFee.V0(amount, source, fee, maxRelativeFeeBasisPoints)
            is Notification.FeePolicyDisabled -> PaymentRejected.Disabled.V0(amount, source)
            is Notification.MissingOffChainAmountTooLow -> PaymentRejected.MissingOffchainAmountTooLow.V0(amount, source)
            is Notification.GenericError -> PaymentRejected.GenericError.V0(amount, source)
            is fr.acinq.phoenix.data.WatchTowerOutcome.Nominal -> WatchTowerOutcome.Nominal.V0(channelsWatchedCount)
            is fr.acinq.phoenix.data.WatchTowerOutcome.RevokedFound -> WatchTowerOutcome.RevokedFound.V0(channels)
            is fr.acinq.phoenix.data.WatchTowerOutcome.Unknown -> WatchTowerOutcome.Unknown.V0
        }
    }
}
