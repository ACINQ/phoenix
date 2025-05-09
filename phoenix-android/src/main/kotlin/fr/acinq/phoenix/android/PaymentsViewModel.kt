/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.managers.PaymentsManager
import fr.acinq.phoenix.managers.PaymentsPageFetcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModel(
    paymentsManager: PaymentsManager,
) : ViewModel() {

    companion object {
        const val pageSize = 60
        const val paymentsCountInHome = 10
    }

    private val log = LoggerFactory.getLogger(this::class.java)

    private val paymentsPageFetcher: PaymentsPageFetcher = paymentsManager.makePageFetcher()
    val paymentsPage = paymentsPageFetcher.paymentsPage

    private val _paymentsFlow = MutableStateFlow<Map<UUID, WalletPaymentInfo>>(HashMap())
    val paymentsFlow: StateFlow<Map<UUID, WalletPaymentInfo>> = _paymentsFlow.asStateFlow()

    private val homePageFetcher: PaymentsPageFetcher = paymentsManager.makePageFetcher()
    val homePaymentsFlow = homePageFetcher.paymentsPage.mapLatest { it.rows }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    init {
        paymentsPageFetcher.subscribeToAll(offset = 0, count = pageSize)
        homePageFetcher.subscribeToAll(offset = 0, count = paymentsCountInHome)

        // collect changes on the payments page that we subscribed to
        viewModelScope.launch(CoroutineExceptionHandler { _, e ->
            log.error("error when collecting payments-page items: ", e)
        }) {
            monitorPaymentsPage()
        }
    }

    private suspend fun monitorPaymentsPage() {
        paymentsPageFetcher.paymentsPage.collect { page ->
            val existingPayments = paymentsFlow.value
            when {
                existingPayments.isEmpty() -> {
                    _paymentsFlow.value = page.rows.associateBy { it.payment.id }
                }
                page.offset == 0 -> {
                    // we are refreshing the first page with what may be new payments; these payments must be put on top of the map.
                    var newPayments = existingPayments.toMutableMap()
                    page.rows.forEach { row ->
                        when {
                            newPayments.containsKey(row.id) -> newPayments[row.id] = row
                            row.payment.run { completedAt ?: createdAt } > newPayments.values.first().payment.run { completedAt ?: createdAt } -> newPayments = (mapOf(row.payment.id to row) + newPayments).toMutableMap()
                            else -> newPayments = (newPayments + (row.payment.id to row)).toMutableMap()
                        }
                    }
                    _paymentsFlow.value = newPayments
                }
                else -> {
                    _paymentsFlow.value += page.rows.associateBy { it.payment.id }
                }
            }
        }
    }

    /** Updates the payment fetcher to listen to changes within the given count and offset, indirectly updating the [paymentsFlow]. */
    fun subscribeToPayments(offset: Int, count: Int) {
        paymentsPageFetcher.subscribeToAll(offset, count)
    }

    class Factory(
        private val paymentsManager: PaymentsManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PaymentsViewModel(paymentsManager) as T
        }
    }
}
