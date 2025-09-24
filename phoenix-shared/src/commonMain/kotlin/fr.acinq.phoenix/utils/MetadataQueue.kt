package fr.acinq.phoenix.utils

import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.data.WalletPaymentMetadata
import kotlinx.coroutines.flow.MutableStateFlow

class MetadataQueue() {

    private var metadataQueue = MutableStateFlow(mapOf<UUID, WalletPaymentMetadata>())

    /**
     * The lightning-kmp layer triggers the addition of a payment to the database.
     * But sometimes there is associated metadata that we want to include,
     * and we would like to write it to the database within the same transaction.
     * So we have a system to enqueue/dequeue associated metadata.
     */
    fun enqueue(row: WalletPaymentMetadata, id: UUID) {
        val oldMap = metadataQueue.value
        val newMap = oldMap + (id to row)
        metadataQueue.value = newMap
    }

    /**
     * Returns any enqueued metadata, and also appends the current fiat exchange rate.
     */
    internal fun dequeue(id: UUID): WalletPaymentMetadata {
        val oldMap = metadataQueue.value
        val newMap = oldMap - id
        metadataQueue.value = newMap

        val row = oldMap[id] ?: WalletPaymentMetadata()
        return row
    }
}