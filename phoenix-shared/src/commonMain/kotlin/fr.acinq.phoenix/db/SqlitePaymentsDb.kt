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
import fr.acinq.lightning.utils.ensureNeverFrozen
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.lightning.wire.FailureMessage
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.managers.CurrencyManager
import fracinqphoenixdb.Incoming_payments
import fracinqphoenixdb.Outgoing_payment_parts
import fracinqphoenixdb.Outgoing_payments
import fracinqphoenixdb.Payments_metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class SqlitePaymentsDb(
    driver: SqlDriver,
    private val currencyManager: CurrencyManager? = null
) : PaymentsDb {

    init {
        ensureNeverFrozen() // Crashes when attempting to freeze CurrencyManager sub-graph
    }

    /**
     * Within `SqlitePaymentsDb`, we are using background threads.
     * ```
     * withContext(Dispatchers.Default) {
     *     // code running in background thread here...
     * }
     * ```
     * Recall that kotlin-native has that horrible unworkable memory management "feature",
     * where it attempts to freeze the object sub-graph of any object moved between threads.
     * This means that:
     * ```
     * withContext(Dispatchers.Default) {
     *     // Any object we access within this lambda will be frozen.
     *     localVariable.function() // implicitly accesses this...
     *     // Thus kotlin-native will freeze `this`, and ALL local variables too.
     *     // Which results in a crash.
     * }
     * ```
     * There are various workarounds for this problem. In the long-term, the preferred solution
     * is to disable kotlin's unworkable ridiculous policy. Which is possible in v1.6.0.
     * But for the time being, the simplest solution is:
     * ```
     * val variable = localVariable
     * withContext(Dispatchers.Default) {
     *     variable.function() // only freeze variable, and not `this`
     * }
     * ```
     */
    private class Properties(driver: SqlDriver) {
        val database = PaymentsDatabase(
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
        val inQueries = IncomingQueries(database)
        val outQueries = OutgoingQueries(database)
        val aggrQueries = database.aggregatedQueriesQueries
        val metaQueries = MetadataQueries(database)

        val cloudKitDb = makeCloudKitDb(database)
    }
    private val _doNotFreezeMe = Properties(driver)

    fun getCloudKitDb(): CloudKitInterface? {
        return _doNotFreezeMe.cloudKitDb
    }

    private var metadataQueue = MutableStateFlow(mapOf<WalletPaymentId, WalletPaymentMetadataRow>())

    override suspend fun addOutgoingParts(
        parentId: UUID,
        parts: List<OutgoingPayment.Part>
    ) {
        val outQueries = _doNotFreezeMe.outQueries

        withContext(Dispatchers.Default) {
            outQueries.addOutgoingParts(parentId, parts)
        }
    }

    override suspend fun addOutgoingPayment(
        outgoingPayment: OutgoingPayment
    ) {
        val database = _doNotFreezeMe.database
        val outQueries = _doNotFreezeMe.outQueries
        val metaQueries = _doNotFreezeMe.metaQueries

        val paymentId = outgoingPayment.walletPaymentId()
        val metadataRow = dequeueMetadata(paymentId)

        withContext(Dispatchers.Default) {
            database.transaction {
                outQueries.addOutgoingPayment(outgoingPayment)
                // Add associated metadata within the same atomic database transaction.
                if (!metadataRow.isEmpty()) {
                    metaQueries.addMetadata(paymentId, metadataRow)
                }
            }

        }
    }

    override suspend fun completeOutgoingPayment(
        id: UUID,
        completed: OutgoingPayment.Status.Completed
    ) {
        val outQueries = _doNotFreezeMe.outQueries

        withContext(Dispatchers.Default) {
            outQueries.completeOutgoingPayment(id, completed)
        }
    }

    override suspend fun updateOutgoingPart(
        partId: UUID,
        preimage: ByteVector32,
        completedAt: Long
    ) {
        val outQueries = _doNotFreezeMe.outQueries

        withContext(Dispatchers.Default) {
            outQueries.updateOutgoingPart(partId, preimage, completedAt)
        }
    }

    override suspend fun updateOutgoingPart(
        partId: UUID,
        failure: Either<ChannelException, FailureMessage>,
        completedAt: Long
    ) {
        val outQueries = _doNotFreezeMe.outQueries

        withContext(Dispatchers.Default) {
            outQueries.updateOutgoingPart(partId, failure, completedAt)
        }
    }

    override suspend fun getOutgoingPart(
        partId: UUID
    ): OutgoingPayment? {
        val outQueries = _doNotFreezeMe.outQueries

        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPart(partId)
        }
    }

    override suspend fun getOutgoingPayment(
        id: UUID
    ): OutgoingPayment? {
        val outQueries = _doNotFreezeMe.outQueries

        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPayment(id)
        }
    }

    suspend fun getOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<OutgoingPayment, WalletPaymentMetadata?>? {
        val database = _doNotFreezeMe.database
        val outQueries = _doNotFreezeMe.outQueries
        val metaQueries = _doNotFreezeMe.metaQueries

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

    override suspend fun listOutgoingPayments(
        paymentHash: ByteVector32
    ): List<OutgoingPayment> {
        val outQueries = _doNotFreezeMe.outQueries

        return withContext(Dispatchers.Default) {
            outQueries.listOutgoingPayments(paymentHash)
        }
    }

    @Deprecated("This method uses offset and has bad performances, use seek method instead when possible")
    override suspend fun listOutgoingPayments(
        count: Int,
        skip: Int,
        filters: Set<PaymentTypeFilter>
    ): List<OutgoingPayment> {
        val outQueries = _doNotFreezeMe.outQueries

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
        val database = _doNotFreezeMe.database
        val inQueries = _doNotFreezeMe.inQueries
        val metaQueries = _doNotFreezeMe.metaQueries

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
                // Add associated metadata within the same atomic database transaction.
                if (!metadataRow.isEmpty()) {
                    metaQueries.addMetadata(paymentId, metadataRow)
                }
            }
        }
    }

    override suspend fun receivePayment(
        paymentHash: ByteVector32,
        receivedWith: Set<IncomingPayment.ReceivedWith>,
        receivedAt: Long
    ) {
        val database = _doNotFreezeMe.database
        val inQueries = _doNotFreezeMe.inQueries

        withContext(Dispatchers.Default) {
            database.transaction {
                inQueries.receivePayment(paymentHash, receivedWith, receivedAt)
            }
        }
    }

    override suspend fun addAndReceivePayment(
        preimage: ByteVector32,
        origin: IncomingPayment.Origin,
        receivedWith: Set<IncomingPayment.ReceivedWith>,
        createdAt: Long,
        receivedAt: Long
    ) {
        val database = _doNotFreezeMe.database
        val inQueries = _doNotFreezeMe.inQueries
        val metaQueries = _doNotFreezeMe.metaQueries

        val paymentHash = Crypto.sha256(preimage).toByteVector32()
        val paymentId = WalletPaymentId.IncomingPaymentId(paymentHash)
        val metadataRow = dequeueMetadata(paymentId)

        withContext(Dispatchers.Default) {
            database.transaction {
                inQueries.addAndReceivePayment(
                    preimage,
                    origin,
                    receivedWith,
                    createdAt,
                    receivedAt
                )
                // Add associated metadata within the same atomic database transaction.
                if (!metadataRow.isEmpty()) {
                    metaQueries.addMetadata(paymentId, metadataRow)
                }
            }
        }
    }

    override suspend fun updateNewChannelReceivedWithChannelId(
        paymentHash: ByteVector32,
        channelId: ByteVector32
    ) {
        val inQueries = _doNotFreezeMe.inQueries

        withContext(Dispatchers.Default) {
            inQueries.updateNewChannelReceivedWithChannelId(paymentHash, channelId)
        }
    }

    override suspend fun getIncomingPayment(
        paymentHash: ByteVector32
    ): IncomingPayment? {
        val inQueries = _doNotFreezeMe.inQueries

        return withContext(Dispatchers.Default) {
            inQueries.getIncomingPayment(paymentHash)
        }
    }

    suspend fun getIncomingPayment(
        paymentHash: ByteVector32,
        options: WalletPaymentFetchOptions
    ): Pair<IncomingPayment, WalletPaymentMetadata?>? {
        val database = _doNotFreezeMe.database
        val inQueries = _doNotFreezeMe.inQueries
        val metaQueries = _doNotFreezeMe.metaQueries

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

    override suspend fun listReceivedPayments(
        count: Int,
        skip: Int,
        filters: Set<PaymentTypeFilter>
    ): List<IncomingPayment> {
        val inQueries = _doNotFreezeMe.inQueries

        return withContext(Dispatchers.Default) {
            inQueries.listReceivedPayments(count, skip)
        }
    }

    // ---- list ALL payments

    suspend fun listPaymentsCount(): Long {
        val aggrQueries = _doNotFreezeMe.aggrQueries

        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsCount(::allPaymentsCountMapper).executeAsList().first()
        }
    }

    suspend fun listPaymentsCountFlow(): Flow<Long> {
        val aggrQueries = _doNotFreezeMe.aggrQueries

        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsCount(::allPaymentsCountMapper).asFlow().mapToOne()
        }
    }

    suspend fun listPaymentsOrder(
        count: Int,
        skip: Int
    ): List<WalletPaymentOrderRow> {
        val aggrQueries = _doNotFreezeMe.aggrQueries

        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsOrder(
                limit = count.toLong(),
                offset = skip.toLong(),
                mapper = ::allPaymentsOrderMapper
            ).executeAsList()
        }
    }

    suspend fun listPaymentsOrderFlow(
        count: Int,
        skip: Int
    ): Flow<List<WalletPaymentOrderRow>> {
        val aggrQueries = _doNotFreezeMe.aggrQueries

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

    /**
     * The lightning-kmp layer triggers the addition of a payment to the database.
     * But sometimes there is associated metadata that we want to include,
     * and we would like to write it to the database within the same transaction.
     * So we have a system to enqueue/dequeue associated metadata.
     */
    internal fun enqueueMetadata(row: WalletPaymentMetadataRow, id: WalletPaymentId) {
        val oldMap = metadataQueue.value
        val newMap = oldMap + (id to row)
        metadataQueue.value = newMap
    }

    /**
     * Returns any enqueued metadata, and also appends the current fiat exchange rate.
     */
    private fun dequeueMetadata(id: WalletPaymentId): WalletPaymentMetadataRow {
        val oldMap = metadataQueue.value
        val newMap = oldMap - id
        metadataQueue.value = newMap

        val row = oldMap[id] ?: WalletPaymentMetadataRow()

        // Append the current exchange rate, unless it was explicitly set earlier.
        return if (row.original_fiat != null) {
            row
        } else {
            row.copy(original_fiat = currencyManager?.calculateOriginalFiat()?.let {
                Pair(it.fiatCurrency.name, it.price)
            })
        }
    }

    suspend fun updateMetadata(
        id: WalletPaymentId,
        userDescription: String?,
        userNotes: String?
    ) {
        val metaQueries = _doNotFreezeMe.metaQueries

        withContext(Dispatchers.Default) {
            metaQueries.updateUserInfo(
                id = id,
                userDescription = userDescription,
                userNotes = userNotes
            )
        }
    }

    suspend fun deletePayment(
        paymentId: WalletPaymentId
    ) {
        val database = _doNotFreezeMe.database

        withContext(Dispatchers.Default) {
            database.transaction {
                when (paymentId) {
                    is WalletPaymentId.IncomingPaymentId -> {
                        database.incomingPaymentsQueries.delete(
                            payment_hash = paymentId.paymentHash.toByteArray()
                        )
                    }
                    is WalletPaymentId.OutgoingPaymentId -> {
                        database.outgoingPaymentsQueries.deletePaymentParts(
                            part_parent_id = paymentId.dbId
                        )
                        database.outgoingPaymentsQueries.deletePayment(
                            id = paymentId.dbId
                        )
                    }
                }
                didDeleteWalletPayment(paymentId, database)
            }
        }
    }

    companion object {
        private fun allPaymentsCountMapper(
            result: Long?
        ): Long {
            return result ?: 0
        }

        private fun allPaymentsOrderMapper(
            type: Long,
            id: String,
            created_at: Long,
            completed_at: Long?,
            metadata_modified_at: Long?
        ): WalletPaymentOrderRow {
            val paymentId = when (type) {
                WalletPaymentId.DbType.OUTGOING.value -> {
                    WalletPaymentId.OutgoingPaymentId.fromString(id)
                }
                WalletPaymentId.DbType.INCOMING.value -> {
                    WalletPaymentId.IncomingPaymentId.fromString(id)
                }
                else -> throw UnhandledPaymentType(type)
            }
            return WalletPaymentOrderRow(
                id = paymentId,
                createdAt = created_at,
                completedAt = completed_at,
                metadataModifiedAt = metadata_modified_at
            )
        }
    }
}

