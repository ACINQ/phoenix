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

package fr.acinq.phoenix.data

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis

/** Notification object, typically regarding missed payments. */
sealed class Notification {
    abstract val id: UUID
    abstract val createdAt: Long
    abstract val readAt: Long?
    val hasBeenRead by lazy { readAt != null }

    sealed class PaymentRejected : Notification() {
        abstract val amount: MilliSatoshi
        abstract val source: LiquidityEvents.Source
    }

    data class OverAbsoluteFee(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
        val fee: MilliSatoshi,
        override val source: LiquidityEvents.Source,
        val maxAbsoluteFee: Satoshi,
    ) : PaymentRejected()

    data class OverRelativeFee(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
        val fee: MilliSatoshi,
        override val source: LiquidityEvents.Source,
        val maxRelativeFeeBasisPoints: Int,
    ) : PaymentRejected()

    data class FeePolicyDisabled(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
        override val source: LiquidityEvents.Source,
    ) : PaymentRejected()

    data class MissingOffChainAmountTooLow(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
        override val source: LiquidityEvents.Source,
    ) : PaymentRejected()

    data class ChannelFundingInProgress(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
        override val source: LiquidityEvents.Source,
    ) : PaymentRejected()

    data class GenericError(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
        override val source: LiquidityEvents.Source,
    ) : PaymentRejected()
}

sealed class WatchTowerOutcome : Notification() {
    data class Unknown(override val id: UUID, override val createdAt: Long, override val readAt: Long?): WatchTowerOutcome() {
        constructor() : this(UUID.randomUUID(), currentTimestampMillis(), null)
    }
    data class Nominal(override val id: UUID, override val createdAt: Long, override val readAt: Long?, val channelsWatchedCount: Int): WatchTowerOutcome() {
        constructor(channelsWatchedCount: Int) : this(UUID.randomUUID(), currentTimestampMillis(), null, channelsWatchedCount)
    }
    data class RevokedFound(override val id: UUID, override val createdAt: Long, override val readAt: Long?, val channels: Set<ByteVector32>): WatchTowerOutcome() {
        constructor(channels: Set<ByteVector32>) : this(UUID.randomUUID(), currentTimestampMillis(), null, channels)
    }
}
