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
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.utils.*
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.db.contacts.SqliteContactsDb
import fr.acinq.phoenix.db.payments.*
import fr.acinq.phoenix.db.payments.PaymentsMetadataQueries
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import fr.acinq.phoenix.managers.PaymentMetadataQueue
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata
import fr.acinq.phoenix.utils.extensions.outgoingInvoiceRequest
import kotlin.collections.List
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class SqlitePaymentsDb(
    val driver: SqlDriver,
    val database: PaymentsDatabase,
    val paymentMetadataQueue: PaymentMetadataQueue?,
    val loggerFactory: LoggerFactory
) : IncomingPaymentsDb by SqliteIncomingPaymentsDb(database, paymentMetadataQueue),
    OutgoingPaymentsDb by SqliteOutgoingPaymentsDb(database, paymentMetadataQueue),
    PaymentsDb {

    val metadataQueries = PaymentsMetadataQueries(database)
    val contacts = SqliteContactsDb(driver, database, loggerFactory)

    val log = loggerFactory.newLogger(SqlitePaymentsDb::class)

    override suspend fun getInboundLiquidityPurchase(txId: TxId): LiquidityAds.LiquidityTransactionDetails? {
        val payment = buildList {
            addAll(database.paymentsIncomingQueries.listByTxId(txId).executeAsList())
            addAll(database.paymentsOutgoingQueries.listByTxId(txId).executeAsList())
        }.firstOrNull()
        @Suppress("DEPRECATION")
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

    suspend fun getPayment(id: UUID): Pair<WalletPayment, WalletPaymentMetadata?>? = withContext(Dispatchers.Default) {
        _getPayment(id)
    }

    fun _getPayment(id: UUID): Pair<WalletPayment, WalletPaymentMetadata?>? = database.transactionWithResult {
        (database.paymentsIncomingQueries.get(id).executeAsOneOrNull() ?: database.paymentsOutgoingQueries.get(id).executeAsOneOrNull())?.let { payment ->
            val metadata = metadataQueries.get(id)
            payment to metadata
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

    fun listPaymentsAsFlow(count: Long, skip: Long): Flow<List<WalletPaymentInfo>> {
        return combine(
            database.paymentsQueries.list(limit = count, offset = skip, mapper = ::mapPaymentsAndMetadata).asFlow().mapToList(Dispatchers.Default),
            contacts.indexesFlow,
            transform = ::combinePaymentAndContact
        )
    }

    fun listOutgoingInFlightPaymentsAsFlow(count: Long, skip: Long): Flow<List<WalletPaymentInfo>> {
        return combine(
            database.paymentsQueries.listInFlight(limit = count, offset = skip, mapper = ::mapPaymentsAndMetadata).asFlow().mapToList(Dispatchers.Default),
            contacts.indexesFlow,
            transform = ::combinePaymentAndContact
        )
    }

    // Recent payments includes in-flight (not completed) payments.
    fun listRecentPaymentsAsFlow(count: Long, skip: Long, sinceDate: Long): Flow<List<WalletPaymentInfo>> {
        return combine(
            database.paymentsQueries.listRecent(min_ts = sinceDate, limit = count, offset = skip, mapper = ::mapPaymentsAndMetadata).asFlow().mapToList(Dispatchers.Default),
            contacts.indexesFlow,
            transform = ::combinePaymentAndContact
        )
    }

    suspend fun listCompletedPayments(count: Long, skip: Long, startDate: Long, endDate: Long): List<WalletPaymentInfo> {
        return withContext(Dispatchers.Default) {
            database.paymentsQueries.listSuccessful(succeeded_at_from = startDate, succeeded_at_to = endDate, limit = count, offset = skip, mapper = ::mapPaymentsAndMetadata)
                .executeAsList()
        }
    }

    private fun combinePaymentAndContact(paymentInfoList: List<WalletPaymentInfo>, indexes: SqliteContactsDb.ContactIndexes): List<WalletPaymentInfo> = paymentInfoList.map { paymentInfo ->
        val payment = paymentInfo.payment
        val metadata = paymentInfo.metadata
        indexes.contactForPayment(payment, metadata)?.let {
            paymentInfo.copy(contact = it)
        } ?: paymentInfo
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mapPaymentsAndMetadata(
        data_: ByteArray,
        payment_id: UUID?,
        lnurl_base_type: LnurlBase.TypeVersion?,
        lnurl_base_blob: ByteArray?,
        lnurl_description: String?,
        lnurl_metadata_type: LnurlMetadata.TypeVersion?,
        lnurl_metadata_blob: ByteArray?,
        lnurl_successAction_type: LnurlSuccessAction.TypeVersion?,
        lnurl_successAction_blob: ByteArray?,
        user_description: String?,
        user_notes: String?,
        modified_at: Long?,
        original_fiat_type: String?,
        original_fiat_rate: Double?,
        lightning_address: String?
    ): WalletPaymentInfo {

        val payment = try {
            WalletPaymentAdapter.decode(data_)
        } catch (e: Exception) {
            log.error(e) { "failed to deserialize payment: ${e.message}" }
            throw e
        }

        val metadata = PaymentsMetadataQueries.mapAll(
            id = payment.id,
            lnurl_base_type = lnurl_base_type,
            lnurl_base_blob = lnurl_base_blob,
            lnurl_description = lnurl_description,
            lnurl_metadata_type = lnurl_metadata_type,
            lnurl_metadata_blob = lnurl_metadata_blob,
            lnurl_successAction_type = lnurl_successAction_type,
            lnurl_successAction_blob = lnurl_successAction_blob,
            user_description = user_description,
            user_notes = user_notes,
            modified_at = modified_at,
            original_fiat_type = original_fiat_type,
            original_fiat_rate = original_fiat_rate,
            lightning_address = lightning_address
        )

        return WalletPaymentInfo(payment, metadata, null)
    }

    suspend fun getOldestCompletedDate(): Long? = withContext(Dispatchers.Default) {
        database.paymentsQueries.getOldestCompletedAt().executeAsOneOrNull()?.completed_at
    }

    suspend fun countCompletedInRange(startDate: Long, endDate: Long): Long = withContext(Dispatchers.Default) {
        database.paymentsQueries.countCompletedInRange(completed_at_from = startDate, completed_at_to = endDate).executeAsOne()
    }

    suspend fun updateUserInfo(id: UUID, userDescription: String?, userNotes: String?) = withContext(Dispatchers.Default) {
        metadataQueries.updateUserInfo(id = id, userDescription = userDescription, userNotes = userNotes)
    }

    /**
     * @param notify Set to false if `didDeleteWalletPayment` should not be invoked.
     */
    suspend fun deletePayment(paymentId: UUID, notify: Boolean = true): Unit = withContext(Dispatchers.Default) {
        database.transaction {
            database.paymentsIncomingQueries.deleteById(id = paymentId)
            if (database.paymentsIncomingQueries.changes().executeAsOne() == 0L) {
                database.paymentsOutgoingQueries.deleteById(id = paymentId)
            }
            if (notify) {
                didDeleteWalletPayment(paymentId, database)
            }
        }
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
                                database.paymentsOutgoingQueries.deleteById(manualLiquidityPayment.id)
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

    fun close() {
        contacts.cancel()
        driver.close()
    }
}
