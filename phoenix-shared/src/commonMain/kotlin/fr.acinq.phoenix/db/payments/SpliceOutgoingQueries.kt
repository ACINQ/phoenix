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

import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didCompleteWalletPayment

class SpliceOutgoingQueries(val database: PaymentsDatabase) {
    private val spliceOutQueries = database.spliceOutgoingPaymentsQueries

    fun addSpliceOutgoingPayment(payment: SpliceOutgoingPayment) {
        spliceOutQueries.insertSpliceOutgoing(
            id = payment.id.toString(),
            amount_sat = payment.amountSatoshi.sat,
            address = payment.address,
            mining_fees_sat = payment.miningFees.sat,
            tx_id = payment.txId.toByteArray(),
            created_at = payment.createdAt,
            confirmed_at = payment.confirmedAt,
        )
    }

    fun getSpliceOutPayment(id: UUID): SpliceOutgoingPayment? {
        return spliceOutQueries.getSpliceOutgoing(
            id = id.toString(),
            mapper = ::mapSpliceOutgoingPayment
        ).executeAsOneOrNull()
    }

    fun setConfirmed(id: UUID, confirmedAt: Long) {
        spliceOutQueries.setConfirmed(confirmed_at = confirmedAt, id = id.toString())
        didCompleteWalletPayment(WalletPaymentId.SpliceOutgoingPaymentId(id), database)
    }

    companion object {
        fun mapSpliceOutgoingPayment(
            id: String,
            amount_sat: Long,
            address: String,
            mining_fees_sat: Long,
            tx_id: ByteArray,
            created_at: Long,
            confirmed_at: Long?
        ): SpliceOutgoingPayment {
            return SpliceOutgoingPayment(
                id = UUID.fromString(id),
                amountSatoshi = amount_sat.sat,
                address = address,
                miningFees = mining_fees_sat.sat,
                txId = tx_id.toByteVector32(),
                createdAt = created_at,
                confirmedAt = confirmed_at
            )
        }
    }
}