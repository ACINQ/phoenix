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

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.db.*
import fr.acinq.lightning.db.OnChainIncomingPayment.Companion.setConfirmed
import fr.acinq.lightning.db.OnChainIncomingPayment.Companion.setLocked
import fr.acinq.lightning.db.OnChainOutgoingPayment.Companion.setConfirmed
import fr.acinq.lightning.db.OnChainOutgoingPayment.Companion.setLocked
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.db.payments.PaymentsMetadataQueries
import fr.acinq.phoenix.managers.CurrencyManager
import fracinqphoenixdb.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlitePaymentsDb(
    val driver: SqlDriver,
    val database: PaymentsDatabase,
    private val currencyManager: CurrencyManager? = null,
) : IncomingPaymentsDb by SqliteIncomingPaymentsDb(database),
    OutgoingPaymentsDb by SqliteOutgoingPaymentsDb(database),
    PaymentsDb {

    val metadataQueries = PaymentsMetadataQueries(database)
    private var metadataQueue = MutableStateFlow(mapOf<UUID, WalletPaymentMetadataRow>())

    suspend fun getPayment(id: UUID, options: WalletPaymentFetchOptions): Pair<WalletPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            database.paymentsQueries.get(id).executeAsOneOrNull()?.let { WalletPaymentAdapter.decode(it) }?.let { payment ->
                val metadata = metadataQueries.get(id, options)
                payment to metadata
            }
        }
    }

    override suspend fun setLocked(txId: TxId) {
        database.transaction {
            val lockedAt = currentTimestampMillis()
            database.onChainTransactionsQueries.setLocked(tx_id = txId, locked_at = lockedAt)
            database.onChainTransactionsQueries
                .listByTxId(txId)
                .executeAsList()
                .map { WalletPaymentAdapter.decode(it) }
                .forEach { payment ->
                    @Suppress("DEPRECATION")
                    when (payment) {
                        is LightningIncomingPayment -> {}
                        is OnChainIncomingPayment -> {
                            val payment1 = payment.setLocked(lockedAt)
                            database.paymentsIncomingQueries.update(id = payment1.id, data = payment1, receivedAt = lockedAt)
                            didSaveWalletPayment(payment1.id, database)
                        }
                        is LegacyPayToOpenIncomingPayment -> {}
                        is LegacySwapInIncomingPayment -> {}
                        is LightningOutgoingPayment -> {}
                        is OnChainOutgoingPayment -> {
                            val payment1 = payment.setLocked(lockedAt)
                            database.paymentsOutgoingQueries.update(id = payment1.id, data = payment1, completed_at = lockedAt, sent_at = lockedAt)
                            didSaveWalletPayment(payment1.id, database)
                        }
                    }
                }
        }
    }

    suspend fun setConfirmed(txId: TxId) = withContext(Dispatchers.Default) {
        database.transaction {
            val confirmedAt = currentTimestampMillis()
            database.onChainTransactionsQueries.setConfirmed(tx_id = txId, confirmed_at = confirmedAt)
            database.onChainTransactionsQueries
                .listByTxId(txId)
                .executeAsList()
                .map { WalletPaymentAdapter.decode(it) }
                .forEach { payment ->
                    @Suppress("DEPRECATION")
                    when (payment) {
                        is LightningIncomingPayment -> {}
                        is OnChainIncomingPayment -> {
                            val payment1 = payment.setConfirmed(confirmedAt)
                            database.paymentsIncomingQueries.update(id = payment1.id, data = payment1, receivedAt = null)
                            didSaveWalletPayment(payment1.id, database)
                        }
                        is LegacyPayToOpenIncomingPayment -> {}
                        is LegacySwapInIncomingPayment -> {}
                        is LightningOutgoingPayment -> {}
                        is OnChainOutgoingPayment -> {
                            val payment1 = payment.setConfirmed(confirmedAt)
                            database.paymentsOutgoingQueries.update(id = payment1.id, data = payment1, completed_at = null, sent_at = null)
                            didSaveWalletPayment(payment1.id, database)
                        }
                    }
                }
        }
    }

    suspend fun listUnconfirmedTransactions(): Flow<List<TxId>> = withContext(Dispatchers.Default) {
        // TODO: should return a flow of tx ids instead of a list
        database.onChainTransactionsQueries.listUnconfirmed().asFlow().mapToList(Dispatchers.Default)
    }

    suspend fun getWalletPaymentForTxId(txId: TxId): List<WalletPayment> = withContext(Dispatchers.Default) {
        database.onChainTransactionsQueries.listByTxId(txId).executeAsList().map { WalletPaymentAdapter.decode(it) }
    }

    suspend fun listPaymentsForTxId(txId: TxId): List<WalletPayment> = withContext(Dispatchers.Default) {
        database.onChainTransactionsQueries.listByTxId(txId).executeAsList().map { WalletPaymentAdapter.decode(it) }
    }

    // ---- list ALL payments

    suspend fun listPayments(count: Long, skip: Long): Flow<List<WalletPayment>> = withContext(Dispatchers.Default) {
        // TODO: optimise this method to only fetch the data we need to populate the home list
        // including contact, metadata, ...
        database.paymentsQueries.list(count, skip)
            .asFlow()
            .map { it.executeAsList().map { WalletPaymentAdapter.decode(it) } }
    }

    suspend fun listPayments(count: Long, skip: Long, startDate: Long, endDate: Long, fetchOptions: WalletPaymentFetchOptions): List<WalletPaymentInfo> = withContext(Dispatchers.Default) {
        // TODO: optimise this method to join all the data and populate the list in one query, including contact, metadata, ...
        database.paymentsQueries.listFromTo(completed_at_from = startDate, completed_at_to = endDate, limit = count, offset = skip).executeAsList()
            .map {
                WalletPaymentInfo(
                    payment = WalletPaymentAdapter.decode(it),
                    metadata = WalletPaymentMetadata(),
                    contact = null,
                    fetchOptions = fetchOptions
                )
            }
    }

