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

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.UUID

/** Notification object, typically regarding missed payments. */
sealed class Notification {
    abstract val id: UUID
    abstract val createdAt: Long
    abstract val readAt: Long?
    val hasBeenRead by lazy { readAt != null }

    sealed class PaymentRejected : Notification() {
        abstract val amount: MilliSatoshi
    }

    data class FeeTooExpensive(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
        val expectedFee: MilliSatoshi,
        val maxAllowedFee: MilliSatoshi,
    ) : PaymentRejected()

    data class RejectedManually(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi
    ) : PaymentRejected()

    data class FeePolicyDisabled(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
    ) : PaymentRejected()

    data class ChannelsInitializing(
        override val id: UUID,
        override val createdAt: Long,
        override val readAt: Long?,
        override val amount: MilliSatoshi,
    ) : PaymentRejected()
}