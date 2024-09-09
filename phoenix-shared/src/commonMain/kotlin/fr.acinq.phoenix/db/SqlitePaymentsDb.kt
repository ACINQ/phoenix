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

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.channel.ChannelException
import fr.acinq.lightning.db.*
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.utils.*
import fr.acinq.lightning.wire.FailureMessage
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.db.payments.LinkTxToPaymentQueries
import fr.acinq.phoenix.managers.CurrencyManager
import fracinqphoenixdb.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlitePaymentsDb(
    loggerFactory: LoggerFactory,
    private val driver: SqlDriver,
    private val currencyManager: CurrencyManager? = null
) : PaymentsDb {

    private val log = loggerFactory.newLogger(this::class)

    internal val database = PaymentsDatabase(
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
        ),
        channel_close_outgoing_paymentsAdapter = Channel_close_outgoing_payments.Adapter(
            closing_info_typeAdapter = EnumColumnAdapter()
        ),
        inbound_liquidity_outgoing_paymentsAdapter = Inbound_liquidity_outgoing_payments.Adapter(
            lease_typeAdapter = EnumColumnAdapter()
        )
    )

    internal val inQueries = IncomingQueries(database)
    internal val outQueries = OutgoingQueries(database)
    internal val spliceOutQueries = SpliceOutgoingQueries(database)
    internal val channelCloseQueries = ChannelCloseOutgoingQueries(database)
    internal val cpfpQueries = SpliceCpfpOutgoingQueries(database)
    private val aggrQueries = database.aggregatedQueriesQueries
    internal val metaQueries = MetadataQueries(database)
    private val linkTxToPaymentQueries = LinkTxToPaymentQueries(database)
    internal val inboundLiquidityQueries = InboundLiquidityQueries(database)

    private var metadataQueue = MutableStateFlow(mapOf<WalletPaymentId, WalletPaymentMetadataRow>())

    override suspend fun addOutgoingLightningParts(
        parentId: UUID,
        parts: List<LightningOutgoingPayment.Part>
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
                    is LightningOutgoingPayment -> {
                        outQueries.addLightningOutgoingPayment(outgoingPayment)
                    }
                    is SpliceOutgoingPayment -> {
                        spliceOutQueries.addSpliceOutgoingPayment(outgoingPayment)
                        linkTxToPaymentQueries.linkTxToPayment(
                            txId = outgoingPayment.txId,
                            walletPaymentId = outgoingPayment.walletPaymentId()
                        )
                    }
                    is ChannelCloseOutgoingPayment -> {
                        channelCloseQueries.addChannelCloseOutgoingPayment(outgoingPayment)
                        linkTxToPaymentQueries.linkTxToPayment(
                            txId = outgoingPayment.txId,
                            walletPaymentId = outgoingPayment.walletPaymentId()
                        )
                    }
                    is SpliceCpfpOutgoingPayment -> {
                        cpfpQueries.addCpfpPayment(outgoingPayment)
                        linkTxToPaymentQueries.linkTxToPayment(outgoingPayment.txId, outgoingPayment.walletPaymentId())
                    }
                    is InboundLiquidityOutgoingPayment -> {
                        inboundLiquidityQueries.add(outgoingPayment)
                        linkTxToPaymentQueries.linkTxToPayment(outgoingPayment.txId, outgoingPayment.walletPaymentId())
                    }
                }
                // Add associated metadata within the same atomic database transaction.
                if (!metadataRow.isEmpty()) {
                    metaQueries.addMetadata(paymentId, metadataRow)
                }
            }
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
        failure: LightningOutgoingPayment.Part.Status.Failure,
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
    @Deprecated("only use this method for migrating legacy payments from android-eclair")
    suspend fun completeOutgoingLightningPartLegacy(
        partId: UUID,
        failedStatus: LightningOutgoingPayment.Part.Status.Failed,
        completedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            val (statusType, statusData) = failedStatus.failure.mapToDb()
            outQueries.database.outgoingPaymentsQueries.updateLightningPart(
                part_id = partId.toString(),
                part_status_type = statusType,
                part_status_blob = statusData,
                part_completed_at = completedAt
            )
        }
    }

    override suspend fun getLightningOutgoingPayment(id: UUID): LightningOutgoingPayment? = withContext(Dispatchers.Default) {
        outQueries.getPaymentStrict(id)
    }

    override suspend fun getLightningOutgoingPaymentFromPartId(partId: UUID): LightningOutgoingPayment? = withContext(Dispatchers.Default) {
        outQueries.getPaymentFromPartId(partId)
    }

    suspend fun getLightningOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<OutgoingPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            outQueries.getPaymentRelaxed(id)?.let {
                val metadata = metaQueries.getMetadata(it.walletPaymentId(), options)
                it to metadata
            }
        }
    }

    suspend fun getSpliceOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<SpliceOutgoingPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            spliceOutQueries.getSpliceOutPayment(id)?.let {
                val metadata = metaQueries.getMetadata(id = it.walletPaymentId(), options)
                it to metadata
            }
        }
    }

    suspend fun getChannelCloseOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<ChannelCloseOutgoingPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            channelCloseQueries.getChannelCloseOutgoingPayment(id)?.let {
                val metadata = metaQueries.getMetadata(id = it.walletPaymentId(), options)
                it to metadata
            }
        }
    }

    suspend fun getSpliceCpfpOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<SpliceCpfpOutgoingPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            cpfpQueries.getCpfp(id)?.let {
                val metadata = metaQueries.getMetadata(id = it.walletPaymentId(), options)
                it to metadata
            }
        }
    }

    suspend fun getInboundLiquidityOutgoingPayment(
        id: UUID,
        options: WalletPaymentFetchOptions
    ): Pair<InboundLiquidityOutgoingPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            inboundLiquidityQueries.get(id)?.let {
                val metadata = metaQueries.getMetadata(id = it.walletPaymentId(), options)
                it to metadata
            }
        }
    }

    // ---- list outgoing

    override suspend fun listLightningOutgoingPayments(
        paymentHash: ByteVector32
    ): List<LightningOutgoingPayment> = withContext(Dispatchers.Default) {
        outQueries.listLightningOutgoingPayments(paymentHash)
    }

    // ---- incoming payments

    override suspend fun addIncomingPayment(
        preimage: ByteVector32,
        origin: IncomingPayment.Origin,
        createdAt: Long
    ): IncomingPayment {
        val paymentHash = Crypto.sha256(preimage).toByteVector32()
        val paymentId = WalletPaymentId.IncomingPaymentId(paymentHash)
        val metadataRow = dequeueMetadata(paymentId)

        return withContext(Dispatchers.Default) {
            database.transactionWithResult {
                inQueries.addIncomingPayment(preimage, paymentHash, origin, createdAt)
                // Add associated metadata within the same atomic database transaction.
                if (!metadataRow.isEmpty()) {
                    metaQueries.addMetadata(paymentId, metadataRow)
                }
                inQueries.getIncomingPayment(paymentHash)!!
            }
        }
    }

    override suspend fun receivePayment(
        paymentHash: ByteVector32,
        receivedWith: List<IncomingPayment.ReceivedWith>,
        receivedAt: Long
    ) {
        withContext(Dispatchers.Default) {
            database.transaction {
                inQueries.receivePayment(paymentHash, receivedWith, receivedAt)
                // if one received-with is on-chain, save the tx id to the db
                receivedWith.filterIsInstance<IncomingPayment.ReceivedWith.OnChainIncomingPayment>().firstOrNull()?.let {
                    linkTxToPaymentQueries.linkTxToPayment(it.txId, WalletPaymentId.IncomingPaymentId(paymentHash))
                }
            }
        }
    }

    override suspend fun setLocked(txId: TxId) {
        database.transaction {
            val lockedAt = currentTimestampMillis()
            linkTxToPaymentQueries.setLocked(txId, lockedAt)
            linkTxToPaymentQueries.listWalletPaymentIdsForTx(txId).forEach { walletPaymentId ->
                when (walletPaymentId) {
                    is WalletPaymentId.IncomingPaymentId -> {
                        inQueries.setLocked(walletPaymentId.paymentHash, lockedAt)
                    }
                    is WalletPaymentId.LightningOutgoingPaymentId -> {
                        // LN payments need not be locked
                    }
                    is WalletPaymentId.SpliceOutgoingPaymentId -> {
                        spliceOutQueries.setLocked(walletPaymentId.id, lockedAt)
                    }
                    is WalletPaymentId.ChannelCloseOutgoingPaymentId -> {
                        channelCloseQueries.setLocked(walletPaymentId.id, lockedAt)
                    }
                    is WalletPaymentId.SpliceCpfpOutgoingPaymentId -> {
                        cpfpQueries.setLocked(walletPaymentId.id, lockedAt)
                    }
                    is WalletPaymentId.InboundLiquidityOutgoingPaymentId -> {
                        inboundLiquidityQueries.setLocked(walletPaymentId.id, lockedAt)
                    }
                }
            }
        }
    }

    suspend fun setConfirmed(txId: TxId) = withContext(Dispatchers.Default) {
        database.transaction {
            val confirmedAt = currentTimestampMillis()
            linkTxToPaymentQueries.setConfirmed(txId, confirmedAt)
            linkTxToPaymentQueries.listWalletPaymentIdsForTx(txId).forEach { walletPaymentId ->
                when (walletPaymentId) {
                    is WalletPaymentId.IncomingPaymentId -> {
                        inQueries.setConfirmed(walletPaymentId.paymentHash, confirmedAt)
                    }
                    is WalletPaymentId.LightningOutgoingPaymentId -> {
                        // LN payments need not be confirmed
                    }
                    is WalletPaymentId.SpliceOutgoingPaymentId -> {
                        spliceOutQueries.setConfirmed(walletPaymentId.id, confirmedAt)
                    }
                    is WalletPaymentId.ChannelCloseOutgoingPaymentId -> {
                        channelCloseQueries.setConfirmed(walletPaymentId.id, confirmedAt)
                    }
                    is WalletPaymentId.SpliceCpfpOutgoingPaymentId -> {
                        cpfpQueries.setConfirmed(walletPaymentId.id, confirmedAt)
                    }
                    is WalletPaymentId.InboundLiquidityOutgoingPaymentId -> {
                        inboundLiquidityQueries.setConfirmed(walletPaymentId.id, confirmedAt)
                    }
                }
            }
        }
    }

    suspend fun listUnconfirmedTransactions(): Flow<List<ByteArray>> = withContext(Dispatchers.Default) {
        linkTxToPaymentQueries.listUnconfirmedTxs()
    }

    suspend fun listPaymentsForTxId(
        txId: TxId
    ): List<WalletPayment> = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            linkTxToPaymentQueries.listWalletPaymentIdsForTx(txId).mapNotNull {
                when (it) {
                    is WalletPaymentId.IncomingPaymentId -> inQueries.getIncomingPayment(it.paymentHash)
                    is WalletPaymentId.LightningOutgoingPaymentId -> outQueries.getPaymentRelaxed(it.id)
                    is WalletPaymentId.ChannelCloseOutgoingPaymentId -> channelCloseQueries.getChannelCloseOutgoingPayment(it.id)
                    is WalletPaymentId.SpliceCpfpOutgoingPaymentId -> cpfpQueries.getCpfp(it.id)
                    is WalletPaymentId.SpliceOutgoingPaymentId -> spliceOutQueries.getSpliceOutPayment(it.id)
                    is WalletPaymentId.InboundLiquidityOutgoingPaymentId -> inboundLiquidityQueries.get(it.id)
                }
            }
        }
    }

    override suspend fun getIncomingPayment(
        paymentHash: ByteVector32
    ): IncomingPayment? = withContext(Dispatchers.Default) {
        inQueries.getIncomingPayment(paymentHash)
    }

    suspend fun getIncomingPayment(
        paymentHash: ByteVector32,
        options: WalletPaymentFetchOptions
    ): Pair<IncomingPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            inQueries.getIncomingPayment(paymentHash)?.let { payment ->
                val metadata = metaQueries.getMetadata(id = WalletPaymentId.IncomingPaymentId(paymentHash), options = options)
                Pair(payment, metadata)
            }
        }
    }

    // ---- cleaning expired payments

    override suspend fun listExpiredPayments(
        fromCreatedAt: Long,
        toCreatedAt: Long
    ): List<IncomingPayment> = withContext(Dispatchers.Default) {
        inQueries.listExpiredPayments(fromCreatedAt, toCreatedAt)
    }

    override suspend fun removeIncomingPayment(
        paymentHash: ByteVector32
    ): Boolean = withContext(Dispatchers.Default) {
        inQueries.deleteIncomingPayment(paymentHash)
    }

    // ---- list ALL payments

    fun listPaymentsCountFlow(): Flow<Long> {
        return aggrQueries.listAllPaymentsCount(::allPaymentsCountMapper)
            .asFlow()
            .map {
                withContext(Dispatchers.Default) {
                    database.transactionWithResult {
                        it.executeAsOne()
                    }
                }
            }
    }

    suspend fun listIncomingPaymentsNotYetConfirmed(): Flow<List<IncomingPayment>> = withContext(Dispatchers.Default) {
        inQueries.listAllNotConfirmed()
    }

    /** Returns a flow of incoming payments within <count, skip>. This flow is updated when the data change in the database. */
    fun listPaymentsOrderFlow(
        count: Int,
        skip: Int
    ): Flow<List<WalletPaymentOrderRow>>  {
        return aggrQueries.listAllPaymentsOrder(
            limit = count.toLong(),
            offset = skip.toLong(),
            mapper = ::allPaymentsOrderMapper
        )
        .asFlow()
        .map {
            withContext(Dispatchers.Default) {
                database.transactionWithResult {
                    it.executeAsList()
                }
            }
        }
    }

    fun listRecentPaymentsOrderFlow(
        date: Long,
        count: Int,
        skip: Int
    ): Flow<List<WalletPaymentOrderRow>> {
        return aggrQueries.listRecentPaymentsOrder(
            date = date,
            limit = count.toLong(),
            offset = skip.toLong(),
            mapper = ::allPaymentsOrderMapper
        )
        .asFlow()
        .map {
            withContext(Dispatchers.Default) {
                database.transactionWithResult {
                    it.executeAsList()
                }
            }
        }
    }

    fun listOutgoingInFlightPaymentsOrderFlow(
        count: Int,
        skip: Int
    ): Flow<List<WalletPaymentOrderRow>> {
        return aggrQueries.listOutgoingInFlightPaymentsOrder(
            limit = count.toLong(),
            offset = skip.toLong(),
            mapper = ::allPaymentsOrderMapper
        )
        .asFlow()
        .map {
            withContext(Dispatchers.Default) {
                database.transactionWithResult {
                    it.executeAsList()
                }
            }
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
    ): List<WalletPaymentOrderRow> = withContext(Dispatchers.Default) {
        aggrQueries.listRangeSuccessfulPaymentsOrder(
            startDate = startDate,
            endDate = endDate,
            limit = count.toLong(),
            offset = skip.toLong(),
            mapper = ::allPaymentsOrderMapper
        ).executeAsList()
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
    ): Long = withContext(Dispatchers.Default) {
        aggrQueries.listRangeSuccessfulPaymentsCount(
            startDate = startDate,
            endDate = endDate,
            mapper = ::allPaymentsCountMapper
        ).executeAsList().first()
    }

    suspend fun getOldestCompletedDate(): Long? = withContext(Dispatchers.Default) {
        val oldestIncoming = inQueries.getOldestReceivedDate()
        val oldestOutgoing = outQueries.getOldestCompletedDate()
        listOfNotNull(oldestIncoming, oldestOutgoing).minOrNull()
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
    ) = withContext(Dispatchers.Default) {
        metaQueries.updateUserInfo(
            id = id,
            userDescription = userDescription,
            userNotes = userNotes
        )
    }

    suspend fun deletePayment(paymentId: WalletPaymentId) = withContext(Dispatchers.Default) {
        database.transaction {
            when (paymentId) {
                is WalletPaymentId.IncomingPaymentId -> {
                    database.incomingPaymentsQueries.delete(
                        payment_hash = paymentId.paymentHash.toByteArray()
                    )
                }
                is WalletPaymentId.LightningOutgoingPaymentId -> {
                    database.outgoingPaymentsQueries.deleteLightningPartsForParentId(
                        part_parent_id = paymentId.dbId
                    )
                    database.outgoingPaymentsQueries.deletePayment(
                        id = paymentId.dbId
                    )
                }
                is WalletPaymentId.ChannelCloseOutgoingPaymentId -> {
                    database.channelCloseOutgoingPaymentQueries.deleteChannelCloseOutgoing(
                        id = paymentId.dbId
                    )
                }
                is WalletPaymentId.SpliceOutgoingPaymentId -> {
                    database.spliceOutgoingPaymentsQueries.deleteSpliceOutgoing(
                        id = paymentId.dbId
                    )
                }
                is WalletPaymentId.SpliceCpfpOutgoingPaymentId -> {
                    database.spliceCpfpOutgoingPaymentsQueries.deleteCpfp(
                        id = paymentId.dbId
                    )
                }
                is WalletPaymentId.InboundLiquidityOutgoingPaymentId -> {
                    database.inboundLiquidityOutgoingQueries.delete(id = paymentId.dbId)
                }
            }
            didDeleteWalletPayment(paymentId, database)
        }
    }

    fun close() = driver.close()

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
                WalletPaymentId.DbType.OUTGOING.value -> WalletPaymentId.LightningOutgoingPaymentId.fromString(id)
                WalletPaymentId.DbType.INCOMING.value -> WalletPaymentId.IncomingPaymentId.fromString(id)
                WalletPaymentId.DbType.SPLICE_OUTGOING.value -> WalletPaymentId.SpliceOutgoingPaymentId.fromString(id)
                WalletPaymentId.DbType.CHANNEL_CLOSE_OUTGOING.value -> WalletPaymentId.ChannelCloseOutgoingPaymentId.fromString(id)
                WalletPaymentId.DbType.SPLICE_CPFP_OUTGOING.value -> WalletPaymentId.SpliceCpfpOutgoingPaymentId.fromString(id)
                WalletPaymentId.DbType.INBOUND_LIQUIDITY_OUTGOING.value -> WalletPaymentId.InboundLiquidityOutgoingPaymentId.fromString(id)
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
