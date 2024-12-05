/*
 * Copyright 2021 ACINQ SAS
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

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.OnChainIncomingPayment
import fr.acinq.lightning.db.SpliceInIncomingPayment
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class IncomingQueries(private val database: PaymentsDatabase) {

    private val queries = database.incomingPaymentsQueries

    fun addIncomingPayment(
        incomingPayment: IncomingPayment
    ) {
        queries.insert(
            id = incomingPayment.id.toString(),
            payment_hash = (incomingPayment as? LightningIncomingPayment)?.paymentHash?.toByteArray(),
            created_at = incomingPayment.createdAt,
            received_at = incomingPayment.completedAt,
            json = incomingPayment
        )
    }

    fun receivePayment(
        paymentHash: ByteVector32,
        parts: List<LightningIncomingPayment.Received.Part>,
        receivedAt: Long
    ) {
        database.transaction {
            when (val paymentInDb = getIncomingPayment(paymentHash)) {
                null -> throw IncomingPaymentNotFound(paymentHash)
                is LightningIncomingPayment -> {
                    val newReceived = when (val received = paymentInDb.received) {
                        null -> LightningIncomingPayment.Received(parts, receivedAt)
                        else -> received.copy(parts = received.parts + parts)
                    }
                    val paymentInDb1 = when (paymentInDb) {
                        is Bolt11IncomingPayment -> paymentInDb.copy(received = newReceived)
                        is Bolt12IncomingPayment -> paymentInDb.copy(received = newReceived)
                    }
                    queries.updateReceived(
                        received_at = receivedAt,
                        json = paymentInDb1,
                        id = paymentInDb1.id.toString()
                    )
                    didSaveWalletPayment(WalletPaymentId.IncomingPaymentId.fromPaymentHash(paymentHash), database)
                }
                else -> error("unexpected type: $paymentInDb")
            }
        }
    }

    fun setLocked(id: UUID, lockedAt: Long) {
        database.transaction {
            when (val paymentInDb = getIncomingPayment(id)) {
                null -> {}
                is OnChainIncomingPayment -> {
                    val paymentInDb1 = when (paymentInDb) {
                        is NewChannelIncomingPayment -> paymentInDb.copy(lockedAt = lockedAt)
                        is SpliceInIncomingPayment -> paymentInDb.copy(lockedAt = lockedAt)
                    }
                    queries.updateReceived(
                        received_at = lockedAt,
                        json = paymentInDb1,
                        id = paymentInDb1.id.toString()
                    )
                    didSaveWalletPayment(WalletPaymentId.IncomingPaymentId(id), database)
                }
                else -> error("unexpected type: $paymentInDb")
            }
        }
    }

    fun setConfirmed(id: UUID, confirmedAt: Long) {
        database.transaction {
            when (val paymentInDb = getIncomingPayment(id)) {
                null -> {}
                is OnChainIncomingPayment -> {
                    val paymentInDb1 = when (paymentInDb) {
                        is NewChannelIncomingPayment -> paymentInDb.copy(confirmedAt = confirmedAt)
                        is SpliceInIncomingPayment -> paymentInDb.copy(confirmedAt = confirmedAt)
                    }
                    queries.updateReceived(
                        received_at = paymentInDb1.lockedAt, // keep the existing value
                        json = paymentInDb1,
                        id = paymentInDb1.id.toString()
                    )
                    didSaveWalletPayment(WalletPaymentId.IncomingPaymentId(id), database)
                }
                else -> error("unexpected type: $paymentInDb")
            }
        }
    }

    fun getIncomingPayment(id: UUID): IncomingPayment? {
        return queries.get(id = id.toString()).executeAsOneOrNull()?.json
    }

    fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return queries.getByPaymentHash(payment_hash = paymentHash.toByteArray()).executeAsOneOrNull()?.json
    }

    fun getOldestReceivedDate(): Long? {
        return queries.getOldestReceivedDate().executeAsOneOrNull()
    }

    fun listAllNotConfirmed(): Flow<List<IncomingPayment>> {
        return queries.listAllNotConfirmed().asFlow().mapToList(Dispatchers.IO)
    }

    fun listExpiredPayments(fromCreatedAt: Long, toCreatedAt: Long): List<LightningIncomingPayment> {
        return queries.listAllWithin(fromCreatedAt, toCreatedAt).executeAsList()
            .filterIsInstance<Bolt11IncomingPayment>()
            .filter { it.received == null && it.paymentRequest.isExpired() }
    }

    /** Try to delete an incoming payment ; return true if an element was deleted, false otherwise. */
    fun deleteIncomingPayment(id: UUID): Boolean {
        return database.transactionWithResult {
            queries.delete(id = id.toString())
            queries.changes().executeAsOne() != 0L
        }
    }

    /** Try to delete an incoming payment ; return true if an element was deleted, false otherwise. */
    fun deleteIncomingPayment(paymentHash: ByteVector32): Boolean {
        return database.transactionWithResult {
            queries.deleteByPaymentHash(payment_hash = paymentHash.toByteArray())
            queries.changes().executeAsOne() != 0L
        }
    }

}
