package fr.acinq.phoenix.db

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.db.payments.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.kodein.memory.util.freeze
import kotlin.math.pow
import kotlin.math.sqrt

class CloudKitDb(
    private val database: PaymentsDatabase
): CloudKitInterface, CoroutineScope by MainScope() {

    /**
     * Provides a flow of the count of items within the cloudkit_payments_queue table.
     */
    private val _queueCount = MutableStateFlow<Long>(0)
    val queueCount: StateFlow<Long> = _queueCount

    init {
        launch {
            val ckQueries = database.cloudKitPaymentsQueries
            val fetchQueueCountFlow = ckQueries.fetchQueueCount().asFlow().mapToOne()

            fetchQueueCountFlow.collect { count ->
                _queueCount.value = count
            }
        }
    }

    data class MetadataRow(
        val unpaddedSize: Long,
        val recordCreation: Long,
        val recordBlob: ByteArray
    )

    data class MetadataStats(
        val mean: Double,
        val standardDeviation: Double
    ) {
        constructor(): this(mean = 0.0, standardDeviation = 0.0)
    }

    data class FetchQueueBatchResult(

        // The fetched rowid values from the `cloudkit_payments_queue` table
        val rowids: List<Long>,

        // Maps `cloudkit_payments_queue.rowid` to the corresponding PaymentRowId.
        // If missing from the map, then the `cloudkit_payments_queue` row was
        // malformed or unrecognized.
        val rowidMap: Map<Long, WalletPaymentId>,

        // Maps to the fetch payment information in the database.
        // If missing from the map, then the payment has been deleted from the database.
        val rowMap: Map<WalletPaymentId, WalletPaymentInfo>,

        // Maps to `cloudkit_payments_metadata.ckrecord_info`.
        // If missing from the map, then then record doesn't exist in the database.
        val metadataMap: Map<WalletPaymentId, ByteArray>,

        // The `cloudkit_payments_metadata` stores the size of the non-padded record.
        // Statistics about these values are returned, rowMap is non-empty.
        val incomingStats: MetadataStats,
        val outgoingStats: MetadataStats
    )

    suspend fun fetchQueueBatch(limit: Long): FetchQueueBatchResult {
        return withContext(Dispatchers.Default) {

            val ckQueries = database.cloudKitPaymentsQueries
            val inQueries = IncomingQueries(database)
            val outQueries = OutgoingQueries(database)
            val metaQueries = MetadataQueries(database)

            val rowids = mutableListOf<Long>()
            val rowidMap = mutableMapOf<Long, WalletPaymentId>()
            val rowMap = mutableMapOf<WalletPaymentId, WalletPaymentInfo>()
            val metadataMap = mutableMapOf<WalletPaymentId, ByteArray>()
            var incomingStats = MetadataStats()
            var outgoingStats = MetadataStats()

            database.transaction {

                // Step 1 of 5:
                // Fetch the rows from the `cloudkit_payments_queue` batch.
                // We are fetching the next/oldest X rows from the queue.

                val batch = ckQueries.fetchQueueBatch(limit).executeAsList()

                // Step 2 of 5:
                // Process the batch, and fill out the `rowids` & `rowidMap` variable.

                batch.forEach { row ->
                    rowids.add(row.rowid)
                    when (row.type) {
                        WalletPaymentId.DbType.INCOMING.value -> {
                            try {
                                WalletPaymentId.IncomingPaymentId.fromString(row.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        WalletPaymentId.DbType.OUTGOING.value -> {
                            try {
                                WalletPaymentId.OutgoingPaymentId.fromString(row.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        else -> null
                    }?.let { paymentId ->
                        rowidMap[row.rowid] = paymentId
                    }
                } // </batch.forEach>

                // Remember: there could be duplicates
                val uniquePaymentIds = rowidMap.values.toSet()

                // Step 3 of 5:
                // Fetch the corresponding payment info from the database.
                // Depending on the type of WalletPaymentId, this will be either:
                // - IncomingPayment
                // - OutgoingPayment
                //
                // In order to optimize disk access, we fetch from 1 table at a time.

                val metadataPlaceholder = WalletPaymentMetadata()

                uniquePaymentIds.filterIsInstance<
                    WalletPaymentId.IncomingPaymentId
                >().forEach { paymentId ->
                    inQueries.getIncomingPayment(
                        paymentHash = paymentId.paymentHash
                    )?.let { payment ->
                        rowMap[paymentId] = WalletPaymentInfo(payment, metadataPlaceholder)
                    }
                } // </incoming_payments>

                uniquePaymentIds.filterIsInstance<
                    WalletPaymentId.OutgoingPaymentId
                >().forEach { paymentId ->
                    outQueries.getOutgoingPayment(
                        id = paymentId.id
                    )?.let { payment ->
                        rowMap[paymentId] = WalletPaymentInfo(payment, metadataPlaceholder)
                    }
                } // </outgoing_payments>

                val fetchOptions = WalletPaymentFetchOptions.All
                uniquePaymentIds.forEach { paymentId ->
                    metaQueries.getMetadata(paymentId, fetchOptions)?.let { metadata ->
                        rowMap[paymentId]?.let {
                            rowMap[paymentId] = it.copy(metadata = metadata)
                        }
                    }
                } // </payments_metadata>

                // Step 4 of 5:
                // Fetch the corresponding `cloudkit_payments_metadata.ckrecord_info`

                uniquePaymentIds.forEach { paymentId ->

                    ckQueries.fetchMetadata(
                        type = paymentId.dbType.value,
                        id = paymentId.dbId
                    ).executeAsOneOrNull()?.let { row ->
                        metadataMap[paymentId] = row.record_blob
                    }
                }

                // Step 5 of 5:
                // Fetch the metadata statistics (if needed).

                if (rowMap.isNotEmpty()) {

                    val process = { list: List<Long> ->
                        val mean = list.sum().toDouble() / list.size.toDouble()
                        val variance = list.map {
                            val diff = it.toDouble() - mean
                            diff.pow(2)
                        }.sum()
                        val standardDeviation = sqrt(variance)

                        MetadataStats(
                            mean = mean,
                            standardDeviation = standardDeviation
                        )
                    }

                    var incoming = mutableListOf<Long>()
                    var outgoing = mutableListOf<Long>()

                    ckQueries.scanSizes().executeAsList().forEach { row ->
                        if (row.unpadded_size > 0) {
                            when (row.type) {
                                WalletPaymentId.DbType.INCOMING.value ->
                                    incoming.add(row.unpadded_size)
                                WalletPaymentId.DbType.OUTGOING.value ->
                                    outgoing.add(row.unpadded_size)
                            }
                        }
                    }

                    incomingStats = process(incoming)
                    outgoingStats = process(outgoing)
                }

            } // </database.transaction>

            FetchQueueBatchResult(
                rowids = rowids,
                rowidMap = rowidMap,
                rowMap = rowMap,
                metadataMap = metadataMap,
                incomingStats = incomingStats,
                outgoingStats = outgoingStats
            )
        }
    }

    suspend fun updateRows(
        deleteFromQueue: List<Long>,
        deleteFromMetadata: List<WalletPaymentId>,
        updateMetadata: Map<WalletPaymentId, MetadataRow>
    ) {
        // We are seeing crashes when accessing the ByteArray values in updateMetadata.
        // So we need a workaround.
        val sanitizedMetadata = sanitizeMetadata(updateMetadata)

        withContext(Dispatchers.Default) {
            val ckQueries = database.cloudKitPaymentsQueries

            database.transaction {

                deleteFromQueue.forEach { rowid ->
                    ckQueries.deleteFromQueue(rowid)
                }

                deleteFromMetadata.forEach { paymentId ->
                    ckQueries.deleteMetadata(
                        type = paymentId.dbType.value,
                        id = paymentId.dbId
                    )
                }

                sanitizedMetadata.forEach { (paymentId, row) ->
                    val rowExists = ckQueries.existsMetadata(
                        type = paymentId.dbType.value,
                        id = paymentId.dbId
                    ).executeAsOne() > 0
                    if (rowExists) {
                        ckQueries.updateMetadata(
                            unpadded_size = row.unpaddedSize,
                            record_blob = row.recordBlob,
                            type = paymentId.dbType.value,
                            id = paymentId.dbId
                        )
                    } else {
                        ckQueries.addMetadata(
                            type = paymentId.dbType.value,
                            id = paymentId.dbId,
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
        type: Long,
        id: String
    ): ByteArray? {

        return withContext(Dispatchers.Default) {
            val ckQueries = database.cloudKitPaymentsQueries

            val row = ckQueries.fetchMetadata(type = type, id = id).executeAsOneOrNull()
            row?.record_blob
        }
    }

    suspend fun fetchOldestCreation(): Long? {

        return withContext(Dispatchers.Default) {
            val ckQueries = database.cloudKitPaymentsQueries

            val row = ckQueries.fetchOldestCreation().executeAsOneOrNull()
            row?.record_creation
        }
    }

    suspend fun updateRows(
        downloadedPayments: List<WalletPayment>,
        downloadedPaymentsMetadata: Map<WalletPaymentId, WalletPaymentMetadataRow>,
        updateMetadata: Map<WalletPaymentId, MetadataRow>
    ) {
        // We are seeing crashes when accessing the values within the List<PaymentRow>.
        // Perhaps because the List was created in Swift ?
        // The workaround seems to be to copy the list here,
        // or otherwise process it outside of the `withContext` below.
        val incomingList = downloadedPayments.mapNotNull { it as? IncomingPayment }
        val outgoingList = downloadedPayments.mapNotNull { it as? OutgoingPayment }

        // We are seeing crashes when accessing the ByteArray values in updateMetadata.
        // So we need a workaround.
        val sanitizedMetadata = sanitizeMetadata(updateMetadata)

        withContext(Dispatchers.Default) {

            val ckQueries = database.cloudKitPaymentsQueries
            val inQueries = database.incomingPaymentsQueries
            val outQueries = database.outgoingPaymentsQueries
            val metaQueries = database.paymentsMetadataQueries

            database.transaction {

                for (incomingPayment in incomingList) {

                    val existing = inQueries.get(
                        payment_hash = incomingPayment.paymentHash.toByteArray(),
                        mapper = IncomingQueries.Companion::mapIncomingPayment
                    ).executeAsOneOrNull()

                    if (existing == null) {
                        val (originType, originData) = incomingPayment.origin.mapToDb()
                        inQueries.insert(
                            payment_hash = incomingPayment.paymentHash.toByteArray(),
                            preimage = incomingPayment.preimage.toByteArray(),
                            origin_type = originType,
                            origin_blob = originData,
                            created_at = incomingPayment.createdAt
                        )
                    }

                    val oldReceived = existing?.received
                    val received = incomingPayment.received

                    if (oldReceived == null && received != null) {
                        val (type, blob) = received.receivedWith.mapToDb() ?: null to null
                        inQueries.updateReceived(
                            received_at = received.receivedAt,
                            received_with_type = type,
                            received_with_blob = blob,
                            payment_hash = incomingPayment.paymentHash.toByteArray()
                        )
                    }
                } // </incoming_payments table>

                for (outgoingPayment in outgoingList) {

                    val existing = outQueries.getOutgoingPaymentWithoutParts(
                        id = outgoingPayment.id.toString(),
                        mapper = OutgoingQueries.Companion::mapOutgoingPaymentWithoutParts
                    ).executeAsOneOrNull()

                    if (existing == null) {
                        val (detailsTypeVersion, detailsData) = outgoingPayment.details.mapToDb()
                        database.outgoingPaymentsQueries.addOutgoingPayment(
                            id = outgoingPayment.id.toString(),
                            recipient_amount_msat = outgoingPayment.recipientAmount.msat,
                            recipient_node_id = outgoingPayment.recipient.toString(),
                            payment_hash = outgoingPayment.details.paymentHash.toByteArray(),
                            created_at = outgoingPayment.createdAt,
                            details_type = detailsTypeVersion,
                            details_blob = detailsData
                        )
                    }

                    for (part in outgoingPayment.parts) {

                        val partRowExists = outQueries.hasOutgoingPart(
                            part_id = part.id.toString()
                        ).executeAsOne() > 0
                        if (!partRowExists) {
                            outQueries.addOutgoingPart(
                                part_id = part.id.toString(),
                                part_parent_id = outgoingPayment.id.toString(),
                                part_amount_msat = part.amount.msat,
                                part_route = part.route,
                                part_created_at = part.createdAt
                            )

                            val statusInfo = when (val status = part.status) {
                                is OutgoingPayment.Part.Status.Failed ->
                                    status.completedAt to status.mapToDb()
                                is OutgoingPayment.Part.Status.Succeeded ->
                                    status.completedAt to status.mapToDb()
                                else -> null
                            }
                            if (statusInfo != null) {
                                val completedAt = statusInfo.first
                                val (type, blob) = statusInfo.second
                                outQueries.updateOutgoingPart(
                                    part_id = part.id.toString(),
                                    part_status_type = type,
                                    part_status_blob = blob,
                                    part_completed_at = completedAt
                                )
                            }
                        }
                    }

                    val oldCompleted = existing?.status as? OutgoingPayment.Status.Completed
                    val completed = outgoingPayment.status as? OutgoingPayment.Status.Completed

                    if (oldCompleted == null && completed != null) {
                        val (statusType, statusBlob) = completed.mapToDb()
                        outQueries.updateOutgoingPayment(
                            id = outgoingPayment.id.toString(),
                            completed_at = completed.completedAt,
                            status_type = statusType,
                            status_blob = statusBlob
                        )
                    }
                } // </outgoing_payments table>

                downloadedPaymentsMetadata.forEach { (paymentId, row) ->
                    val rowExists = metaQueries.hasMetadata(
                        type = paymentId.dbType.value,
                        id = paymentId.dbId
                    ).executeAsOne() > 0
                    if (!rowExists) {
                        metaQueries.addMetadata(
                            type = paymentId.dbType.value,
                            id = paymentId.dbId,
                            lnurl_base_type = row.lnurl_base?.first,
                            lnurl_base_blob = row.lnurl_base?.second,
                            lnurl_metadata_type = row.lnurl_metadata?.first,
                            lnurl_metadata_blob = row.lnurl_metadata?.second,
                            lnurl_successAction_type = row.lnurl_successAction?.first,
                            lnurl_successAction_blob = row.lnurl_successAction?.second,
                            lnurl_description = row.lnurl_description,
                            user_description = row.user_description
                        )
                    }
                } // </payments_metadata table>

                sanitizedMetadata.forEach { (paymentId, row) ->
                    val rowExists = ckQueries.existsMetadata(
                        type = paymentId.dbType.value,
                        id = paymentId.dbId
                    ).executeAsOne() > 0
                    if (rowExists) {
                        ckQueries.updateMetadata(
                            unpadded_size = row.unpaddedSize,
                            record_blob = row.recordBlob,
                            type = paymentId.dbType.value,
                            id = paymentId.dbId
                        )
                    } else {
                        ckQueries.addMetadata(
                            type = paymentId.dbType.value,
                            id = paymentId.dbId,
                            unpadded_size = row.unpaddedSize,
                            record_creation = row.recordCreation,
                            record_blob = row.recordBlob
                        )
                    }
                } // </cloudkit_payments_metadata table>
            }
        }
    }

    private fun sanitizeMetadata(
        metadata: Map<WalletPaymentId, MetadataRow>
    ): Map<WalletPaymentId, MetadataRow> {

        // We are seeing crashes when accessing the ByteArray values in the map:
        // > "illegal attempt to access non-shared kotlin.ByteArray"
        //
        // This is perhaps because the Map was created in Swift, and passed to Kotlin.
        //
        return metadata.mapValues { (_, metadataRow) ->
            metadataRow.copy(
                recordBlob = metadataRow.recordBlob.copyOf().freeze()
            )
        }
    }

    data class MissingItem(
        val paymentId: WalletPaymentId,
        val timestamp: Long
    )

    suspend fun enqueueMissingItems(): Unit {
        withContext(Dispatchers.Default) {

            val ckQueries = database.cloudKitPaymentsQueries
            val inQueries = database.incomingPaymentsQueries
            val outQueries = database.outgoingPaymentsQueries

            database.transaction {

                val existing = mutableSetOf<WalletPaymentId>()
                ckQueries.scanMetadata().executeAsList().forEach { row ->
                    WalletPaymentId.create(row.type, row.id)?.let {
                        existing.add(it)
                    }
                }

                val missing = mutableListOf<MissingItem>()

                inQueries.scanCompleted().executeAsList().forEach { row ->
                    val rowId = WalletPaymentId.IncomingPaymentId.fromByteArray(row.payment_hash)
                    if (!existing.contains(rowId)) {
                        missing.add(MissingItem(rowId, row.received_at))
                    }
                }

                outQueries.scanCompleted().executeAsList().forEach { row ->
                    val rowId = WalletPaymentId.OutgoingPaymentId.fromString(row.id)
                    if (!existing.contains(rowId)) {
                        missing.add(MissingItem(rowId, row.completed_at))
                    }
                }

                // Now we're going to add them to the database.
                // But in what order do we want to upload them to the cloud ?
                //
                // We will choose to upload the newest item first.
                // Since items are uploaded in FIFO order,
                // we just need to make the newest item have the
                // smallest `date_added` value.

                missing.sortBy { it.timestamp }

                // The list is now sorted in ascending order.
                // Which means the oldest payment is at index 0,
                // and the newest payment is at index <last>.

                val now = currentTimestampMillis()
                missing.forEachIndexed { idx, item ->
                    ckQueries.addToQueue(
                        type = item.paymentId.dbType.value,
                        id = item.paymentId.dbId,
                        date_added = now - idx
                    )
                }
            }
        }
    }

    suspend fun clearDatabaseTables(): Unit {
        withContext(Dispatchers.Default) {

            val ckQueries = database.cloudKitPaymentsQueries

            database.transaction {
                ckQueries.deleteAllFromMetadata()
                ckQueries.deleteAllFromQueue()
            }
        }
    }
}