//    fun listPaymentsCountFlow(): Flow<Long> {
//        return aggrQueries.listAllPaymentsCount(::allPaymentsCountMapper)
//            .asFlow()
//            .map {
//                withContext(Dispatchers.Default) {
//                    database.transactionWithResult {
//                        it.executeAsOne()
//                    }
//                }
//            }
//    }

    /** Returns a flow of incoming payments within <count, skip>. This flow is updated when the data change in the database. */
//    fun listPaymentsOrderFlow(
//        count: Int,
//        skip: Int
//    ): Flow<List<WalletPaymentOrderRow>>  {
//        return aggrQueries.listAllPaymentsOrder(
//            limit = count.toLong(),
//            offset = skip.toLong(),
//            mapper = ::allPaymentsOrderMapper
//        )
//        .asFlow()
//        .map {
//            withContext(Dispatchers.Default) {
//                database.transactionWithResult {
//                    it.executeAsList()
//                }
//            }
//        }
//    }

//    fun listRecentPaymentsOrderFlow(
//        date: Long,
//        count: Int,
//        skip: Int
//    ): Flow<List<WalletPaymentOrderRow>> {
//        return aggrQueries.listRecentPaymentsOrder(
//            date = date,
//            limit = count.toLong(),
//            offset = skip.toLong(),
//            mapper = ::allPaymentsOrderMapper
//        )
//        .asFlow()
//        .map {
//            withContext(Dispatchers.Default) {
//                database.transactionWithResult {
//                    it.executeAsList()
//                }
//            }
//        }
//    }

//    fun listOutgoingInFlightPaymentsOrderFlow(
//        count: Int,
//        skip: Int
//    ): Flow<List<WalletPaymentOrderRow>> {
//        return aggrQueries.listOutgoingInFlightPaymentsOrder(
//            limit = count.toLong(),
//            offset = skip.toLong(),
//            mapper = ::allPaymentsOrderMapper
//        )
//        .asFlow()
//        .map {
//            withContext(Dispatchers.Default) {
//                database.transactionWithResult {
//                    it.executeAsList()
//                }
//            }
//        }
//    }

    /**
     * List payments successfully received or sent between [startDate] and [endDate], for page ([skip]->[skip+count]).
     *
     * @param startDate timestamp in millis
     * @param endDate timestamp in millis
     * @param count limit number of rows
     * @param skip rows offset for paging
     */
//    suspend fun listRangeSuccessfulPaymentsOrder(
//        startDate: Long,
//        endDate: Long,
//        count: Int,
//        skip: Int
//    ): List<WalletPaymentOrderRow> = withContext(Dispatchers.Default) {
//        aggrQueries.listRangeSuccessfulPaymentsOrder(
//            startDate = startDate,
//            endDate = endDate,
//            limit = count.toLong(),
//            offset = skip.toLong(),
//            mapper = ::allPaymentsOrderMapper
//        ).executeAsList()
//    }

//    /**
//     * Count payments successfully received or sent between [startDate] and [endDate].
//     *
//     * @param startDate timestamp in millis
//     * @param endDate timestamp in millis
//     */
//    suspend fun listRangeSuccessfulPaymentsCount(
//        startDate: Long,
//        endDate: Long
//    ): Long = withContext(Dispatchers.Default) {
//        aggrQueries.listRangeSuccessfulPaymentsCount(
//            startDate = startDate,
//            endDate = endDate,
//            mapper = ::allPaymentsCountMapper
//        ).executeAsList().first()
//    }

    /**
     * The lightning-kmp layer triggers the addition of a payment to the database.
     * But sometimes there is associated metadata that we want to include,
     * and we would like to write it to the database within the same transaction.
     * So we have a system to enqueue/dequeue associated metadata.
     */
    internal fun enqueueMetadata(row: WalletPaymentMetadataRow, id: UUID) {
        val oldMap = metadataQueue.value
        val newMap = oldMap + (id to row)
        metadataQueue.value = newMap
    }

    /**
     * Returns any enqueued metadata, and also appends the current fiat exchange rate.
     */
    private fun dequeueMetadata(id: UUID): WalletPaymentMetadataRow {
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

    suspend fun updateUserInfo(id: UUID, userDescription: String?, userNotes: String?) = withContext(Dispatchers.Default) {
        metadataQueries.updateUserInfo(id = id, userDescription = userDescription, userNotes = userNotes)
    }

    suspend fun deletePayment(paymentId: UUID): Unit = withContext(Dispatchers.Default) {
        TODO("add a parameter to know which table we should delete paymentId from")
        // didDeleteWalletPayment(paymentId, database)
    }

    fun close() = driver.close()

    companion object {
        private fun allPaymentsCountMapper(
            result: Long?
        ): Long {
            return result ?: 0
        }

        private fun allPaymentsOrderMapper(
            id: UUID,
            created_at: Long,
            completed_at: Long?,
            metadata_modified_at: Long?
        ): WalletPaymentOrderRow {
            return WalletPaymentOrderRow(
                id = id,
                createdAt = created_at,
                completedAt = completed_at,
                metadataModifiedAt = metadata_modified_at
            )
        }
    }
}

data class WalletPaymentOrderRow(
    val id: UUID,
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
            return "${id}|${createdAt}|"
        }
}
