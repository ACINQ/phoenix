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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.android.components.mvi.MVIControllerViewModel
import fr.acinq.phoenix.android.utils.datastore.UserPrefs
import fr.acinq.phoenix.controllers.ControllerFactory
import fr.acinq.phoenix.controllers.HomeController
import fr.acinq.phoenix.controllers.main.Home
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.walletPaymentId
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.managers.Connections
import fr.acinq.phoenix.managers.PaymentsManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class PaymentRowState(
    val orderRow: WalletPaymentOrderRow,
    val payment: WalletPayment?
)

@ExperimentalCoroutinesApi
class HomeViewModel(
    val connectionsFlow: StateFlow<Connections>,
    val paymentsManager: PaymentsManager,
    controller: HomeController
) : MVIControllerViewModel<Home.Model, Home.Intent>(controller) {

    private val _paymentsFlow = MutableStateFlow<Map<String, PaymentRowState>>(HashMap())
    val paymentsFlow: StateFlow<Map<String, PaymentRowState>> = _paymentsFlow

    init {
        paymentsManager.subscribeToPaymentsPage(0, 150)

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
            log.error("failed to collect payments page items: ", e)
        }) {
            paymentsManager.paymentsPage.collect {
                viewModelScope.launch(Dispatchers.Default) {
                    // rewrite all the payments flow map to keep payments ordering - adding the diff would put new elements to the bottom of the map
                    _paymentsFlow.value = it.rows.map { row ->
                        row.id.identifier to (_paymentsFlow.value[row.id.identifier] ?: run {
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
                    _paymentsFlow.value += (row.id.identifier to PaymentRowState(row, paymentInfo.payment))
                }
            }
        }
    }

    class Factory(
        private val connectionsFlow: StateFlow<Connections>,
        private val paymentsManager: PaymentsManager,
        private val controllerFactory: ControllerFactory,
        private val getController: ControllerFactory.() -> HomeController
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(connectionsFlow, paymentsManager, controllerFactory.getController()) as T
        }
    }
}
