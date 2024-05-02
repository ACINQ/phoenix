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
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Transaction
import fr.acinq.bitcoin.scala.Satoshi
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.spendExpiredSwapIn
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
    data object Sending : SwapInRefundState()
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

    fun executeRefund(address: String, feerate: FeeratePerByte) {
        if (state is SwapInRefundState.Sending) return
        state = SwapInRefundState.Sending

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when executing swap-in refund: ", e)
        }) {
            when (val parseAddress = Parser.readBitcoinAddress(NodeParamsManager.chain, address)) {
                is Either.Left -> {
                    log.debug("invalid refund address=$address (${parseAddress.value}")
                    state = SwapInRefundState.Done.Failed.InvalidAddress(address, parseAddress.value)
                    return@launch
                }
                is Either.Right -> {
                    val peer = peerManager.getPeer()
                    peer.swapInWallet.wallet.walletStateFlow.filterNotNull().first()
                    val keyManager = walletManager.keyManager.filterNotNull().first()
                    val swapInWallet = peerManager.swapInWallet.filterNotNull().first()
                    val tx = electrumClient.spendExpiredSwapIn(
                        swapInKeys = keyManager.swapInOnChainWallet,
                        wallet = swapInWallet,
                        scriptPubKey = parseAddress.value.script,
                        feerate = FeeratePerKw(feerate)
                    )
                    state = if (tx == null) {
                        log.error("spendExpiredSwapIn returned a null tx")
                        SwapInRefundState.Done.Failed.CannotCreateTx
                    } else {
                        log.info("successfully spent swap-in refund tx=$tx")
                        SwapInRefundState.Done.Success(tx)
                    }
                }
            }
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