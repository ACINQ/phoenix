/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.android.payments

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.business
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.managers.PaymentsManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class PaymentDetailsState {
    object Loading : PaymentDetailsState()
    sealed class Success : PaymentDetailsState() {
        abstract val payment: WalletPaymentInfo

        data class Splash(override val payment: WalletPaymentInfo) : Success()
        data class TechnicalDetails(override val payment: WalletPaymentInfo) : Success()
    }

    object NotFound: PaymentDetailsState()
}

class PaymentDetailsViewModel(
    private val paymentsManager: PaymentsManager,
    private val paymentId: WalletPaymentId?
) : ViewModel() {

    private val log = LoggerFactory.getLogger(this::class.java)

    var state by mutableStateOf<PaymentDetailsState>(PaymentDetailsState.Loading)

    init {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to retrieve payment details for id=$paymentId: ", e)
            state = PaymentDetailsState.NotFound
        }) {
            getPayment(paymentId)
        }
    }

    /** Retrieve all available details of the payment. Should run on [Dispatchers.IO]. */
    private suspend fun getPayment(id: WalletPaymentId?) {
        log.info("getting payment details for id=$id")
        val result = id?.let {
            paymentsManager.getPayment(id, WalletPaymentFetchOptions.All)
        }?.let {
            PaymentDetailsState.Success.Splash(it)
        } ?: PaymentDetailsState.NotFound
        viewModelScope.launch(Dispatchers.Main) { state = result }
    }

    fun updateMetadata(id: WalletPaymentId, userDescription: String?) {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            log.error("failed to save user description to database: ", e)
        }) {
            // update meta then refresh
            paymentsManager.updateMetadata(id, userDescription)
            getPayment(id)
        }
    }

    class Factory(
        private val paymentsManager: PaymentsManager,
        private val paymentId: WalletPaymentId?,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PaymentDetailsViewModel(paymentsManager, paymentId) as T
        }
    }
}

@Composable
fun PaymentDetailsView(
    paymentId: WalletPaymentId?,
    onBackClick: () -> Unit,
    fromEvent: Boolean,
) {
    val vm: PaymentDetailsViewModel = viewModel(factory = PaymentDetailsViewModel.Factory(business.paymentsManager, paymentId))

    val onBack = {
        when (val state = vm.state) {
            is PaymentDetailsState.Success.TechnicalDetails -> vm.state = PaymentDetailsState.Success.Splash(state.payment)
            else -> onBackClick()
        }
    }
    DefaultScreenLayout {
        DefaultScreenHeader(
            onBackClick = onBack,
            backgroundColor = Color.Unspecified,
            title = if (vm.state is PaymentDetailsState.Success.TechnicalDetails) stringResource(id = R.string.paymentdetails_title) else null
        )
        when (val state = vm.state) {
            is PaymentDetailsState.Loading -> CenterContentView {
                Text(
                    text = stringResource(id = R.string.paymentdetails_loading),
                    modifier = Modifier.padding(16.dp)
                )
            }
            is PaymentDetailsState.NotFound -> CenterContentView {
                Text(
                    text = stringResource(id = R.string.paymentdetails_error_not_found),
                    modifier = Modifier.padding(16.dp),
                )
            }
            is PaymentDetailsState.Success.Splash -> {
                PaymentDetailsSplashView(
                    data = state.payment,
                    onDetailsClick = { vm.state = PaymentDetailsState.Success.TechnicalDetails(state.payment) },
                    onMetadataDescriptionUpdate = { id, description -> vm.updateMetadata(id, description) },
                    fromEvent = fromEvent,
                )
            }
            is PaymentDetailsState.Success.TechnicalDetails -> {
                PaymentDetailsTechnicalView(data = state.payment)
            }
        }
    }
}

@Composable
private fun CenterContentView(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier) {
            content()
        }
    }
}
