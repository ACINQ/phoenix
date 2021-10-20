/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.db

import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.channel.ChannelException
import fr.acinq.lightning.db.*
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.lightning.wire.FailureMessage
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.db.payments.*
import fracinqphoenixdb.Incoming_payments
import fracinqphoenixdb.Outgoing_payment_parts
import fracinqphoenixdb.Outgoing_payments
import fracinqphoenixdb.Payments_metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class SqlitePaymentsDb(driver: SqlDriver) : PaymentsDb {

    private val database = PaymentsDatabase(
        driver = driver,
        outgoing_payment_partsAdapter = Outgoing_payment_parts.Adapter(
            part_routeAdapter = OutgoingQueries.hopDescAdapter,
            part_status_typeAdapter = EnumColumnAdapter()
        ),
        outgoing_paymentsAdapter = Outgoing_payments.Adapter(
            status_typeAdapter = EnumColumnAdapter(),
            details_typeAdapter = EnumColumnAdapter()
        ),
        incoming_paymentsAdapter = Incoming_payments.Adapter(
            origin_typeAdapter = EnumColumnAdapter(),
            received_with_typeAdapter = EnumColumnAdapter()
        ),
        payments_metadataAdapter = Payments_metadata.Adapter(
            lnurl_base_typeAdapter = EnumColumnAdapter(),
            lnurl_metadata_typeAdapter = EnumColumnAdapter(),
            lnurl_successAction_typeAdapter = EnumColumnAdapter()
        )
    )
    private val inQueries = IncomingQueries(database)
    internal val outQueries = OutgoingQueries(database)
    private val aggrQueries = database.aggregatedQueriesQueries
    private val metaQueries = MetadataQueries(database)

    val cloudKitDb = makeCloudKitDb(database)

    private var metadataQueue = MutableStateFlow(mapOf<WalletPaymentId, WalletPaymentMetadataRow>())

    override suspend fun addOutgoingParts(parentId: UUID, parts: List<OutgoingPayment.Part>) {
        withContext(Dispatchers.Default) {
            outQueries.addOutgoingParts(parentId, parts)
        }
    }

    override suspend fun addOutgoingPayment(outgoingPayment: OutgoingPayment) {
        val paymentId = outgoingPayment.walletPaymentId()
        val metadataRow = dequeueMetadata(paymentId)
        withContext(Dispatchers.Default) {
            database.transaction {
                outQueries.addOutgoingPayment(outgoingPayment)
                // If there is pending metadata for the given payment,
                // add it to the database now, within the same atomic database transaction.
                metadataRow?.let { row ->
                    metaQueries.addMetadata(paymentId, row)
                }
            }

        }
    }

    override suspend fun completeOutgoingPayment(id: UUID, completed: OutgoingPayment.Status.Completed) {
        withContext(Dispatchers.Default) {
            outQueries.completeOutgoingPayment(id, completed)
        }
    }

    override suspend fun updateOutgoingPart(partId: UUID, preimage: ByteVector32, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.updateOutgoingPart(partId, preimage, completedAt)
        }
    }

    override suspend fun updateOutgoingPart(partId: UUID, failure: Either<ChannelException, FailureMessage>, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.updateOutgoingPart(partId, failure, completedAt)
        }
    }

    override suspend fun getOutgoingPart(partId: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPart(partId)
        }
    }

    override suspend fun getOutgoingPayment(id: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPayment(id)
        }
    }

    suspend fun getOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<OutgoingPayment, WalletPaymentMetadata?>? {
        return withContext(Dispatchers.Default) {
            database.transactionWithResult {
                outQueries.getOutgoingPayment(id)?.let { payment ->
                    val metadata = metaQueries.getMetadata(
                        id = WalletPaymentId.OutgoingPaymentId(id),
                        options = options
                    )
                    Pair(payment, metadata)
                }
            }
        }
    }

    // ---- list outgoing

    override suspend fun listOutgoingPayments(paymentHash: ByteVector32): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            outQueries.listOutgoingPayments(paymentHash)
        }
    }

    @Deprecated("This method uses offset and has bad performances, use seek method instead when possible")
    override suspend fun listOutgoingPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            outQueries.listOutgoingPayments(count, skip)
        }
    }

    // ---- incoming payments

    override suspend fun addIncomingPayment(
        preimage: ByteVector32,
        origin: IncomingPayment.Origin,
        createdAt: Long
    ) {
        val paymentHash = Crypto.sha256(preimage).toByteVector32()
        val paymentId = WalletPaymentId.IncomingPaymentId(paymentHash)
        val metadataRow = dequeueMetadata(paymentId)
        withContext(Dispatchers.Default) {
            database.transaction {
                inQueries.addIncomingPayment(
                    preimage = preimage,
                    paymentHash = paymentHash,
                    origin = origin,
                    createdAt = createdAt
                )
                // If there is pending metadata for the given payment,
                // add it to the database now, within the same atomic database transaction.
                metadataRow?.let { row ->
                    metaQueries.addMetadata(paymentId, row)
                }
            }
        }
    }

    override suspend fun receivePayment(paymentHash: ByteVector32, receivedWith: Set<IncomingPayment.ReceivedWith>, receivedAt: Long) {
        withContext(Dispatchers.Default) {
            inQueries.receivePayment(paymentHash, receivedWith, receivedAt)
        }
    }

    override suspend fun addAndReceivePayment(preimage: ByteVector32, origin: IncomingPayment.Origin, receivedWith: Set<IncomingPayment.ReceivedWith>, createdAt: Long, receivedAt: Long) {
        withContext(Dispatchers.Default) {
            inQueries.addAndReceivePayment(preimage, origin, receivedWith, createdAt, receivedAt)
        }
    }

    override suspend fun updateNewChannelReceivedWithChannelId(paymentHash: ByteVector32, channelId: ByteVector32) {
        withContext(Dispatchers.Default) {
            inQueries.updateNewChannelReceivedWithChannelId(paymentHash, channelId)
        }
    }

    override suspend fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return withContext(Dispatchers.Default) {
            inQueries.getIncomingPayment(paymentHash)
        }
    }

    suspend fun getIncomingPayment(
        paymentHash: ByteVector32,
        options: WalletPaymentFetchOptions
    ): Pair<IncomingPayment, WalletPaymentMetadata?>? {
        return withContext(Dispatchers.Default) {
            database.transactionWithResult {
                inQueries.getIncomingPayment(paymentHash)?.let { payment ->
                    val metadata = metaQueries.getMetadata(
                        id = WalletPaymentId.IncomingPaymentId(paymentHash),
                        options = options
                    )
                    Pair(payment, metadata)
                }
            }
        }
    }

    override suspend fun listReceivedPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<IncomingPayment> {
        return withContext(Dispatchers.Default) {
            inQueries.listReceivedPayments(count, skip)
        }
    }

    // ---- list ALL payments

    suspend fun listPaymentsCount(): Long {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsCount(::allPaymentsCountMapper).executeAsList().first()
        }
    }

    suspend fun listPaymentsCountFlow(): Flow<Long> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsCount(::allPaymentsCountMapper).asFlow().mapToOne()
        }
    }

    suspend fun listPaymentsOrder(count: Int, skip: Int): List<WalletPaymentOrderRow> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsOrder(
                limit = count.toLong(),
                offset = skip.toLong(),
                mapper = ::allPaymentsOrderMapper
            ).executeAsList()
        }
    }

    suspend fun listPaymentsOrderFlow(count: Int, skip: Int): Flow<List<WalletPaymentOrderRow>> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsOrder(
                limit = count.toLong(),
                offset = skip.toLong(),
                mapper = ::allPaymentsOrderMapper
            ).asFlow().mapToList()
        }
    }

    override suspend fun listPayments(
        count: Int,
        skip: Int,
        filters: Set<PaymentTypeFilter>
    ): List<WalletPayment> = throw NotImplementedError("Use listPaymentsOrderFlow instead")

    internal fun enqueueMetadata(row: WalletPaymentMetadataRow, id: WalletPaymentId) {
        val oldMap = metadataQueue.value
        val newMap = oldMap + (id to row)
        metadataQueue.value = newMap
    }

    private fun dequeueMetadata(id: WalletPaymentId): WalletPaymentMetadataRow? {
        val oldMap = metadataQueue.value
        val newMap = oldMap - id
        metadataQueue.value = newMap
        return oldMap[id]
    }

    companion object {
        private fun allPaymentsCountMapper(
            result: Long?
        ): Long {
            return result ?: 0
        }

        private fun allPaymentsOrderMapper(
            direction: String,
            outgoing_payment_id: String?,
            incoming_payment_id: ByteArray?,
            @Suppress("UNUSED_PARAMETER") created_at: Long,
            @Suppress("UNUSED_PARAMETER") completed_at: Long?
        ): WalletPaymentOrderRow {
            val paymentId = when (direction.lowercase()) {
                "outgoing" -> WalletPaymentId.OutgoingPaymentId(
                    id = UUID.fromString(outgoing_payment_id!!)
                )
                "incoming" -> WalletPaymentId.IncomingPaymentId(
                    paymentHash = ByteVector32(incoming_payment_id!!)
                )
                else -> throw UnhandledDirection(direction)
            }
            return WalletPaymentOrderRow(
                id = paymentId,
                createdAt = created_at,
                completedAt = completed_at
            )
        }
    }
}