class OutgoingPaymentPartNotFound(partId: UUID) : RuntimeException("could not find outgoing payment part with part_id=$partId")
class IncomingPaymentNotFound(paymentHash: ByteVector32) : RuntimeException("missing payment for payment_hash=$paymentHash")
class UnhandledPaymentType(type: Long) : RuntimeException("unhandled payment type=$type")

data class WalletPaymentOrderRow(
    val id: WalletPaymentId,
    val createdAt: Long,
    val completedAt: Long?,
    val metadataModifiedAt: Long?
) {
    /**
     * Returns a unique identifier, suitable for use in a HashMap.
     * Form is:
     * - "outgoing|id|createdAt|completedAt|metadataModifiedAt"
     * - "incoming|paymentHash|createdAt|completedAt|metadataModifiedAt"
     */
    val identifier: String get() {
        return this.staleIdentifierPrefix +
                (completedAt?.toString() ?: "null") + "|" +
                (metadataModifiedAt?.toString() ?: "null")
    }

    /**
     * Returns a prefix that can be used to detect older (stale) versions of the row.
     * Form is:
     * - "outgoing|id|createdAt|"
     * - "incoming|paymentHash|createdAt|"
     */
    val staleIdentifierPrefix: String get() {
        return "${id.identifier}|${createdAt}|"
    }
}

/**
 * Implement this function to execute platform specific code when a payment completes.
 * For example, on iOS this is used to enqueue the (encrypted) payment for upload to CloudKit.
 *
 * This function is invoked inside the same transaction used to add/modify the row.
 * This means any database operations performed in this function are atomic,
 * with respect to the referenced row.
 */
expect fun didCompleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase)

/**
 * Implement this function to execute platform specific code when a payment is deleted.
 * For example, on iOS this is used to enqueue an operation to delete the payment from CloudKit.
 */
expect fun didDeleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase)

/**
 * Implement this function to execute platform specific code when a payment's metdata is updated.
 * For example: the user modifies the payment description.
 *
 * This function is invoked inside the same transaction used to add/modify the row.
 * This means any database operations performed in this function are atomic,
 * with respect to the referenced row.
 */
expect fun didUpdateWalletPaymentMetadata(id: WalletPaymentId, database: PaymentsDatabase)

/**
 * Implemented on Apple platforms with support for CloudKit.
 */
expect fun makeCloudKitDb(database: PaymentsDatabase): CloudKitInterface?
