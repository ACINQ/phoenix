/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.managers

import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.managers.global.CurrencyManager
import kotlinx.coroutines.flow.MutableStateFlow


/**
 * The lightning-kmp layer triggers the addition of a payment to the database.
 * But sometimes there is associated metadata that we want to include,
 * and we would like to write it to the database within the same transaction.
 * So we have a system to enqueue/dequeue associated metadata.
 */
class PaymentMetadataQueue(
    private val appConfigurationManager: AppConfigurationManager,
    private val currencyManager: CurrencyManager,
) {

    private var metadataQueue = MutableStateFlow(mapOf<UUID, WalletPaymentMetadata>())

    /** Adds a payment metadata to the queue. */
    fun enqueue(row: WalletPaymentMetadata, id: UUID) {
        val oldMap = metadataQueue.value
        val newMap = oldMap + (id to row)
        metadataQueue.value = newMap
    }

    /** Pops a payment metadata from the queue if it exists. */
    internal fun dequeue(id: UUID): WalletPaymentMetadata? {
        val oldMap = metadataQueue.value
        val newMap = oldMap - id
        metadataQueue.value = newMap
        return oldMap[id]
    }

    /**
     * Ensures the given payment metadata contains a fiat exchange rate. It it does not have a fiat rate already,
     * fetch the current primary rate provided by the currency manager.
     */
    internal fun enrichPaymentMetadata(metadata: WalletPaymentMetadata?): WalletPaymentMetadata {
        val metadataOrDefault = metadata ?: WalletPaymentMetadata()
        return if (metadataOrDefault.originalFiat == null) {
            val currentFiatRate = appConfigurationManager.preferredFiatCurrencies.value?.let { currencyManager.calculateOriginalFiat(it.primary) }
            metadataOrDefault.copy(originalFiat = currentFiatRate)
        } else metadataOrDefault
    }
}