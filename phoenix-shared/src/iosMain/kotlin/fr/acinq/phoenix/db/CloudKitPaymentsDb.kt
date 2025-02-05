package fr.acinq.phoenix.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.coroutines.asFlow
import fr.acinq.lightning.db.*
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.db.payments.*
import kotlin.collections.List
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.pow
import kotlin.math.sqrt

class CloudKitPaymentsDb(
    private val paymentsDb: SqlitePaymentsDb
): CoroutineScope by MainScope() {

    private val db: Transacter = paymentsDb.database
    private val queries = paymentsDb.database.cloudKitPaymentsQueries

    /**
     * Provides a flow of the count of items within the cloudkit_payments_queue table.
     */
    private val _queueCount = MutableStateFlow<Long>(0)
    val queueCount: StateFlow<Long> = _queueCount.asStateFlow()

    data class MetadataRow(
        val unpaddedSize: Long,
        val recordCreation: Long,
        val recordBlob: ByteArray
    )

    data class FetchQueueBatchResult(

        // The fetched rowid values from the `cloudkit_payments_queue` table
        val rowids: List<Long>,

        // Maps `cloudkit_payments_queue.rowid` to the corresponding PaymentRowId.
        // If missing from the map, then the `cloudkit_payments_queue` row was
        // malformed or unrecognized.
        val rowidMap: Map<Long, UUID>,

        // Maps to the fetch payment information in the database.
        // If missing from the map, then the payment has been deleted from the database.
        val rowMap: Map<UUID, WalletPaymentInfo>,

        // Maps to `cloudkit_payments_metadata.ckrecord_info`.
        // If missing from the map, then then record doesn't exist in the database.
        val metadataMap: Map<UUID, ByteArray>
    )

    init {
        // N.B.: There appears to be a subtle bug in SQLDelight's
        // `.asFlow().mapToX()`, as described here:
        // https://github.com/ACINQ/phoenix/pull/415
        launch {
            queries.fetchQueueCount()
                .asFlow()
                .map {
                    withContext(Dispatchers.Default) {
                        db.transactionWithResult {
                            it.executeAsOne()
                        }
                    }
                }
                .collect { count ->
                    _queueCount.value = count
                }
        }
    }

    suspend fun fetchQueueBatch(limit: Long): FetchQueueBatchResult {
        return withContext(Dispatchers.Default) {

            val ckQueries = paymentsDb.database.cloudKitPaymentsQueries

            val rowids = mutableListOf<Long>()
            val rowidMap = mutableMapOf<Long, UUID>()
            val rowMap = mutableMapOf<UUID, WalletPaymentInfo>()
            val metadataMap = mutableMapOf<UUID, ByteArray>()

            db.transaction {

                // Step 1 of 4:
                // Fetch the rows from the `cloudkit_payments_queue` batch.
                // We are fetching the next/oldest X rows from the queue.

                val batch = ckQueries.fetchQueueBatch(limit).executeAsList()

                // Step 2 of 4:
                // Process the batch, and fill out the `rowids` & `rowidMap` variable.

                batch.forEach { row ->
                    rowids.add(row.rowid)
                    rowidMap[row.rowid] = row.id
                } // </batch.forEach>

                // Remember: there could be duplicates
                val uniquePaymentIds = rowidMap.values.toSet()

                // Step 3 of 4:
                // Fetch the corresponding payment info from the database.
                // In order to optimize disk access, we fetch from 1 table at a time.

                val metadataPlaceholder = WalletPaymentMetadata()

                uniquePaymentIds.forEach { paymentId ->
                    paymentsDb._getPayment(paymentId)?.let { pair ->
                        rowMap[paymentId] = WalletPaymentInfo(
                            payment = pair.first,
                            metadata = metadataPlaceholder,
                            contact = null
                        )
                    }
                }

                uniquePaymentIds.forEach { paymentId ->
                    paymentsDb.metadataQueries.get(paymentId)?.let { metadata ->
                        rowMap[paymentId]?.let {
                            rowMap[paymentId] = it.copy(
                                metadata = metadata,
                                contact = null
                            )
                        }
                    }
                } // </payments_metadata>

                // Step 4 of 4:
                // Fetch the corresponding `cloudkit_payments_metadata.ckrecord_info`

                uniquePaymentIds.forEach { paymentId ->
                    ckQueries.fetchMetadata(paymentId).executeAsOneOrNull()?.let { row ->
                        metadataMap[paymentId] = row.record_blob
                    }
                }

            } // </db.transaction>

            FetchQueueBatchResult(
                rowids = rowids,
                rowidMap = rowidMap,
                rowMap = rowMap,
                metadataMap = metadataMap
            )
        }
    }

    suspend fun updateRows(
        deleteFromQueue: List<Long>,
        deleteFromMetadata: List<UUID>,
        updateMetadata: Map<UUID, MetadataRow>
    ) {
        withContext(Dispatchers.Default) {
            db.transaction {

                deleteFromQueue.forEach { rowid ->
                    queries.deleteFromQueue(rowid)
                }

                deleteFromMetadata.forEach { paymentId ->
                    queries.deleteMetadata(paymentId)
                }

                updateMetadata.forEach { (paymentId, row) ->
                    val rowExists = queries.existsMetadata(paymentId).executeAsOne() > 0
                    if (rowExists) {
                        queries.updateMetadata(
                            unpadded_size = row.unpaddedSize,
                            record_blob = row.recordBlob,
                            id = paymentId
                        )
                    } else {
                        queries.addMetadata(
                            id = paymentId,
                            unpadded_size = row.unpaddedSize,
                            record_creation = row.recordCreation,
                            record_blob = row.recordBlob
                        )
                    }
                }
            }
        }
    }

    suspend fun fetchMetadata(
        id: UUID
    ): ByteArray? = withContext(Dispatchers.Default) {

        val row = queries.fetchMetadata(id = id).executeAsOneOrNull()
        row?.record_blob
    }

    suspend fun fetchOldestCreation(): Long? = withContext(Dispatchers.Default) {

        val row = queries.fetchOldestCreation().executeAsOneOrNull()
        row?.record_creation
    }

    suspend fun updateRows(
        downloadedPayments: List<WalletPayment>,
        downloadedPaymentsMetadata: Map<UUID, WalletPaymentMetadataRow>,
        updateMetadata: Map<UUID, MetadataRow>
    ) {
        withContext(Dispatchers.Default) {

            val inQueries = paymentsDb.database.paymentsIncomingQueries
            val outQueries = paymentsDb.database.paymentsOutgoingQueries
            val ckQueries = paymentsDb.database.cloudKitPaymentsQueries
            val metaQueries = paymentsDb.database.paymentsMetadataQueries

            val incomingPaymentsDb = SqliteIncomingPaymentsDb(paymentsDb.database)
            val outgoingPaymentsDb = SqliteOutgoingPaymentsDb(paymentsDb.database)

            db.transaction {

                downloadedPayments.forEach { payment ->

                    val paymentId: UUID = payment.id
                    if (payment is IncomingPayment) {
                        val existing = inQueries.get(paymentId).executeAsOneOrNull()
                        if (existing == null) {
                            incomingPaymentsDb._addIncomingPayment(payment, notify = false)
                        }
                    } else if (payment is OutgoingPayment) {
                        val existing = outQueries.get(paymentId).executeAsOneOrNull()
                        if (existing == null) {
                            outgoingPaymentsDb._addOutgoingPayment(payment, notify = false)
                        }
                    }
                }

                downloadedPaymentsMetadata.forEach { (paymentId, row) ->
                    val rowExists = metaQueries.hasMetadata(paymentId).executeAsOne() > 0
                    if (!rowExists) {
                        metaQueries.addMetadata(
                            payment_id = paymentId,
                            lnurl_base_type = row.lnurl_base?.first,
                            lnurl_base_blob = row.lnurl_base?.second,
                            lnurl_metadata_type = row.lnurl_metadata?.first,
                            lnurl_metadata_blob = row.lnurl_metadata?.second,
                            lnurl_successAction_type = row.lnurl_successAction?.first,
                            lnurl_successAction_blob = row.lnurl_successAction?.second,
                            lnurl_description = row.lnurl_description,
                            user_description = row.user_description,
                            user_notes = row.user_notes,
                            modified_at = row.modified_at,
                            original_fiat_type = row.original_fiat?.first,
                            original_fiat_rate = row.original_fiat?.second
                        )
                    }
                } // </payments_metadata table>

                updateMetadata.forEach { (paymentId, row) ->
                    val rowExists = ckQueries.existsMetadata(paymentId).executeAsOne() > 0
                    if (rowExists) {
                        ckQueries.updateMetadata(
                            unpadded_size = row.unpaddedSize,
                            record_blob = row.recordBlob,
                            id = paymentId
                        )
                    } else {
                        ckQueries.addMetadata(
                            id = paymentId,
                            unpadded_size = row.unpaddedSize,
                            record_creation = row.recordCreation,
                            record_blob = row.recordBlob
                        )
                    }
                } // </cloudkit_payments_metadata table>
            }
        }
    }

    suspend fun enqueueOutdatedItems() = withContext(Dispatchers.Default) {

        val ckQueries = paymentsDb.database.cloudKitPaymentsQueries
        db.transaction {

            val paymentIds = mutableListOf<UUID>()
            ckQueries.listNonZeroSizes().executeAsList().forEach { row ->
                paymentIds.add(row.id)
            }

            for (paymentId in paymentIds) {
                ckQueries.addToQueue(
                    id = paymentId,
                    date_added = currentTimestampMillis()
                )
            }
        }
    }

    data class MissingItem(
        val paymentId: UUID,
        val timestamp: Long
    )

    suspend fun enqueueMissingItems() {
        withContext(Dispatchers.Default) {

            val ckQueries = paymentsDb.database.cloudKitPaymentsQueries
            db.transaction {

                // Step 1 of 3:
                // Fetch list of payment ID's that are already represented in the cloud.

                val existing = mutableSetOf<UUID>()
                ckQueries.scanMetadata().executeAsList().forEach { uuid ->
                    existing.add(uuid)
                }

                // Step 2 of 3:
                // Scan local payment ID's, looking to see if any are missing from the cloud.

                val missing = mutableListOf<MissingItem>()
                run {
                    val inQueries = paymentsDb.database.paymentsIncomingQueries
                    inQueries.listSuccessfulIds().executeAsList().forEach { row ->
                        if (!existing.contains(row.id)) {
                            missing.add(MissingItem(row.id, row.received_at))
                        }
                    }
                }
                run {
                    val outQueries = paymentsDb.database.paymentsOutgoingQueries
                    outQueries.listSuccessfulIds().executeAsList().forEach { row ->
                        if (!existing.contains(row.id)) {
                            missing.add(MissingItem(row.id, row.completed_at))
                        }
                    }
                }

                // Step 3 of 3:
                // Add any missing items to the queue.
                //
                // But in what order do we want to upload them to the cloud ?
                //
                // We will choose to upload the OLDEST item first.
                // This matches how they normally would have been uploaded.
                // Also, when a user restores their wallet (e.g. on a new phone),
                // we always want to download the newest payments first.
                // And this assumes the newest items in the cloud are the newest payments.
                //
                // Since items are uploaded in FIFO order,
                // we just need to make the oldest item have the
                // smallest `date_added` value.

                missing.sortByDescending { it.timestamp }

                // The list is now sorted in descending order.
                // Which means the newest item is at index 0,
                // and the oldest item is at index <last>.

                val now = currentTimestampMillis()
                missing.forEachIndexed { idx, item ->
                    ckQueries.addToQueue(
                        id = item.paymentId,
                        date_added = now - idx
                    )
                }
            }
        }
    }

    suspend fun clearDatabaseTables() {
        withContext(Dispatchers.Default) {
            db.transaction {
                queries.deleteAllFromMetadata()
                queries.deleteAllFromQueue()
            }
        }
    }
}
