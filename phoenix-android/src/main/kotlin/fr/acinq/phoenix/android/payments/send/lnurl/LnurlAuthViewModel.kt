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
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.managers.SendManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class LnurlAuthViewState {
    data object Init : LnurlAuthViewState()
    data object LoggingIn : LnurlAuthViewState()
    data object AuthSuccess : LnurlAuthViewState()
    data class AuthFailure(val error: SendManager.LnurlAuthError) : LnurlAuthViewState()
    data class Error(val cause: Throwable) : LnurlAuthViewState()
}

class LnurlAuthViewModel(private val sendManager: SendManager) : ViewModel() {

    val log = LoggerFactory.getLogger(this::class.java)
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
        private val sendManager: SendManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LnurlAuthViewModel(sendManager) as T
        }
    }
}