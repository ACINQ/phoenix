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
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didSaveWalletPayment

class SpliceOutgoingQueries(val database: PaymentsDatabase) {
    private val spliceOutQueries = database.spliceOutgoingPaymentsQueries

    fun addSpliceOutgoingPayment(payment: SpliceOutgoingPayment) {
        database.transaction {
            spliceOutQueries.insertSpliceOutgoing(
                id = payment.id.toString(),
                recipient_amount_sat = payment.recipientAmount.sat,
                address = payment.address,
                mining_fees_sat = payment.miningFees.sat,
                channel_id = payment.channelId.toByteArray(),
                tx_id = payment.txId.value.toByteArray(),
                created_at = payment.createdAt,
                confirmed_at = payment.confirmedAt,
                locked_at = payment.lockedAt
            )
            didSaveWalletPayment(WalletPaymentId.SpliceOutgoingPaymentId(payment.id), database)
        }
    }

    fun getSpliceOutPayment(id: UUID): SpliceOutgoingPayment? {
        return spliceOutQueries.getSpliceOutgoing(
            id = id.toString(),
            mapper = ::mapSpliceOutgoingPayment
        ).executeAsOneOrNull()
    }

    fun setConfirmed(id: UUID, confirmedAt: Long) {
        database.transaction {
            spliceOutQueries.setConfirmed(confirmed_at = confirmedAt, id = id.toString())
            didSaveWalletPayment(WalletPaymentId.SpliceOutgoingPaymentId(id), database)
        }
    }

    fun setLocked(id: UUID, lockedAt: Long) {
        database.transaction {
            spliceOutQueries.setLocked(locked_at = lockedAt, id = id.toString())
            didSaveWalletPayment(WalletPaymentId.SpliceOutgoingPaymentId(id), database)
        }
    }

    companion object {
        fun mapSpliceOutgoingPayment(
            id: String,
            recipient_amount_sat: Long,
            address: String,
            mining_fees_sat: Long,
            tx_id: ByteArray,
            channel_id: ByteArray,
            created_at: Long,
            confirmed_at: Long?,
            locked_at: Long?
        ): SpliceOutgoingPayment {
            return SpliceOutgoingPayment(
                id = UUID.fromString(id),
                recipientAmount = recipient_amount_sat.sat,
                address = address,
                miningFees = mining_fees_sat.sat,
                txId = TxId(tx_id),
                channelId = channel_id.toByteVector32(),
                createdAt = created_at,
                confirmedAt = confirmed_at,
                lockedAt = locked_at
            )
        }
    }
}