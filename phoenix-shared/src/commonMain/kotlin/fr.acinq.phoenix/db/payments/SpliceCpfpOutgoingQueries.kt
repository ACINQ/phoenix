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

import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.SpliceOutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didSaveWalletPayment

class SpliceCpfpOutgoingQueries(val database: PaymentsDatabase) {
    private val cpfpQueries = database.spliceCpfpOutgoingPaymentsQueries

    fun addCpfpPayment(payment: SpliceCpfpOutgoingPayment) {
        database.transaction {
            cpfpQueries.insertCpfp(
                id = payment.id.toString(),
                mining_fees_sat = payment.miningFees.sat,
                channel_id = payment.channelId.toByteArray(),
                tx_id = payment.txId.toByteArray(),
                created_at = payment.createdAt,
                confirmed_at = payment.confirmedAt,
                locked_at = payment.lockedAt
            )
            didSaveWalletPayment(WalletPaymentId.SpliceCpfpOutgoingPaymentId(payment.id), database)
        }
    }

    fun getCpfp(id: UUID): SpliceCpfpOutgoingPayment? {
        return cpfpQueries.getCpfp(
            id = id.toString(),
            mapper = ::mapCpfp
        ).executeAsOneOrNull()
    }

    fun setConfirmed(id: UUID, confirmedAt: Long) {
        database.transaction {
            cpfpQueries.setConfirmed(confirmed_at = confirmedAt, id = id.toString())
            didSaveWalletPayment(WalletPaymentId.SpliceCpfpOutgoingPaymentId(id), database)
        }
    }

    fun setLocked(id: UUID, lockedAt: Long) {
        database.transaction {
            cpfpQueries.setLocked(locked_at = lockedAt, id = id.toString())
            didSaveWalletPayment(WalletPaymentId.SpliceCpfpOutgoingPaymentId(id), database)
        }
    }

    private companion object {
        fun mapCpfp(
            id: String,
            mining_fees_sat: Long,
            channel_id: ByteArray,
            tx_id: ByteArray,
            created_at: Long,
            confirmed_at: Long?,
            locked_at: Long?
        ): SpliceCpfpOutgoingPayment {
            return SpliceCpfpOutgoingPayment(
                id = UUID.fromString(id),
                miningFees = mining_fees_sat.sat,
                channelId = channel_id.toByteVector32(),
                txId = tx_id.toByteVector32(),
                createdAt = created_at,
                confirmedAt = confirmed_at,
                lockedAt = locked_at
            )
        }
    }
}