/*
 * Copyright 2024 ACINQ SAS
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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.OnChainOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.OutgoingPaymentsDb
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didSaveWalletPayment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqliteOutgoingPaymentsDb(private val database: PaymentsDatabase) : OutgoingPaymentsDb {

    override suspend fun addLightningOutgoingPaymentParts(parentId: UUID, parts: List<LightningOutgoingPayment.Part>) {
        withContext(Dispatchers.Default) {
            database.transaction {
                val payment = database.paymentsOutgoingQueries.get(parentId).executeAsOneOrNull() as LightningOutgoingPayment
                val payment1 = payment.copy(parts = payment.parts + parts)
                database.paymentsOutgoingQueries.update(
                    id = parentId,
                    data = payment1,
                    completed_at = null,
                    succeeded_at = null
                )
            }
            parts.forEach { part ->
                database.paymentsOutgoingQueries.insertPartLink(part_id = part.id, parent_id = parentId)
            }
            didSaveWalletPayment(parentId, database)
        }
    }

    override suspend fun addOutgoingPayment(outgoingPayment: OutgoingPayment) {
        withContext(Dispatchers.Default) {
            _addOutgoingPayment(outgoingPayment)
        }
    }

    /**
     * Internal version:
     * Used to add multiple incoming payments in a single database transaction.
     *
     * @param notify Set to false if `didSaveWalletPayment` should not be invoked
     *               (e.g. when downloading payments from the cloud)
     */
    fun _addOutgoingPayment(outgoingPayment: OutgoingPayment, notify: Boolean = true) = database.transaction {
        when (outgoingPayment) {
            is LightningOutgoingPayment -> {
                database.paymentsOutgoingQueries.insert(
                    id = outgoingPayment.id,
                    payment_hash = outgoingPayment.paymentHash,
                    tx_id = null,
                    created_at = outgoingPayment.createdAt,
                    completed_at = outgoingPayment.completedAt,
                    succeeded_at = outgoingPayment.succeededAt,
                    data_ = outgoingPayment
                )
            }
            is OnChainOutgoingPayment -> {
                database.paymentsOutgoingQueries.insert(
                    id = outgoingPayment.id,
                    payment_hash = null,
                    tx_id = outgoingPayment.txId,
                    created_at = outgoingPayment.createdAt,
                    completed_at = outgoingPayment.completedAt,
                    succeeded_at = outgoingPayment.succeededAt,
                    data_ = outgoingPayment
                )
                // The payment may have been downloaded from the cloud.
                // So it may already be confirmed/locked.
                database.onChainTransactionsQueries.insert(
                    payment_id = outgoingPayment.id,
                    tx_id = outgoingPayment.txId,
                    confirmed_at = outgoingPayment.confirmedAt,
                    locked_at = outgoingPayment.lockedAt
                )
            }
        }
        if (notify) {
            didSaveWalletPayment(outgoingPayment.id, database)
        }
    }

    override suspend fun completeLightningOutgoingPayment(id: UUID, status: LightningOutgoingPayment.Status.Completed) {
        withContext(Dispatchers.Default) {
            database.transaction {
                val payment = database.paymentsOutgoingQueries.get(id).executeAsOneOrNull() as LightningOutgoingPayment
                val payment1 = payment.copy(status = status)
                database.paymentsOutgoingQueries.update(
                    id = id,
                    data = payment1,
                    completed_at = payment1.completedAt,
                    succeeded_at = payment1.succeededAt,
                )
                didSaveWalletPayment(id, database)
            }
        }
    }

    override suspend fun completeLightningOutgoingPaymentPart(parentId: UUID, partId: UUID, status: LightningOutgoingPayment.Part.Status.Completed) {
        withContext(Dispatchers.Default) {
            database.transaction {
                val payment = database.paymentsOutgoingQueries.get(parentId).executeAsOneOrNull() as LightningOutgoingPayment
                val payment1 = payment.copy(parts = payment.parts.map {
                    when {
                        it.id == partId -> it.copy(status = status)
                        else -> it
                    }
                })
                database.paymentsOutgoingQueries.update(
                    id = parentId,
                    data = payment1,
                    completed_at = null, // parts do not update parent timestamps
                    succeeded_at = null
                )
                didSaveWalletPayment(parentId, database)
            }
        }
    }

    override suspend fun getLightningOutgoingPayment(id: UUID): LightningOutgoingPayment? {
        return withContext(Dispatchers.Default) {
            database.paymentsOutgoingQueries.get(id).executeAsOneOrNull() as? LightningOutgoingPayment
        }
    }

    override suspend fun getLightningOutgoingPaymentFromPartId(partId: UUID): LightningOutgoingPayment? {
        return withContext(Dispatchers.Default) {
            database.transactionWithResult {
                database.paymentsOutgoingQueries.getParentId(partId).executeAsOneOrNull()?.let { paymentId ->
                    database.paymentsOutgoingQueries.get(paymentId).executeAsOneOrNull() as? LightningOutgoingPayment
                }
            }
        }
    }

    override suspend fun listLightningOutgoingPayments(paymentHash: ByteVector32): List<LightningOutgoingPayment> {
        return withContext(Dispatchers.Default) {
            database.paymentsOutgoingQueries.listByPaymentHash(paymentHash).executeAsList().filterIsInstance<LightningOutgoingPayment>()
        }
    }
}