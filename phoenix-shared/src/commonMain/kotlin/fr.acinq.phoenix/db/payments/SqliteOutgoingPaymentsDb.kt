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
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
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
                    sent_at = null
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
            database.transaction {
                when (outgoingPayment) {
                    is LightningOutgoingPayment -> {
                        database.paymentsOutgoingQueries.insert(
                            id = outgoingPayment.id,
                            payment_hash = outgoingPayment.paymentHash,
                            tx_id = null,
                            created_at = outgoingPayment.createdAt,
                            completed_at = outgoingPayment.completedAt,
                            // outgoing lightning payments can fail, the sent_at timestamp is only set if the payment was success
                            sent_at = if (outgoingPayment.status is LightningOutgoingPayment.Status.Succeeded) outgoingPayment.completedAt else null,
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
                            sent_at = outgoingPayment.completedAt,
                            data_ = outgoingPayment
                        )
                        database.onChainTransactionsQueries.insert(
                            payment_id = outgoingPayment.id,
                            tx_id = outgoingPayment.txId
                        )
                    }
                }
                didSaveWalletPayment(outgoingPayment.id, database)
            }
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
                    completed_at = status.completedAt,
                    sent_at = if (status is LightningOutgoingPayment.Status.Succeeded) status.completedAt else null,
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
                    sent_at = null
                )
                didSaveWalletPayment(parentId, database)
            }
        }
    }

    override suspend fun getInboundLiquidityPurchase(fundingTxId: TxId): InboundLiquidityOutgoingPayment? {
        return withContext(Dispatchers.Default) {
            database.paymentsOutgoingQueries.listByTxId(fundingTxId).executeAsList()
                .filterIsInstance<InboundLiquidityOutgoingPayment>()
                .firstOrNull()
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
                val paymentId = database.paymentsOutgoingQueries.getParentId(partId).executeAsOneOrNull()!!
                database.paymentsOutgoingQueries.get(paymentId).executeAsOneOrNull() as? LightningOutgoingPayment
            }
        }
    }

    override suspend fun listLightningOutgoingPayments(paymentHash: ByteVector32): List<LightningOutgoingPayment> {
        return withContext(Dispatchers.Default) {
            database.paymentsOutgoingQueries.listByPaymentHash(paymentHash).executeAsList().filterIsInstance<LightningOutgoingPayment>()
        }
    }
}