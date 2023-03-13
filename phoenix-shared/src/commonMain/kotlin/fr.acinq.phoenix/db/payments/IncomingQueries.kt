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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.*
import fr.acinq.phoenix.utils.extensions.execute
import fr.acinq.phoenix.utils.extensions.QueryExecution

class IncomingQueries(private val database: PaymentsDatabase) {

    private val queries = database.incomingPaymentsQueries

    /**
     * @return The paymentHash (= SHA256(preimage))
     */
    fun addIncomingPayment(
        preimage: ByteVector32,
        origin: IncomingPayment.Origin,
        createdAt: Long
    ): ByteVector32 {
        val paymentHash = Crypto.sha256(preimage).toByteVector32()
        addIncomingPayment(
            preimage = preimage,
            paymentHash = paymentHash,
            origin = origin,
            createdAt = createdAt
        )
        return paymentHash
    }

    fun addIncomingPayment(
        preimage: ByteVector32,
        paymentHash: ByteVector32,
        origin: IncomingPayment.Origin,
        createdAt: Long
    ) {
        val (originType, originData) = origin.mapToDb()
        queries.insert(
            payment_hash = paymentHash.toByteArray(),
            preimage = preimage.toByteArray(),
            origin_type = originType,
            origin_blob = originData,
            created_at = createdAt
        )
    }

    fun receivePayment(
        paymentHash: ByteVector32,
        receivedWith: Set<IncomingPayment.ReceivedWith>,
        receivedAt: Long
    ) {
        database.transaction {
            val paymentInDb = queries.get(
                payment_hash = paymentHash.toByteArray(),
                mapper = ::mapIncomingPayment
            ).executeAsOneOrNull() ?: throw IncomingPaymentNotFound(paymentHash)
            val existingReceivedWith = paymentInDb.received?.receivedWith ?: emptySet()
            val newReceivedWith = existingReceivedWith + receivedWith
            val (receivedWithType, receivedWithBlob) = newReceivedWith.mapToDb() ?: (null to null)
            val receivedWithNewChannel = newReceivedWith.any {
                it is IncomingPayment.ReceivedWith.NewChannel
            }
            queries.updateReceived(
                received_at = receivedAt,
                received_with_type = receivedWithType,
                received_with_blob = receivedWithBlob,
                received_with_new_channel = if (receivedWithNewChannel) 1 else 0,
                payment_hash = paymentHash.toByteArray()
            )
            didCompleteWalletPayment(WalletPaymentId.IncomingPaymentId(paymentHash), database)
        }
    }

    fun updateNewChannelReceivedWithChannelId(paymentHash: ByteVector32, channelId: ByteVector32) {
        database.transaction {
            val paymentInDb = queries.get(
                payment_hash = paymentHash.toByteArray(),
                mapper = ::mapIncomingPayment
            ).executeAsOneOrNull() ?: throw IncomingPaymentNotFound(paymentHash)
            val newReceivedWith = paymentInDb.received?.receivedWith?.map {
                when (it) {
                    is IncomingPayment.ReceivedWith.NewChannel -> it.copy(channelId = channelId)
                    else -> it
                }
            }?.toSet() ?: emptySet()
            val (receivedWithType, receivedWithBlob) = newReceivedWith.mapToDb() ?: (null to null)
            val receivedWithNewChannel = newReceivedWith.any {
                it is IncomingPayment.ReceivedWith.NewChannel
            }
            queries.updateReceived(
                received_at = paymentInDb.received?.receivedAt,
                received_with_type = receivedWithType,
                received_with_blob = receivedWithBlob,
                received_with_new_channel = if (receivedWithNewChannel) 1 else 0,
                payment_hash = paymentHash.toByteArray()
            )
            didCompleteWalletPayment(WalletPaymentId.IncomingPaymentId(paymentHash), database)
        }
    }

    fun findNewChannelPayment(channelId: ByteVector32): ByteVector32? {
        return database.transactionWithResult {
            val query = queries.listNewChannel(mapper = ::mapListNewChannel)
            var match: ListNewChannelRow? = null
            query.execute { row ->
                row.received?.receivedWith?.firstOrNull() {
                    it is IncomingPayment.ReceivedWith.NewChannel && it.channelId == channelId
                }?.let {
                    match = row
                    QueryExecution.Stop
                } ?: QueryExecution.Continue
            }
            match?.paymentHash
        }
    }

    fun updateNewChannelConfirmed(paymentHash: ByteVector32, receivedAt: Long) {
        database.transaction {
            val paymentInDb = queries.get(
                payment_hash = paymentHash.toByteArray(),
                mapper = ::mapIncomingPayment
            ).executeAsOneOrNull() ?: throw IncomingPaymentNotFound(paymentHash)
            val newReceivedWith = paymentInDb.received?.receivedWith?.map {
                when (it) {
                    is IncomingPayment.ReceivedWith.NewChannel -> it.copy(confirmed = true)
                    else -> it
                }
            }?.toSet() ?: emptySet()
            val (receivedWithType, receivedWithBlob) = newReceivedWith.mapToDb() ?: (null to null)
            val receivedWithNewChannel = newReceivedWith.any {
                it is IncomingPayment.ReceivedWith.NewChannel
            }
            queries.updateReceived(
                received_at = receivedAt,
                received_with_type = receivedWithType,
                received_with_blob = receivedWithBlob,
                received_with_new_channel = if (receivedWithNewChannel) 1 else 0,
                payment_hash = paymentHash.toByteArray()
            )
            didCompleteWalletPayment(WalletPaymentId.IncomingPaymentId(paymentHash), database)
        }
    }

