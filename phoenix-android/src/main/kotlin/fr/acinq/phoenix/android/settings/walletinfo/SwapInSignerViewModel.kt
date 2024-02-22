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

package fr.acinq.phoenix.android.settings.walletinfo

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.Transaction
import fr.acinq.bitcoin.TxOut
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.crypto.KeyManager
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.transactions.Transactions
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.managers.WalletManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

sealed class SwapInSignerState {
    object Init : SwapInSignerState()
    object Signing : SwapInSignerState()
    data class Signed(
        val amount: Satoshi,
        val txId: String,
        val userSig: String,
    ) : SwapInSignerState()
    sealed class Failed : SwapInSignerState() {
        data class InvalidTxInput(val cause: Throwable) : Failed()
        data class Error(val cause: Throwable) : Failed()
    }
}

class SwapInSignerViewModel(
    val walletManager: WalletManager,
) : ViewModel() {

    val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<SwapInSignerState>(SwapInSignerState.Init)

    fun sign(
        unsignedTx: String,
        amount: Satoshi,
    ) {
        if (state.value == SwapInSignerState.Signing) return
        state.value = SwapInSignerState.Signing

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to sign tx=$unsignedTx for amount=$amount : ", e)
            state.value = SwapInSignerState.Failed.Error(e)
        }) {
            log.debug("signing tx=$unsignedTx amount=$amount")
            val tx = try {
                Transaction.read(unsignedTx)
            } catch (e: Exception) {
                log.error("invalid transaction input: ", e)
                state.value = SwapInSignerState.Failed.InvalidTxInput(e)
                return@launch
            }
            val keyManager = walletManager.keyManager.filterNotNull().first()
            val userSig = keyManager.swapInOnChainWallet.signSwapInputUserLegacy(
                fundingTx = tx,
                index = 0,
                parentTxOuts = listOf(TxOut(amount, ByteVector.empty)),
            )
            state.value = SwapInSignerState.Signed(
                amount = amount,
                txId = tx.txid.toString(),
                userSig = userSig.toString(),
            )
        }
    }

    class Factory(
        private val walletManager: WalletManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SwapInSignerViewModel(walletManager) as T
        }
    }
}