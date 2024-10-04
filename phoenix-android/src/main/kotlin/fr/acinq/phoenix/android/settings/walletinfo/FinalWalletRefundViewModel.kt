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
import fr.acinq.bitcoin.BitcoinError
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.Transaction
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.phoenix.data.BitcoinUriError
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class FinalWalletRefundState {
    data object Init : FinalWalletRefundState()
    data object GettingFee : FinalWalletRefundState()
    data class ReviewFee(val fees: Satoshi, val transaction: Transaction) : FinalWalletRefundState()
    data class Publishing(val transaction: Transaction) : FinalWalletRefundState()
    data class Success(val tx: Transaction) : FinalWalletRefundState()
    sealed class Failed : FinalWalletRefundState() {
        data class Error(val e: Throwable) : Failed()
        data object CannotCreateTx : Failed()
        data class InvalidAddress(val address: String, val error: BitcoinUriError) : Failed()
    }
}

class FinalWalletRefundViewModel(val peerManager: PeerManager, val electrumClient: ElectrumClient) : ViewModel() {
    private val log = LoggerFactory.getLogger(this::class.java)

    val state = mutableStateOf<FinalWalletRefundState>(FinalWalletRefundState.Init)

    fun estimateRefundFee(address: String, feerate: FeeratePerByte) {
        if (state.value is FinalWalletRefundState.GettingFee) return
        state.value = FinalWalletRefundState.GettingFee

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when estimating fees for final wallet refund: ", e)
            state.value = FinalWalletRefundState.Failed.Error(e)
        }) {
            when (val parseResult = Parser.parseBip21Uri(NodeParamsManager.chain, address)) {
                is Either.Left -> {
                    log.debug("invalid final-wallet refund address={} error={}", address, parseResult.value)
                    state.value = FinalWalletRefundState.Failed.InvalidAddress(address, parseResult.value)
                    return@launch
                }
                is Either.Right -> {
                    if (parseResult.value.script == null) {
                        state.value = FinalWalletRefundState.Failed.InvalidAddress(
                            address = parseResult.value.address,
                            error = BitcoinUriError.InvalidScript(BitcoinError.InvalidScript)
                        )
                    } else {
                        val finalWallet = peerManager.getPeer().finalWallet!!
                        log.debug("sending to address=${parseResult.value.address} feerate=$feerate")
                        val res = finalWallet.buildSendAllTransaction(bitcoinAddress = parseResult.value.address, feerate = FeeratePerKw(feerate))
                        delay(300)
                        state.value = if (res == null) {
                            log.error("failed to generate a refund transaction for the final wallet")
                            FinalWalletRefundState.Failed.CannotCreateTx
                        } else {
                            val (tx, fee) = res
                            log.debug("estimated fee=$fee for final-wallet refund")
                            FinalWalletRefundState.ReviewFee(fees = fee, transaction = finalWallet.signTransaction(tx)!!)
                        }
                    }
                }
            }
        }
    }

    fun executeRefund(tx: Transaction) {
        if (state.value !is FinalWalletRefundState.ReviewFee) return
        state.value = FinalWalletRefundState.Publishing(tx)

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when broadcasting final-wallet refund tx=$tx: ", e)
            state.value = FinalWalletRefundState.Failed.Error(e)
        }) {
            electrumClient.broadcastTransaction(tx)
            log.info("final-wallet refund tx=$tx has been broadcast")
            state.value = FinalWalletRefundState.Success(tx)
        }
    }

    class Factory(
        private val peerManager: PeerManager,
        private val electrumClient: ElectrumClient,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return FinalWalletRefundViewModel(peerManager, electrumClient) as T
        }
    }
}