class OutgoingPaymentPartNotFound(partId: UUID) : RuntimeException("could not find outgoing payment part with part_id=$partId")
class IncomingPaymentNotFound(paymentHash: ByteVector32) : RuntimeException("missing payment for payment_hash=$paymentHash")
class UnhandledDirection(direction: String) : RuntimeException("unhandled direction=$direction")

data class WalletPaymentOrderRow(
    val id: WalletPaymentId,
    val createdAt: Long,
    val completedAt: Long?
) {
    /// Returns a unique identifier, suitable for use in a HashMap.
    /// Form is:
    /// - "outgoing|id|createdAt|completedAt"
    /// - "incoming|paymentHash|createdAt|completedAt"
    ///
    val identifier: String get() {
        return "${this.staleIdentifierPrefix}${completedAt?.toString() ?: "null"}"
    }

    /// Returns a prefix that can be used to detect older (stale) versions of the row.
    /// Form is:
    /// - "outgoing|id|createdAt|"
    /// - "incoming|paymentHash|createdAt|"
    ///
    val staleIdentifierPrefix: String get() {
        return "${id.identifier}|${createdAt}|"
    }
}

/// Implement this function to execute platform specific code when a payment completes.
/// For example, on iOS this is used to enqueue the (encrypted) payment for upload to CloudKit.
///
/// Important:
///   This function is invoked inside the same transaction used to add/modify the row.
///   This means any database operations performed in this function are atomic,
///   with respect to the referenced row.
///
expect fun didCompleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase)

/// Implemented on Apple platforms with support for CloudKit.
///
expect fun makeCloudKitDb(database: PaymentsDatabase): CloudKitInterface?
