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
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.data.lnurl.LnurlWithdraw
import fr.acinq.phoenix.managers.SendManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class LnurlWithdrawViewState {
    data object Init : LnurlWithdrawViewState()
    data object SendingInvoice: LnurlWithdrawViewState()
    data object InvoiceSent : LnurlWithdrawViewState()
    sealed class Error : LnurlWithdrawViewState() {
        data class Generic(val cause: Throwable): Error()
        data class WithdrawError(val error : SendManager.LnurlWithdrawError) : Error()
    }
}

class LnurlWithdrawViewModel(private val sendManager: SendManager) : ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<LnurlWithdrawViewState>(LnurlWithdrawViewState.Init)

    fun sendInvoice(withdraw: LnurlWithdraw, amount: MilliSatoshi) {
        if (state.value is LnurlWithdrawViewState.SendingInvoice) return
        state.value = LnurlWithdrawViewState.SendingInvoice

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error in lnurl-withdraw process: ", e)
            state.value = LnurlWithdrawViewState.Error.Generic(e)
        }) {
            val invoice = sendManager.lnurlWithdraw_createInvoice(withdraw, amount = amount, description = withdraw.defaultDescription)
            val result = sendManager.lnurlWithdraw_sendInvoice(withdraw, invoice)
            if (result == null) {
                state.value = LnurlWithdrawViewState.InvoiceSent
            } else {
                log.error("lnurl-withdraw rejected by service: $result")
                state.value = LnurlWithdrawViewState.Error.WithdrawError(result)
            }
        }
    }

    class Factory(
        private val sendManager: SendManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LnurlWithdrawViewModel(sendManager) as T
        }
    }
}
