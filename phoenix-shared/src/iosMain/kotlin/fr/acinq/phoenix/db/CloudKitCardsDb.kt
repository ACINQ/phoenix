package fr.acinq.phoenix.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.coroutines.asFlow
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.BoltCardInfo
import kotlin.collections.List
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CloudKitCardsDb(
    private val paymentsDb: SqlitePaymentsDb
): CoroutineScope by MainScope() {

    private val db: Transacter = paymentsDb.database
    private val queries = paymentsDb.database.cloudKitCardsQueries

    /**
     * Provides a flow of the count of items within the cloudkit_cards_queue table.
     */
    private val _queueCount = MutableStateFlow<Long>(0)
    val queueCount: StateFlow<Long> = _queueCount.asStateFlow()

    data class MetadataRow(
        val recordCreation: Long,
        val recordBlob: ByteArray
    )

    data class MissingItem(
        val cardId: UUID,
        val timestamp: Long
    )

    data class FetchQueueBatchResult(

        // The fetched rowid values from the `cloudkit_cards_queue` table
        val rowids: List<Long>,

        // Maps `cloudkit_cards_queue.rowid` to the corresponding cardId.
        // If missing from the map, then the `cloudkit_cards_queue` row was
        // malformed or unrecognized.
        val rowidMap: Map<Long, UUID>,

        // Maps to the card information in the database.
        // If missing from the map, then the card has been deleted from the database.
        val rowMap: Map<UUID, BoltCardInfo>,

        // Maps to `cloudkit_cards_metadata.ckrecord_info`.
        // If missing from the map, then then record doesn't exist in the database.
        val metadataMap: Map<UUID, ByteArray>,
    )

    init {
        // N.B.: There appears to be a subtle bug in SQLDelight's
        // `.asFlow().mapToX(Dispatchers.Default)`, as described here:
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

            val rowids = mutableListOf<Long>()
            val rowidMap = mutableMapOf<Long, UUID>()
            val rowMap = mutableMapOf<UUID, BoltCardInfo>()
            val metadataMap = mutableMapOf<UUID, ByteArray>()

            val cardQueries = paymentsDb.cards.cardQueries

            db.transaction {

                // Step 1 of 3:
                // Fetch the rows from the `cloudkit_cards_queue` batch.
                // We are fetching the next/oldest X rows from the queue.

                val batch = queries.fetchQueueBatch(limit).executeAsList()

                // Step 2 of 3:
                // Process the batch, and fill out the `rowids` & `rowidMap` variable.

                batch.forEach { row ->
                    rowids.add(row.rowid)
                    try {
                        val cardId = UUID.fromString(row.id)
                        rowidMap[row.rowid] = cardId
                    } catch (e: Exception) {
                        // UUID appears to be malformed within the database.
                        // Nothing we can do here - but let's at least not crash.
                    }
                } // </batch.forEach>

                // Remember: there could be duplicates
                val uniqueCardIds = rowidMap.values.toSet()

                // Step 3 of 3:
                // Fetch the corresponding card info from the database.

                uniqueCardIds.forEach { cardId ->
                    cardQueries.getCard(cardId)?.let {
                        rowMap[cardId] = it
                    }
                }

                // Step 4 of 5:
                // Fetch the corresponding `cloudkit_cards_metadata.ckrecord_info`

                uniqueCardIds.forEach { cardId ->
                    queries.fetchMetadata(
                        id = cardId.toString()
                    ).executeAsOneOrNull()?.let { row ->
                        metadataMap[cardId] = row.record_blob
                    }
                }

            } // </database.transaction>

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

                deleteFromMetadata.forEach { cardId ->
                    queries.deleteMetadata(
                        id = cardId.toString()
                    )
                }

                updateMetadata.forEach { (cardId, row) ->
                    val rowExists = queries.existsMetadata(
                        id = cardId.toString()
                    ).executeAsOne() > 0
                    if (rowExists) {
                        queries.updateMetadata(
                            record_blob = row.recordBlob,
                            id = cardId.toString()
                        )
                    } else {
                        queries.addMetadata(
                            id = cardId.toString(),
                            record_creation = row.recordCreation,
                            record_blob = row.recordBlob
                        )
                    }
                }
            }
        }
    }

    suspend fun fetchOldestCreation(): Long? {
        return withContext(Dispatchers.Default) {
            val row = queries.fetchOldestCreation_Cards().executeAsOneOrNull()
            row?.record_creation
        }
    }

    suspend fun updateRows(
        downloadedCards: List<BoltCardInfo>,
        updateMetadata: Map<UUID, MetadataRow>
    ) {
        // We are seeing crashes when accessing the values within the List<BoltCardInfo>.
        // Perhaps because the List was created in Swift ?
        // The workaround seems to be to copy the list here,
        // or otherwise process it outside of the `withContext` below.
        val cards = downloadedCards.map { it.copy() }

        withContext(Dispatchers.Default) {
            val cardQueries = paymentsDb.cards.cardQueries

            db.transaction {
                for (card in cards) {

                    val rowExists = queries.existsMetadata(
                        id = card.id.toString()
                    ).executeAsOne() > 0
                    if (!rowExists) {
                        cardQueries.saveCard(card, notify = false)
                    }
                }

                for ((cardId, row) in updateMetadata) {
                    val rowExists = queries.existsMetadata(
                        id = cardId.toString()
                    ).executeAsOne() > 0

                    if (rowExists) {
                        queries.updateMetadata(
                            record_blob = row.recordBlob,
                            id = cardId.toString()
                        )
                    } else {
                        queries.addMetadata(
                            id = cardId.toString(),
                            record_creation = row.recordCreation,
                            record_blob = row.recordBlob
                        )
                    }
                } // </cloudkit_card_metadata table>
            }
        }
    }

    suspend fun enqueueMissingItems() {
        withContext(Dispatchers.Default) {
            val rawCardQueries = paymentsDb.database.boltCardsQueries

            db.transaction {

                // Step 1 of 3:
                // Fetch list of card ID's that are already represented in the cloud.

                val cloudCardIds = mutableSetOf<UUID>()
                queries.scanMetadata().executeAsList().forEach { id ->
                    try {
                        val cardId = UUID.fromString(id)
                        cloudCardIds.add(cardId)
                    } catch (e: Exception) {
                        // UUID appears to be malformed within the database.
                        // Nothing we can do here - but let's at least not crash.
                    }
                }

                // Step 2 of 3:
                // Scan local card ID's, looking to see if any are missing from the cloud.

                val missing = mutableListOf<MissingItem>()
                rawCardQueries.scanCards().executeAsList().forEach { row ->
                    try {
                        val cardId = UUID.fromString(row.id)
                        if (!cloudCardIds.contains(cardId)) {
                            missing.add(MissingItem(
                                cardId = cardId,
                                timestamp = row.created_at
                            ))
                        }
                    } catch (e: Exception) {
                        // UUID appears to be malformed within the database.
                        // Nothing we can do here - but let's at least not crash.
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
                // we always want to download the newest items first.
                // And this assumes the newest items in the cloud are the newest cards.
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
                    queries.addToQueue(
                        id = item.cardId.toString(),
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
