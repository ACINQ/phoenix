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
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.utils.*
import fr.acinq.lightning.wire.FailureMessage
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.managers.CurrencyManager
import fracinqphoenixdb.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class SqlitePaymentsDb(
    loggerFactory: LoggerFactory,
    private val driver: SqlDriver,
    private val currencyManager: CurrencyManager? = null
) : PaymentsDb {

    private val log = newLogger(loggerFactory)

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
        ),
        outgoing_payment_closing_tx_partsAdapter = Outgoing_payment_closing_tx_parts.Adapter(
            part_closing_info_typeAdapter = EnumColumnAdapter()
        )
    )

    internal val inQueries = IncomingQueries(database)
    internal val outQueries = OutgoingQueries(database)
    internal val spliceOutQueries = SpliceOutgoingQueries(database)
    private val aggrQueries = database.aggregatedQueriesQueries
    private val metaQueries = MetadataQueries(database)

    private val cloudKitDb = makeCloudKitDb(database)

    private var metadataQueue = MutableStateFlow(mapOf<WalletPaymentId, WalletPaymentMetadataRow>())

    fun getCloudKitDb(): CloudKitInterface? {
        return cloudKitDb
    }

    override suspend fun addOutgoingLightningParts(
        parentId: UUID,
        parts: List<LightningOutgoingPayment.LightningPart>
    ) {
        withContext(Dispatchers.Default) {
            outQueries.addLightningParts(parentId, parts)
        }
    }

    override suspend fun addOutgoingPayment(
        outgoingPayment: OutgoingPayment
    ) {
        val paymentId = outgoingPayment.walletPaymentId()
        val metadataRow = dequeueMetadata(paymentId)

        withContext(Dispatchers.Default) {
            database.transaction {
                when (outgoingPayment) {
                    is LightningOutgoingPayment -> outQueries.addLightningOutgoingPayment(outgoingPayment)
                    is SpliceOutgoingPayment -> spliceOutQueries.addSpliceOutgoingPayment(outgoingPayment)
                }
                // Add associated metadata within the same atomic database transaction.
                if (!metadataRow.isEmpty()) {
                    metaQueries.addMetadata(paymentId, metadataRow)
                }
            }
        }
    }

    override suspend fun completeOutgoingPaymentForClosing(
        id: UUID,
        parts: List<LightningOutgoingPayment.ClosingTxPart>,
        completedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            outQueries.completePaymentForClosing(id, parts, LightningOutgoingPayment.Status.Completed.Succeeded.OnChain(completedAt))
        }
    }

    override suspend fun completeOutgoingPaymentOffchain(
        id: UUID,
        preimage: ByteVector32,
        completedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            outQueries.completePayment(id, LightningOutgoingPayment.Status.Completed.Succeeded.OffChain(preimage, completedAt))
        }
    }

    override suspend fun completeOutgoingPaymentOffchain(
        id: UUID,
        finalFailure: FinalFailure,
        completedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            outQueries.completePayment(id, LightningOutgoingPayment.Status.Completed.Failed(finalFailure, completedAt))
        }
    }

    override suspend fun completeOutgoingLightningPart(
        partId: UUID,
        preimage: ByteVector32,
        completedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            outQueries.updateLightningPart(partId, preimage, completedAt)
        }
    }

    override suspend fun completeOutgoingLightningPart(
        partId: UUID,
        failure: Either<ChannelException, FailureMessage>,
        completedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            outQueries.updateLightningPart(partId, failure, completedAt)
        }
    }

    /**
     * Should only be used for migrating legacy payments, where mapping old error messages to new
     * error types is not possible.
     */
    @Deprecated("only use this method for migrating legacy payments")
    suspend fun completeOutgoingLightningPartLegacy(
        partId: UUID,
        failedStatus: LightningOutgoingPayment.LightningPart.Status.Failed,
        completedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            val (statusType, statusData) = failedStatus.mapToDb()
            outQueries.database.outgoingPaymentsQueries.updateLightningPart(
                part_id = partId.toString(),
                part_status_type = statusType,
                part_status_blob = statusData,
                part_completed_at = completedAt
            )
        }
    }

    override suspend fun getOutgoingPaymentFromPartId(
        partId: UUID
    ): LightningOutgoingPayment? {

        return withContext(Dispatchers.Default) {
            outQueries.getPaymentFromPartId(partId)
        }
    }

    override suspend fun getOutgoingPayment(
        id: UUID
    ): OutgoingPayment? {

        return withContext(Dispatchers.Default) {
            outQueries.getPayment(id)
        }
    }

    suspend fun getLightningOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<LightningOutgoingPayment, WalletPaymentMetadata?>? {

        return withContext(Dispatchers.Default) {
            database.transactionWithResult {
                outQueries.getPayment(id)?.let { payment ->
                    val metadata = metaQueries.getMetadata(
                        id = WalletPaymentId.OutgoingPaymentId(id),
                        options = options
                    )
                    Pair(payment, metadata)
                }
            }
        }
    }

    suspend fun getSpliceOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<SpliceOutgoingPayment, WalletPaymentMetadata?>? {
        return withContext(Dispatchers.Default) {
            database.transactionWithResult {
                spliceOutQueries.getSpliceOutPayment(id)?.let { payment ->
                    val metadata = metaQueries.getMetadata(
                        id = WalletPaymentId.SpliceOutgoingPaymentId(id),
                        options = options
                    )
                    Pair(payment, metadata)
                }
            }
        }
    }

    // ---- list outgoing

    override suspend fun listLightningOutgoingPayments(
        paymentHash: ByteVector32
    ): List<LightningOutgoingPayment> {

        return withContext(Dispatchers.Default) {
            outQueries.listLightningOutgoingPayments(paymentHash)
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
        withContext(Dispatchers.Default) {
            database.transaction {
                inQueries.updateNewChannelReceivedWithChannelId(paymentHash, channelId)
            }
        }
    }

    suspend fun updateNewChannelConfirmed(
        channelId: ByteVector32,
        receivedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            database.transaction {
                inQueries.findNewChannelPayment(channelId)?.let { paymentHash ->
                    inQueries.updateNewChannelConfirmed(paymentHash, receivedAt)
                }
            }
        }
    }

    override suspend fun getIncomingPayment(
        paymentHash: ByteVector32
    ): IncomingPayment? {

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

    // ---- cleaning expired payments

    override suspend fun listExpiredPayments(fromCreatedAt: Long, toCreatedAt: Long): List<IncomingPayment> {

        return withContext(Dispatchers.Default) {
            inQueries.listExpiredPayments(fromCreatedAt, toCreatedAt)
        }
    }

    override suspend fun removeIncomingPayment(paymentHash: ByteVector32): Boolean {

        return withContext(Dispatchers.Default) {
            inQueries.deleteIncomingPayment(paymentHash)
        }
    }

    // ---- list ALL payments

    suspend fun listPaymentsCountFlow(): Flow<Long> {

        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsCount(::allPaymentsCountMapper).asFlow().mapToOne()
        }
    }

    suspend fun listPaymentsOrderFlow(
        count: Int,
        skip: Int
    ): Flow<List<WalletPaymentOrderRow>> {

        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPaymentsOrder(
                limit = count.toLong(),
                offset = skip.toLong(),
                mapper = ::allPaymentsOrderMapper
            ).asFlow().mapToList()
        }
    }

    suspend fun listRecentPaymentsOrderFlow(
        date: Long,
        count: Int,
        skip: Int
    ): Flow<List<WalletPaymentOrderRow>> {

        return withContext(Dispatchers.Default) {
            aggrQueries.listRecentPaymentsOrder(
                date = date,
                limit = count.toLong(),
                offset = skip.toLong(),
                mapper = ::allPaymentsOrderMapper
            ).asFlow().mapToList()
        }
    }

    suspend fun listOutgoingInFlightPaymentsOrderFlow(
        count: Int,
        skip: Int
    ): Flow<List<WalletPaymentOrderRow>> {

        return withContext(Dispatchers.Default) {
            aggrQueries.listOutgoingInFlightPaymentsOrder(
                limit = count.toLong(),
                offset = skip.toLong(),
                mapper = ::allPaymentsOrderMapper
            ).asFlow().mapToList()
        }
    }

    /**
     * List payments successfully received or sent between [startDate] and [endDate], for page ([skip]->[skip+count]).
     *
     * @param startDate timestamp in millis
     * @param endDate timestamp in millis
     * @param count limit number of rows
     * @param skip rows offset for paging
     */
    suspend fun listRangeSuccessfulPaymentsOrder(
        startDate: Long,
        endDate: Long,
        count: Int,
        skip: Int
    ): List<WalletPaymentOrderRow> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listRangeSuccessfulPaymentsOrder(
                startDate = startDate,
                endDate = endDate,
                limit = count.toLong(),
                offset = skip.toLong(),
                mapper = ::allPaymentsOrderMapper
            ).executeAsList()
        }
    }

    /**
     * Count payments successfully received or sent between [startDate] and [endDate].
     *
     * @param startDate timestamp in millis
     * @param endDate timestamp in millis
     */
    suspend fun listRangeSuccessfulPaymentsCount(
        startDate: Long,
        endDate: Long
    ): Long {
        return withContext(Dispatchers.Default) {
            aggrQueries.listRangeSuccessfulPaymentsCount(
                startDate = startDate,
                endDate = endDate,
                mapper = ::allPaymentsCountMapper
            ).executeAsList().first()
        }
    }

    suspend fun getOldestCompletedDate(): Long? {
        return withContext(Dispatchers.Default) {
            val oldestIncoming = inQueries.getOldestReceivedDate()
            val oldestOutgoing = outQueries.getOldestCompletedDate()
            listOfNotNull(oldestIncoming, oldestOutgoing).minOrNull()
        }
    }

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
        withContext(Dispatchers.Default) {
            database.transaction {
                when (paymentId) {
                    is WalletPaymentId.IncomingPaymentId -> {
                        database.incomingPaymentsQueries.delete(
                            payment_hash = paymentId.paymentHash.toByteArray()
                        )
                    }
                    is WalletPaymentId.OutgoingPaymentId -> {
                        database.outgoingPaymentsQueries.deleteLightningPartsForParentId(
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

    fun close() {
        driver.close()
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
                WalletPaymentId.DbType.SPLICE_OUTGOING.value -> {
                    WalletPaymentId.SpliceOutgoingPaymentId.fromString(id)
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
     * - "(splice_)outgoing|id|createdAt|completedAt|metadataModifiedAt"
     * - "incoming|paymentHash|createdAt|completedAt|metadataModifiedAt"
     */
    val identifier: String
        get() {
            return this.staleIdentifierPrefix +
                    (completedAt?.toString() ?: "null") + "|" +
                    (metadataModifiedAt?.toString() ?: "null")
        }

    /**
     * Returns a prefix that can be used to detect older (stale) versions of the row.
     * Form is:
     * - "(splice_)outgoing|id|createdAt|"
     * - "incoming|paymentHash|createdAt|"
     */
    val staleIdentifierPrefix: String
        get() {
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
 * Implement this function to execute platform specific code when a payment's metadata is updated.
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
