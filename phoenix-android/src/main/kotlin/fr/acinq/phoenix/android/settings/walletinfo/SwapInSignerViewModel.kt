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
import fr.acinq.bitcoin.Transaction
import fr.acinq.bitcoin.crypto.musig2.IndividualNonce
import fr.acinq.bitcoin.crypto.musig2.Musig2
import fr.acinq.bitcoin.utils.toResult
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.phoenix.managers.WalletManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


enum class SwapInOptions { LEGACY, TAPROOT }
sealed class SwapInSignerState

sealed class LegacySwapInSignerState : SwapInSignerState() {
    data object Init : LegacySwapInSignerState()
    data object Signing : LegacySwapInSignerState()
    data class Signed(
        val txId: String,
        val userKey: String,
        val userSig: String,
    ) : LegacySwapInSignerState()
    sealed class Failed : LegacySwapInSignerState() {
        data class InvalidTxInput(val cause: Throwable) : Failed()
        data class Error(val cause: Throwable) : Failed()
    }
}

sealed class TaprootSwapInSignerState : SwapInSignerState() {
    data object Init : TaprootSwapInSignerState()
    data object Signing : TaprootSwapInSignerState()
    data class Signed(
        val txId: String,
        val userKey: String,
        val userRefundKey: String,
        val userNonce: String,
        val userSig: String,
    ) : TaprootSwapInSignerState()

    sealed class Failed : TaprootSwapInSignerState() {
        data object AddressIndexNotFound : Failed()
        data class InvalidTxInput(val cause: Throwable) : Failed()
        data class NonceGenerationFailure(val cause: Throwable) : Failed()
        data class Error(val cause: Throwable) : Failed()
    }
}

class SwapInSignerViewModel(
    private val walletManager: WalletManager,
    private val electrumClient: ElectrumClient,
) : ViewModel() {

    private val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<SwapInSignerState>(LegacySwapInSignerState.Init)

    fun signLegacy(
        unsignedTx: String,
    ) {
        if (state.value == LegacySwapInSignerState.Signing) return
        state.value = LegacySwapInSignerState.Signing

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("(legacy) failed to sign tx=$unsignedTx: ", e)
            state.value = LegacySwapInSignerState.Failed.Error(e)
        }) {
            log.debug("signing tx=$unsignedTx")
            val tx = try {
                Transaction.read(unsignedTx)
            } catch (e: Exception) {
                log.error("(legacy) invalid transaction input: ", e)
                state.value = LegacySwapInSignerState.Failed.InvalidTxInput(e)
                return@launch
            }
            val input = if (tx.txIn.size == 1) tx.txIn.first() else throw RuntimeException("tx has ${tx.txIn.size} inputs")
            val parentTxId = input.outPoint.txid
            log.debug("retrieving parent_tx {}", parentTxId)
            val parentTx = electrumClient.getTx(input.outPoint.txid) ?: throw RuntimeException("parent tx=$parentTxId not found by electrum")
            val keyManager = walletManager.keyManager.filterNotNull().first()
            val userSig = keyManager.swapInOnChainWallet.signSwapInputUserLegacy(
                fundingTx = tx,
                index = 0,
                parentTxOuts = parentTx.txOut,
            )
            state.value = LegacySwapInSignerState.Signed(
                txId = tx.txid.toString(),
                userKey = keyManager.swapInOnChainWallet.userPublicKey.toHex(),
                userSig = userSig.toString(),
            )
        }
    }

    fun signTaproot(
        unsignedTx: String,
        serverNonce: String,
    ) {
        if (state.value == TaprootSwapInSignerState.Signing) return
        state.value = TaprootSwapInSignerState.Signing

        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("(taproot) failed to sign tx=$unsignedTx: ", e)
            state.value = TaprootSwapInSignerState.Failed.Error(e)
        }) {
            val keyManager = walletManager.keyManager.filterNotNull().first()
            val userPrivateKey = keyManager.swapInOnChainWallet.userPrivateKey

            // read tx input
            val tx = try {
                Transaction.read(unsignedTx)
            } catch (e: Exception) {
                log.error("(taproot) invalid transaction input: ", e)
                state.value = TaprootSwapInSignerState.Failed.InvalidTxInput(e)
                return@launch
            }

            // parent tx
            val input = if (tx.txIn.size == 1) tx.txIn.first() else throw RuntimeException("tx has ${tx.txIn.size} inputs")
            val parentTxId = input.outPoint.txid
            log.debug("retrieving parent_tx {}", parentTxId)
            val parentTx = electrumClient.getTx(input.outPoint.txid) ?: throw RuntimeException("parent tx=$parentTxId not found by electrum")

            // select correct output
            val txOut = parentTx.txOut[tx.txIn.first().outPoint.index.toInt()]

            // swap-in protocol
            val addressIndex = (0..1_000).indexOfFirst { i ->
               keyManager.swapInOnChainWallet.getSwapInProtocol(i).serializedPubkeyScript == txOut.publicKeyScript
            }
            if (addressIndex == -1) {
                log.error("(taproot) cannot find address index")
                state.value = TaprootSwapInSignerState.Failed.AddressIndexNotFound
                return@launch
            }
            val swapInProtocol = keyManager.swapInOnChainWallet.getSwapInProtocol(addressIndex)

            // generate nonce
            val (userPrivateNonce, userNonce) = try {
                Musig2.generateNonce(randomBytes32(), userPrivateKey, listOf(swapInProtocol.userPublicKey, swapInProtocol.serverPublicKey))
            } catch (e: Exception) {
                log.error("(taproot) unable to generate nonce: ", e)
                state.value = TaprootSwapInSignerState.Failed.NonceGenerationFailure(e)
                return@launch
            }

            val userSig = swapInProtocol.signSwapInputUser(
                fundingTx = tx,
                index = 0,
                parentTxOuts = listOf(txOut),
                userPrivateKey = userPrivateKey,
                privateNonce = userPrivateNonce,
                userNonce = userNonce,
                serverNonce = IndividualNonce(serverNonce)
            ).toResult().getOrThrow()

            state.value = TaprootSwapInSignerState.Signed(
                txId = tx.txid.toString(),
                userKey = keyManager.swapInOnChainWallet.userPublicKey.toHex(),
                userSig = userSig.toString(),
                userRefundKey = swapInProtocol.userRefundKey.toHex(),
                userNonce = userNonce.toString(),
            )
        }
    }

    class Factory(
        private val walletManager: WalletManager,
        private val electrumClient: ElectrumClient,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SwapInSignerViewModel(walletManager, electrumClient) as T
        }
    }
}