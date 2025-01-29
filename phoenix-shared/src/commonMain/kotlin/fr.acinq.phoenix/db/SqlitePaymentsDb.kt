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
import fr.acinq.lightning.utils.*
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.db.payments.PaymentsMetadataQueries
import fr.acinq.phoenix.managers.CurrencyManager
import kotlin.collections.List
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

    suspend fun getPayment(id: UUID): Pair<WalletPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        _getPayment(id)
    }

    fun _getPayment(id: UUID): Pair<WalletPayment, WalletPaymentMetadata?>? = database.transactionWithResult {
        (database.paymentsIncomingQueries.get(id).executeAsOneOrNull() ?: database.paymentsOutgoingQueries.get(id).executeAsOneOrNull())?.let { payment ->
            val metadata = metadataQueries.get(id, WalletPaymentFetchOptions.All)
            payment to metadata
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun getInboundLiquidityPurchase(txId: TxId): LiquidityAds.LiquidityTransactionDetails? {
        val payment = buildList {
            addAll(database.paymentsIncomingQueries.listByTxId(txId).executeAsList())
            addAll(database.paymentsOutgoingQueries.listByTxId(txId).executeAsList())
        }.firstOrNull()
        return when (payment) {
            is LightningIncomingPayment -> payment.liquidityPurchaseDetails
            is OnChainIncomingPayment -> payment.liquidityPurchaseDetails
            is LegacyPayToOpenIncomingPayment -> null
            is LegacySwapInIncomingPayment -> null
            is LightningOutgoingPayment -> null
            is OnChainOutgoingPayment -> payment.liquidityPurchaseDetails
            null -> null
        }
    }

    override suspend fun setLocked(txId: TxId) {
        database.transaction {
            val lockedAt = currentTimestampMillis()
            database.onChainTransactionsQueries.setLocked(tx_id = txId, locked_at = lockedAt)
            database.paymentsIncomingQueries.listByTxId(txId).executeAsList().filterIsInstance<OnChainIncomingPayment>().forEach { payment ->
                val payment1 = payment.setLocked(lockedAt)
                database.paymentsIncomingQueries.update(id = payment1.id, data = payment1, txId = payment1.txId, receivedAt = payment1.lockedAt)
                didSaveWalletPayment(payment1.id, database)
            }
            database.paymentsOutgoingQueries.listByTxId(txId).executeAsList().filterIsInstance<OnChainOutgoingPayment>().forEach { payment ->
                val payment1 = payment.setLocked(lockedAt)
                database.paymentsOutgoingQueries.update(id = payment1.id, data = payment1, completed_at = payment1.completedAt, succeeded_at = payment1.succeededAt)
                didSaveWalletPayment(payment1.id, database)
            }
        }
    }

    suspend fun setConfirmed(txId: TxId) = withContext(Dispatchers.Default) {
        database.transaction {
            val confirmedAt = currentTimestampMillis()
            database.onChainTransactionsQueries.setConfirmed(tx_id = txId, confirmed_at = confirmedAt)
            database.paymentsIncomingQueries.listByTxId(txId).executeAsList().filterIsInstance<OnChainIncomingPayment>().forEach { payment ->
                val payment1 = payment.setConfirmed(confirmedAt)
                // receivedAt must still set to lockedAt, and not confirmedAt.
                database.paymentsIncomingQueries.update(id = payment1.id, data = payment1, txId = payment1.txId, receivedAt = payment1.lockedAt)
                didSaveWalletPayment(payment1.id, database)
            }
            database.paymentsOutgoingQueries.listByTxId(txId).executeAsList().filterIsInstance<OnChainOutgoingPayment>().forEach { payment ->
                val payment1 = payment.setConfirmed(confirmedAt)
                database.paymentsOutgoingQueries.update(id = payment1.id, data = payment1, completed_at = payment1.completedAt, succeeded_at = payment1.succeededAt)
                didSaveWalletPayment(payment1.id, database)
            }
        }
    }

    fun listUnconfirmedTransactions(): Flow<List<TxId>> {
        return database.onChainTransactionsQueries.listUnconfirmed()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    suspend fun listPaymentsForTxId(txId: TxId): List<WalletPayment> = withContext(Dispatchers.Default) {
        database.paymentsIncomingQueries.listByTxId(txId).executeAsList() + database.paymentsOutgoingQueries.listByTxId(txId).executeAsList()
    }

    // ---- list ALL payments

    fun listPaymentsAsFlow(count: Long, skip: Long): Flow<List<WalletPaymentInfo>> {
        return database.paymentsQueries.list(limit = count, offset = skip, mapper = ::mapPaymentsAndMetadata)
            .asFlow()
            .map { it.executeAsList().discardReceivedAutomaticLiquidity() }
    }

    suspend fun listCompletedPayments(count: Long, skip: Long, startDate: Long, endDate: Long): List<WalletPaymentInfo> = withContext(Dispatchers.Default) {
        database.paymentsQueries.listSucceeded(succeeded_at_from = startDate, succeeded_at_to = endDate, limit = count, offset = skip, mapper = ::mapPaymentsAndMetadata).executeAsList()
            .discardReceivedAutomaticLiquidity()
    }

    /** [AutomaticLiquidityPurchasePayment] should be ignored if the payment they are associated with has been received, because the liquidity data are already contained in that payment. */
    private fun List<WalletPaymentInfo>.discardReceivedAutomaticLiquidity(): List<WalletPaymentInfo> = this.filterNot {
        val payment = it.payment
        payment is AutomaticLiquidityPurchasePayment && payment.incomingPaymentReceivedAt != null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mapPaymentsAndMetadata(data_: ByteArray, payment_id: UUID?,
                                       lnurl_base_type: LnurlBase.TypeVersion?, lnurl_base_blob: ByteArray?, lnurl_description: String?, lnurl_metadata_type: LnurlMetadata.TypeVersion?, lnurl_metadata_blob: ByteArray?,
                                       lnurl_successAction_type: LnurlSuccessAction.TypeVersion?, lnurl_successAction_blob: ByteArray?,
                                       user_description: String?, user_notes: String?, modified_at: Long?, original_fiat_type: String?, original_fiat_rate: Double?): WalletPaymentInfo {
        val payment = WalletPaymentAdapter.decode(data_)
        return WalletPaymentInfo(
            payment = payment,
            metadata = PaymentsMetadataQueries.mapAll(payment.id,
                lnurl_base_type, lnurl_base_blob, lnurl_description, lnurl_metadata_type, lnurl_metadata_blob,
                lnurl_successAction_type, lnurl_successAction_blob,
                user_description, user_notes, modified_at, original_fiat_type, original_fiat_rate),
            contact = null,
            fetchOptions = WalletPaymentFetchOptions.Metadata
        )
    }

    fun listPaymentsCountFlow(): Flow<Long> {
        return database.paymentsQueries.count()
            .asFlow()
            .map {
                withContext(Dispatchers.Default) {
                    database.transactionWithResult {
                        it.executeAsOne()
                    }
                }
            }
    }

    /** Returns the timestamp of the oldest completed payment, if any. Lets us set up the export-csv UI with a nice start date. */
    fun getOldestCompletedTimestamp(): Long? {
        return database.paymentsQueries.getOldestCompletedTimestamp().executeAsOneOrNull()?.completed_at
    }

    /** Suspending version of `getOldestCompletedTimestamp`. */
    suspend fun getOldestCompletedDate(): Long? = withContext(Dispatchers.Default) {
        getOldestCompletedTimestamp()
    }

    suspend fun countCompletedInRange(
        startDate: Long,
        endDate: Long
    ): Long = withContext(Dispatchers.Default) {
        database.paymentsQueries.countCompletedInRange(
            completed_at_from = startDate,
            completed_at_to = endDate
        ).executeAsOne()
    }

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

    /**
     * Cloudkit operates on a record-by-record basis. When a database migration involves merging
     * records, it has to be done in a separate post-processing step.
     *
     * This particular function merges liquidity-related records, into other records.
     */
    suspend fun finishCloudkitRestore(): Unit = withContext(Dispatchers.Default) {
        database.transaction {
            database.paymentsIncomingQueries
                .listSuccessful(
                    received_at_from = 0,
                    received_at_to = Long.MAX_VALUE,
                    limit = Long.MAX_VALUE,
                    offset = 0
                )
                .executeAsList()
                .forEach {
                    when (val incomingPayment = it) {
                        is NewChannelIncomingPayment -> if (incomingPayment.liquidityPurchase == null) {
                            val manualLiquidityPayment = database.paymentsOutgoingQueries.listByTxId(incomingPayment.txId)
                                .executeAsOneOrNull() as? ManualLiquidityPurchasePayment
                            manualLiquidityPayment?.let {
                                val incomingPayment1 = incomingPayment.copy(liquidityPurchase = manualLiquidityPayment.liquidityPurchase)
                                database.paymentsIncomingQueries.update(
                                    receivedAt = incomingPayment1.completedAt,
                                    txId = incomingPayment1.txId,
                                    data = incomingPayment1,
                                    id = incomingPayment1.id
                                )
                                database.paymentsOutgoingQueries.delete(manualLiquidityPayment.id)
                                didSaveWalletPayment(incomingPayment.id, database)
                                didDeleteWalletPayment(manualLiquidityPayment.id, database)
                            }
                        }
                        is LightningIncomingPayment ->  if (incomingPayment.liquidityPurchaseDetails == null) {
                            val txId = incomingPayment.parts.filterIsInstance<LightningIncomingPayment.Part.Htlc>().firstNotNullOfOrNull { it.fundingFee?.fundingTxId }
                            txId?.let {
                                val autoLiquidityPayment =
                                    database.paymentsOutgoingQueries.listByTxId(txId)
                                        .executeAsOneOrNull() as? AutomaticLiquidityPurchasePayment
                                autoLiquidityPayment?.let {
                                    val incomingPayment1 = when(incomingPayment) {
                                        is Bolt11IncomingPayment -> incomingPayment.copy(liquidityPurchaseDetails = autoLiquidityPayment.liquidityPurchaseDetails)
                                        is Bolt12IncomingPayment -> incomingPayment.copy(liquidityPurchaseDetails = autoLiquidityPayment.liquidityPurchaseDetails)
                                    }
                                    database.paymentsIncomingQueries.update(
                                        id = incomingPayment1.id,
                                        data = incomingPayment1,
                                        receivedAt = incomingPayment1.completedAt,
                                        txId = incomingPayment1.liquidityPurchaseDetails?.txId
                                    )
                                    val autoLiquidityPayment1 = autoLiquidityPayment.copy(incomingPaymentReceivedAt = incomingPayment1.completedAt)
                                    database.paymentsOutgoingQueries.update(
                                        id = autoLiquidityPayment.id,
                                        completed_at = autoLiquidityPayment1.completedAt,
                                        succeeded_at = autoLiquidityPayment1.succeededAt,
                                        data = autoLiquidityPayment1
                                    )
                                    didSaveWalletPayment(incomingPayment.id, database)
                                    didSaveWalletPayment(autoLiquidityPayment.id, database)
                                }
                            }
                        }
                        else -> Unit
                    }
                }
        }
    }

    fun close() = driver.close()
}

// NOTE: this object should probably be removed.
//
// In the past we had to first list a subset of payments data (id and a few timestamp), then for each
// result, fetch the detail of that payments.
//
// Now we are able to list payments in one go, including the actual payments data (and metadata).
//
// However, the `identifier` seems to be used in the payments cache so some part of this object could
// still be useful and moved into `WalletPaymentInfo` ?
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
