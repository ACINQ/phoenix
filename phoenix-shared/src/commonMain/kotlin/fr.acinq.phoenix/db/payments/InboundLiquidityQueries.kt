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

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didSaveWalletPayment

class InboundLiquidityQueries(val database: PaymentsDatabase) {
    private val queries = database.inboundLiquidityOutgoingQueries

    fun add(payment: InboundLiquidityOutgoingPayment) {
        database.transaction {
            val (leaseType, leaseData) = payment.mapLeaseToDb()
           queries.insert(
               id = payment.id.toString(),
               mining_fees_sat = payment.miningFees.sat,
               channel_id = payment.channelId.toByteArray(),
               tx_id = payment.txId.value.toByteArray(),
               lease_type = leaseType,
               lease_blob = leaseData,
               created_at = payment.createdAt,
               confirmed_at = payment.confirmedAt,
               locked_at = payment.lockedAt,
           )
        }
    }

    fun get(id: UUID): InboundLiquidityOutgoingPayment? {
        return queries.get(id = id.toString(), mapper = ::mapPayment)
            .executeAsOneOrNull()
    }

    fun setConfirmed(id: UUID, confirmedAt: Long) {
        database.transaction {
            queries.setConfirmed(confirmed_at = confirmedAt, id = id.toString())
            didSaveWalletPayment(WalletPaymentId.InboundLiquidityOutgoingPaymentId(id), database)
        }
    }

    fun setLocked(id: UUID, lockedAt: Long) {
        database.transaction {
            queries.setLocked(locked_at = lockedAt, id = id.toString())
            didSaveWalletPayment(WalletPaymentId.InboundLiquidityOutgoingPaymentId(id), database)
        }
    }

    private companion object {
        fun mapPayment(
            id: String,
            mining_fees_sat: Long,
            channel_id: ByteArray,
            tx_id: ByteArray,
            lease_type: InboundLiquidityLeaseTypeVersion,
            lease_blob: ByteArray,
            created_at: Long,
            confirmed_at: Long?,
            locked_at: Long?
        ): InboundLiquidityOutgoingPayment {
            return InboundLiquidityOutgoingPayment(
                id = UUID.fromString(id),
                miningFees = mining_fees_sat.sat,
                channelId = channel_id.toByteVector32(),
                txId = TxId(tx_id),
                lease = InboundLiquidityLeaseData.deserialize(lease_type, lease_blob),
                createdAt = created_at,
                confirmedAt = confirmed_at,
                lockedAt = locked_at
            )
        }
    }
}