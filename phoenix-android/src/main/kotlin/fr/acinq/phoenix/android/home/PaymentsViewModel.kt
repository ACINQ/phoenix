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

package fr.acinq.phoenix.android.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.managers.Connections
import fr.acinq.phoenix.managers.PaymentsManager
import fr.acinq.phoenix.managers.PaymentsPageFetcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

data class PaymentRowState(
    val orderRow: WalletPaymentOrderRow,
    val paymentInfo: WalletPaymentInfo?
)

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModel(
    val connectionsFlow: StateFlow<Connections>,
    private val paymentsManager: PaymentsManager,
) : ViewModel() {

    /** How many payments should be fetched by the initial subscription. */
    private val initialPaymentsCount = 15

    /** How many payments should be visible in the home view. */
    private val latestPaymentsCount = 15

    private val log = LoggerFactory.getLogger(this::class.java)

    private val _paymentsFlow = MutableStateFlow<Map<String, PaymentRowState>>(HashMap())
    /**
     * A flow of known payments. The key is a [WalletPaymentId], the value is the payments details
     * which are basic at first, and then updated asynchronously (see [fetchPaymentDetails]).
     *
     * This flow is initialized by the view model, and then updated by [subscribeToPayments] which is
     * called by the UI when needed (paging with scrolling, see the payments history view).
     */
    val paymentsFlow: StateFlow<Map<String, PaymentRowState>> = _paymentsFlow.asStateFlow()

    /** A subset of [paymentsFlow] used in the Home view. */
    val latestPaymentsFlow: StateFlow<List<PaymentRowState>> = paymentsFlow.mapLatest {
        it.values.take(latestPaymentsCount.coerceAtMost(initialPaymentsCount)).toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    private val paymentsPageFetcher: PaymentsPageFetcher = paymentsManager.makePageFetcher()

    init {
        paymentsPageFetcher.subscribeToAll(offset = 0, count = initialPaymentsCount)

        // get details when a payment completes
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to collect last completed payment: ", e)
        }) {
            paymentsManager.lastCompletedPayment.filterNotNull().collect {
                // a new row object must be built to get a fresh cache key for the payment fetcher
                val row = WalletPaymentOrderRow(
                    id = it.walletPaymentId(),
                    createdAt = it.createdAt,
                    completedAt = it.completedAt,
                    metadataModifiedAt = null
                )
                fetchPaymentDetails(row)
            }
        }

        // collect changes on the payments page that we subscribed to
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to collect all payments page items: ", e)
        }) {
            paymentsPageFetcher.paymentsPage.collect { page ->
                viewModelScope.launch(Dispatchers.Default) {
                    // We must rewrite the whole payments flow map to keep payments ordering.
                    // Adding the diff would only push new elements to the bottom of the map.
                    _paymentsFlow.value = page.rows.associate { newRow ->
                        val paymentId = newRow.id.identifier
                        val existingData = paymentsFlow.value[paymentId]
                        // We look at the row to check if the payment has changed (the row contains timestamps)
                        if (existingData?.orderRow != newRow) {
                            paymentId to PaymentRowState(newRow, null)
                        } else {
                            paymentId to existingData
                        }
                    }
                }
            }
        }
    }

    /** Fetches the details for a given payment and updates [paymentsFlow]. */
    fun fetchPaymentDetails(row: WalletPaymentOrderRow) {
        viewModelScope.launch(Dispatchers.Default) {
            val paymentInfo = paymentsManager.fetcher.getPayment(row, WalletPaymentFetchOptions.Descriptions)
            if (paymentInfo != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    _paymentsFlow.value += (row.id.identifier to PaymentRowState(row, paymentInfo))
                }
            }
        }
    }

    /** Updates the payment fetcher to listen to changes within the given count and offset, indirectly updating the [paymentsFlow]. */
    fun subscribeToPayments(offset: Int, count: Int) {
        paymentsPageFetcher.subscribeToAll(offset, count)
    }

    class Factory(
        private val connectionsFlow: StateFlow<Connections>,
        private val paymentsManager: PaymentsManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PaymentsViewModel(connectionsFlow, paymentsManager) as T
        }
    }
}
