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

package fr.acinq.phoenix.android.settings.channels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector64
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.TxId
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.channels.SpendChannelAddressHelper
import fr.acinq.phoenix.utils.channels.SpendChannelAddressResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

sealed class SpendFromChannelAddressViewState {
    data object Init : SpendFromChannelAddressViewState()
    data object Processing : SpendFromChannelAddressViewState()
    data class SignedTransaction(val pubkey: PublicKey, val signature: ByteVector64) : SpendFromChannelAddressViewState()

    sealed class Error : SpendFromChannelAddressViewState() {
        data class Generic(val cause: Throwable) : Error()
        data object AmountMissing : Error()
        data object TxIndexMalformed : Error()
        data object InvalidChannelKeyPath: Error()
        data class PublicKeyMalformed(val details: String) : Error()
        data class TransactionMalformed(val details: String) : Error()
        data class InvalidSig(val txId: TxId, val publicKey: PublicKey, val fundingScript: ByteVector, val signature: ByteVector64): Error()
    }

    val canProcess: Boolean = this !is Processing && this !is SignedTransaction
}

class SpendFromChannelAddressViewModel(
    private val business: PhoenixBusiness
) : ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<SpendFromChannelAddressViewState>(SpendFromChannelAddressViewState.Init)

    fun resetState() {
        state.value = SpendFromChannelAddressViewState.Init
    }

    fun spendFromChannelAddress(
        amount: Satoshi?,
        fundingTxIndex: Long?,
        channelData: String,
        remoteFundingPubkey: String,
        unsignedTx: String,
    ) {
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when spending from channel address: ", e)
            state.value = SpendFromChannelAddressViewState.Error.Generic(e)
        }) {

            if (amount == null) {
                state.value = SpendFromChannelAddressViewState.Error.AmountMissing
                return@launch
            }

            if (fundingTxIndex == null) {
                state.value = SpendFromChannelAddressViewState.Error.TxIndexMalformed
                return@launch
            }

            val result = SpendChannelAddressHelper.spendFromChannelAddress(
                business = business,
                amount = amount,
                fundingTxIndex = fundingTxIndex,
                channelKeyPath = "FIXME",
                remoteFundingPubkey = remoteFundingPubkey,
                unsignedTx = unsignedTx,
            )

            when (result) {
                is SpendChannelAddressResult.Success -> {
                    delay(300.milliseconds)
                    state.value = SpendFromChannelAddressViewState.SignedTransaction(result.publicKey, result.signature)
                }
                is SpendChannelAddressResult.Failure.InvalidChannelKeyPath -> {
                    state.value = SpendFromChannelAddressViewState.Error.InvalidChannelKeyPath
                }
                is SpendChannelAddressResult.Failure.Generic -> {
                    state.value = SpendFromChannelAddressViewState.Error.Generic(result.error)
                }
                is SpendChannelAddressResult.Failure.RemoteFundingPubkeyMalformed -> {
                    state.value = SpendFromChannelAddressViewState.Error.PublicKeyMalformed(result.details)
                }
                is SpendChannelAddressResult.Failure.TransactionMalformed -> {
                    state.value = SpendFromChannelAddressViewState.Error.TransactionMalformed(result.details)
                }
                is SpendChannelAddressResult.Failure.InvalidSig -> {
                    state.value = SpendFromChannelAddressViewState.Error.InvalidSig(result.txId, result.publicKey, result.fundingScript, result.signature)
                }
            }
        }
    }

    class Factory(
        private val business: PhoenixBusiness
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SpendFromChannelAddressViewModel(business) as T
        }
    }
}