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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.BitcoinError
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.Transaction
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.phoenix.data.BitcoinUriError
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class SwapInRefundState {
    data object Init : SwapInRefundState()
    data object GettingFee : SwapInRefundState()
    data class ReviewFee(val fees: Satoshi, val transaction: Transaction): SwapInRefundState()
    data class Publishing(val transaction: Transaction) : SwapInRefundState()
    sealed class Done : SwapInRefundState() {
        data class Success(val tx: Transaction) : Done()
        sealed class Failed : Done() {
            data class Error(val e: Throwable) : Failed()
            data object CannotCreateTx : Failed()
            data class InvalidAddress(val address: String, val error: BitcoinUriError) : Failed()
        }
    }
}

class SwapInRefundViewModel(
    private val peerManager: PeerManager,
    private val walletManager: WalletManager,
    private val electrumClient: ElectrumClient,
) : ViewModel() {

    val log = LoggerFactory.getLogger(this::class.java)

    var state by mutableStateOf<SwapInRefundState>(SwapInRefundState.Init)

    fun getFeeForRefund(address: String, feerate: FeeratePerByte) {
        if (state is SwapInRefundState.GettingFee) return
        state = SwapInRefundState.GettingFee

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when estimating swap-in refund fees: ", e)
            state = SwapInRefundState.Done.Failed.Error(e)
        }) {
            when (val parseAddress = Parser.readBitcoinAddress(NodeParamsManager.chain, address)) {
                is Either.Left -> {
                    log.debug("invalid refund address=$address (${parseAddress.value}")
                    state = SwapInRefundState.Done.Failed.InvalidAddress(address, parseAddress.value)
                    return@launch
                }
                is Either.Right -> {
                    val keyManager = walletManager.keyManager.filterNotNull().first()
                    val swapInWallet = peerManager.swapInWallet.filterNotNull().first()
                    val script = parseAddress.value.script
                    if (script == null) {
                        state = SwapInRefundState.Done.Failed.InvalidAddress(
                            address = parseAddress.value.address,
                            error = BitcoinUriError.InvalidScript(BitcoinError.InvalidScript)
                        )
                    } else {
                        val res = swapInWallet.spendExpiredSwapIn(
                            swapInKeys = keyManager.swapInOnChainWallet,
                            scriptPubKey = script,
                            feerate = FeeratePerKw(feerate)
                        )
                        state = if (res == null) {
                            log.error("could not generate a swap-in refund transaction")
                            SwapInRefundState.Done.Failed.CannotCreateTx
                        } else {
                            val (tx, fee) = res
                            log.info("estimated fee=$fee for swap-in refund")
                            SwapInRefundState.ReviewFee(fees = fee, transaction = tx)
                        }
                    }
                }
            }
        }
    }

    fun executeRefund(tx: Transaction) {
        if (state !is SwapInRefundState.ReviewFee) return
        state = SwapInRefundState.Publishing(tx)

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when broadcasting swap-in refund tx=$tx: ", e)
            state = SwapInRefundState.Done.Failed.Error(e)
        }) {
            electrumClient.broadcastTransaction(tx)
            log.info("successfully broadcast tx=$tx for swap-in refund")
            state = SwapInRefundState.Done.Success(tx)
        }
    }

    class Factory(
        private val peerManager: PeerManager,
        private val walletManager: WalletManager,
        private val electrumClient: ElectrumClient,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SwapInRefundViewModel(peerManager, walletManager, electrumClient) as T
        }
    }
}