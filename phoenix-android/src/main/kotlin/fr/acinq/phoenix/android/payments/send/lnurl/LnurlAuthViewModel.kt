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
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.getLogger
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.managers.SendManager
import fr.acinq.phoenix.utils.logger.LogHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class LnurlAuthViewState {
    data object Init : LnurlAuthViewState()
    data object LoggingIn : LnurlAuthViewState()
    data object AuthSuccess : LnurlAuthViewState()
    data class AuthFailure(val error: SendManager.LnurlAuthError) : LnurlAuthViewState()
    data class Error(val cause: Throwable) : LnurlAuthViewState()
}

class LnurlAuthViewModel(
    val application: PhoenixApplication,
    val walletId: WalletId,
    private val sendManager: SendManager
) : ViewModel() {
    private val log = LogHelper.getLogger(application.applicationContext, walletId, this)
    val state = mutableStateOf<LnurlAuthViewState>(LnurlAuthViewState.Init)

    fun authenticateToDomain(auth: LnurlAuth, scheme: LnurlAuth.Scheme) {
        if (state.value is LnurlAuthViewState.LoggingIn) return
        state.value = LnurlAuthViewState.LoggingIn

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to authenticate on lnurl-auth=$auth: ", e)
            state.value = LnurlAuthViewState.Error(e)
        }) {
            val result = sendManager.lnurlAuth_signAndSend(auth, minSuccessDelaySeconds = 1.0, scheme = scheme)
            when (result) {
                is SendManager.LnurlAuthError -> state.value = LnurlAuthViewState.AuthFailure(result)
                null -> state.value = LnurlAuthViewState.AuthSuccess
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
            return LnurlAuthViewModel(application, walletId, sendManager) as T
        }
    }
}