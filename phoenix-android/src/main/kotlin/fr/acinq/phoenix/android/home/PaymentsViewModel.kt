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
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.managers.Connections
import fr.acinq.phoenix.managers.PaymentsManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

data class PaymentRowState(
    val orderRow: WalletPaymentOrderRow,
    val paymentInfo: WalletPaymentInfo?
)

class PaymentsViewModel(
    val connectionsFlow: StateFlow<Connections>,
    val paymentsManager: PaymentsManager,
) : ViewModel() {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val _allPaymentsFlow = MutableStateFlow<Map<String, PaymentRowState>>(HashMap())
    val allPaymentsFlow: StateFlow<Map<String, PaymentRowState>> = _allPaymentsFlow

    private val _recentPaymentsFlow = MutableStateFlow<Map<String, PaymentRowState>>(HashMap())
    val recentPaymentsFlow: StateFlow<Map<String, PaymentRowState>> = _recentPaymentsFlow

    init {
        val allPaymentsPageFetcher = paymentsManager.makePageFetcher()
        allPaymentsPageFetcher.subscribeToAll(offset = 0, count = 5)

        val recentPaymentsPageFetcher = paymentsManager.makePageFetcher()
        recentPaymentsPageFetcher.subscribeToRecent(offset = 0, count = 5, seconds = (60 * 60 * 24 * 3))

        // get details when a payment completes
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to collect last completed payment: ", e)
        }) {
            paymentsManager.lastCompletedPayment.collect {
                // a new row object must be built to get a fresh cache key for the payment fetcher
                if (it != null) {
                    val row = WalletPaymentOrderRow(
                        id = it.walletPaymentId(),
                        createdAt = when (it) {
                            is OutgoingPayment -> it.createdAt
                            is IncomingPayment -> it.createdAt
                        },
                        completedAt = it.completedAt(),
                        metadataModifiedAt = null
                    )
                    getPaymentDescription(row)
                }
            }
        }

        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to collect all payments page items: ", e)
        }) {
            allPaymentsPageFetcher.paymentsPage.collect {
                viewModelScope.launch(Dispatchers.Default) {
                    // rewrite all the payments flow map to keep payments ordering - adding the diff would put new elements to the bottom of the map
                    _allPaymentsFlow.value = it.rows.associate { row ->
                        row.id.identifier to (_recentPaymentsFlow.value[row.id.identifier] ?: run {
                            PaymentRowState(row, null)
                        })
                    }
                }
            }
        }

        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("failed to collect recent payments page items: ", e)
        }) {
            recentPaymentsPageFetcher.paymentsPage.collect {
                viewModelScope.launch(Dispatchers.Default) {
                    // rewrite all the payments flow map to keep payments ordering - adding the diff would put new elements to the bottom of the map
                    _recentPaymentsFlow.value = it.rows.map { row ->
                        row.id.identifier to (_recentPaymentsFlow.value[row.id.identifier] ?: run {
                            PaymentRowState(row, null)
                        })
                    }.toMap()
                }
            }
        }
    }

    fun getPaymentDescription(row: WalletPaymentOrderRow) {
        viewModelScope.launch(Dispatchers.Default) {
            val paymentInfo = paymentsManager.fetcher.getPayment(row, WalletPaymentFetchOptions.Descriptions)
            if (paymentInfo != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    _recentPaymentsFlow.value += (row.id.identifier to PaymentRowState(row, paymentInfo))
                    _allPaymentsFlow.value += (row.id.identifier to PaymentRowState(row, paymentInfo))
                }
            }
        }
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
