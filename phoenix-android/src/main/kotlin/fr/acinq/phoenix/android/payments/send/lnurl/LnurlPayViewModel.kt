/*
 * Copyright 2024 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.send.lnurl

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.TrampolineFees
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.getLogger
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.managers.SendManager
import fr.acinq.phoenix.utils.logger.LogHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


sealed class LnurlPayViewState {
    data object Init : LnurlPayViewState()
    data object RequestingInvoice: LnurlPayViewState()
    data class PayingInvoice(val invoice: LnurlPay.Invoice) : LnurlPayViewState()
    sealed class Error : LnurlPayViewState() {
        data class Generic(val cause: Throwable): Error()
        data class PayError(val error : SendManager.LnurlPayError) : Error()
    }
}

class LnurlPayViewModel(
    val application: PhoenixApplication,
    val walletId: WalletId,
    private val sendManager: SendManager
) : ViewModel() {

    private val log = LogHelper.getLogger(application.applicationContext, walletId, this)
    val state = mutableStateOf<LnurlPayViewState>(LnurlPayViewState.Init)

    fun requestAndPayInvoice(pay: SendManager.ParseResult.Lnurl.Pay, amount: MilliSatoshi, fees: TrampolineFees, comment: String?, onPaymentSent: () -> Unit) {
        if (state.value is LnurlPayViewState.RequestingInvoice || state.value is LnurlPayViewState.PayingInvoice) return
        state.value = LnurlPayViewState.RequestingInvoice

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to pay lnurl-pay intent: ", e)
            state.value = LnurlPayViewState.Error.Generic(e)
        }) {
            when (val result = sendManager.lnurlPay_requestInvoice(pay, amount, comment)) {
                is Either.Left -> state.value = LnurlPayViewState.Error.PayError(result.value)
                is Either.Right -> {
                    val invoice = result.value
                    state.value = LnurlPayViewState.PayingInvoice(invoice)
                    sendManager.lnurlPay_payInvoice(pay, amount, comment, invoice, fees)
                    viewModelScope.launch(Dispatchers.Main) {
                        onPaymentSent()
                    }
                }
            }
        }
    }

    class Factory(
        val application: PhoenixApplication,
        val walletId: WalletId,
        private val sendManager: SendManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LnurlPayViewModel(application, walletId, sendManager) as T
        }
    }
}