    fun addAndReceivePayment(
        preimage: ByteVector32,
        origin: IncomingPayment.Origin,
        receivedWith: Set<IncomingPayment.ReceivedWith>,
        createdAt: Long,
        receivedAt: Long
    ) {
        database.transaction {
            val paymentHash = Crypto.sha256(preimage).toByteVector32()
            val (originType, originData) = origin.mapToDb()
            val (receivedWithType, receivedWithBlob) = receivedWith.mapToDb() ?: (null to null)
            val receivedWithNewChannel = receivedWith.any {
                it is IncomingPayment.ReceivedWith.NewChannel
            }
            queries.insertAndReceive(
                payment_hash = paymentHash.toByteArray(),
                preimage = preimage.toByteArray(),
                origin_type = originType,
                origin_blob = originData,
                received_at = receivedAt,
                received_with_type = receivedWithType,
                received_with_blob = receivedWithBlob,
                received_with_new_channel = if (receivedWithNewChannel) 1 else 0,
                created_at = createdAt
            )
            didCompleteWalletPayment(WalletPaymentId.IncomingPaymentId(paymentHash), database)
        }
    }

    fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return queries.get(payment_hash = paymentHash.toByteArray(), ::mapIncomingPayment).executeAsOneOrNull()
    }

    fun getOldestReceivedDate(): Long? {
        return queries.getOldestReceivedDate().executeAsOneOrNull()
    }

    fun listExpiredPayments(fromCreatedAt: Long, toCreatedAt: Long): List<IncomingPayment> {
        return queries.listAllWithin(fromCreatedAt, toCreatedAt, ::mapIncomingPayment).executeAsList().filter {
            it.received == null
        }
    }

    /** Try to delete an incoming payment ; return true if an element was deleted, false otherwise. */
    fun deleteIncomingPayment(paymentHash: ByteVector32): Boolean {
        return database.transactionWithResult {
            queries.delete(payment_hash = paymentHash.toByteArray())
            queries.changes().executeAsOne() != 0L
        }
    }

    companion object {
        fun mapIncomingPayment(
            @Suppress("UNUSED_PARAMETER") payment_hash: ByteArray,
            preimage: ByteArray,
            created_at: Long,
            origin_type: IncomingOriginTypeVersion,
            origin_blob: ByteArray,
            received_amount_msat: Long?,
            received_at: Long?,
            received_with_type: IncomingReceivedWithTypeVersion?,
            received_with_blob: ByteArray?,
        ): IncomingPayment {
            return IncomingPayment(
                preimage = ByteVector32(preimage),
                origin = IncomingOriginData.deserialize(origin_type, origin_blob),
                received = mapIncomingReceived(received_amount_msat, received_at, received_with_type, received_with_blob, origin_type),
                createdAt = created_at
            )
        }

        private fun mapIncomingReceived(
            received_amount_msat: Long?,
            received_at: Long?,
            received_with_type: IncomingReceivedWithTypeVersion?,
            received_with_blob: ByteArray?,
            origin_type: IncomingOriginTypeVersion
        ): IncomingPayment.Received? {
            return when {
                received_at == null && received_with_type == null && received_with_blob == null -> null
                received_at != null && received_with_type != null && received_with_blob != null -> {
                    IncomingPayment.Received(
                        receivedWith = IncomingReceivedWithData.deserialize(received_with_type, received_with_blob, received_amount_msat?.msat, origin_type),
                        receivedAt = received_at
                    )
                }
                else -> throw UnreadableIncomingReceivedWith(received_at, received_with_type, received_with_blob)
            }
        }

        private fun mapListNewChannel(
            payment_hash: ByteArray,
            received_amount_msat: Long?,
            received_at: Long?,
            received_with_type: IncomingReceivedWithTypeVersion?,
            received_with_blob: ByteArray?,
            origin_type: IncomingOriginTypeVersion
        ): ListNewChannelRow {
            return ListNewChannelRow(
                paymentHash = payment_hash.toByteVector32(),
                received = mapIncomingReceived(
                    received_amount_msat = received_amount_msat,
                    received_at = received_at,
                    received_with_type = received_with_type,
                    received_with_blob = received_with_blob,
                    origin_type = origin_type
                )
            )
        }
    }
}

data class ListNewChannelRow(
    val paymentHash: ByteVector32,
    val received: IncomingPayment.Received?
)

class UnreadableIncomingReceivedWith(receivedAt: Long?, receivedWithTypeVersion: IncomingReceivedWithTypeVersion?, receivedWithBlob: ByteArray?) :
    RuntimeException("unreadable received with data [ receivedAt=$receivedAt, receivedWithTypeVersion=$receivedWithTypeVersion, receivedWithBlob=$receivedWithBlob ]")