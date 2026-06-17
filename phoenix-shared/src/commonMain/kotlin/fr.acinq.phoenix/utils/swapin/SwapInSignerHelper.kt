/*
 * Copyright 2026 ACINQ SAS
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

package fr.acinq.phoenix.utils.swapin

import fr.acinq.bitcoin.Transaction
import fr.acinq.bitcoin.crypto.musig2.IndividualNonce
import fr.acinq.bitcoin.crypto.musig2.Musig2
import fr.acinq.bitcoin.utils.Either
import fr.acinq.bitcoin.utils.toResult
import fr.acinq.lightning.Lightning.randomBytes32
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.logging.info
import fr.acinq.phoenix.PhoenixBusiness
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

object SwapInSignerHelper {

    suspend fun signPartialTxLegacy(business: PhoenixBusiness, unsignedTx: String): LegacySwapInSignerResult {
        val loggerFactory = business.loggerFactory
        val log = loggerFactory.newLogger(this::class)

        val electrumClient = business.electrumClient
        val walletManager = business.walletManager

        val tx = try {
            Transaction.read(unsignedTx)
        } catch (e: Exception) {
            log.error(e) { "(legacy) invalid transaction input: " }
            return LegacySwapInSignerResult.Failure.TransactionMalformed(e.message ?: e::class.toString())
        }

        val input = if (tx.txIn.size == 1) tx.txIn.first() else return LegacySwapInSignerResult.Failure.TooManyInputs(tx.txIn.size)
        val parentTxId = input.outPoint.txid

        log.info { "(legacy) retrieving tx details for $parentTxId" }
        val parentTx = electrumClient.getTx(parentTxId) ?: return LegacySwapInSignerResult.Failure.ParentTxNotFound
        val keyManager = walletManager.keyManager.filterNotNull().first()

        val userSig = try {
            keyManager.swapInOnChainWallet.signSwapInputUserLegacy(
                fundingTx = tx,
                index = 0,
                parentTxOuts = parentTx.txOut,
            )
        } catch (e: Exception) {
            log.error(e) { "(taproot) signature error" }
            return LegacySwapInSignerResult.Failure.SigningError
        }

        log.info { "(legacy) successfully signed partial tx input" }
        return LegacySwapInSignerResult.Success(
            txId = tx.txid.toString(),
            userPubkey = keyManager.swapInOnChainWallet.userPublicKey.toHex(),
            userSignature = userSig.toString()
        )
    }

    suspend fun signPartialTxTaproot(business: PhoenixBusiness, unsignedTx: String, serverNonce: String): TaprootSwapInSignerResult {
        val loggerFactory = business.loggerFactory
        val log = loggerFactory.newLogger(this::class)

        val electrumClient = business.electrumClient
        val walletManager = business.walletManager
        val keyManager = walletManager.keyManager.filterNotNull().first()
        val userPrivateKey = keyManager.swapInOnChainWallet.userPrivateKey

        // read tx input
        val tx = try {
            Transaction.read(unsignedTx)
        } catch (e: Exception) {
            log.error(e) { "(taproot) invalid transaction input: " }
            return TaprootSwapInSignerResult.Failure.TransactionMalformed(e.message ?: e::class.toString())
        }

        // parent tx
        val (txOut, swapInProtocol) = try {
            val input = if (tx.txIn.size == 1) tx.txIn.first() else return TaprootSwapInSignerResult.Failure.TooManyInputs(tx.txIn.size)
            val parentTxId = input.outPoint.txid
            log.info { "(taproot) retrieving tx details for $parentTxId" }
            val parentTx = electrumClient.getTx(parentTxId) ?: return TaprootSwapInSignerResult.Failure.ParentTxNotFound

            // select correct output
            val txOut = parentTx.txOut[tx.txIn.first().outPoint.index.toInt()]

            // swap-in protocol
            val addressIndex = (0..1_000).indexOfFirst { i ->
                keyManager.swapInOnChainWallet.getSwapInProtocol(i).serializedPubkeyScript == txOut.publicKeyScript
            }
            if (addressIndex == -1) {
                log.error { "(taproot) cannot find address index" }
                return TaprootSwapInSignerResult.Failure.AddressIndexNotFound
            }
            txOut to keyManager.swapInOnChainWallet.getSwapInProtocol(addressIndex)
        } catch (e: Exception) {
            log.error(e) { "(taproot) failed to retrieve swap-in-protocol or tx-out details: " }
            return TaprootSwapInSignerResult.Failure.SwapProtocolError
        }

        // generate nonce
        val (userPrivateNonce, userNonce) = try {
            Musig2.generateNonce(
                sessionId = randomBytes32(),
                signingKey = Either.Left(userPrivateKey),
                publicKeys = listOf(swapInProtocol.userPublicKey, swapInProtocol.serverPublicKey),
                message = null,
                extraInput = null
            )
        } catch (e: Exception) {
            log.error(e) { "(taproot) unable to generate nonce: " }
            return TaprootSwapInSignerResult.Failure.NonceGenerationFailure
        }

        val userSig = try {
            swapInProtocol.signSwapInputUser(
                fundingTx = tx,
                index = 0,
                parentTxOuts = listOf(txOut),
                userPrivateKey = userPrivateKey,
                privateNonce = userPrivateNonce,
                userNonce = userNonce,
                serverNonce = IndividualNonce(serverNonce)
            ).toResult().getOrThrow()
        } catch (e: Exception) {
            log.error(e) { "(taproot) signature error" }
            return TaprootSwapInSignerResult.Failure.SigningError
        }

        log.info { "(taproot) successfully signed partial tx input" }
        return TaprootSwapInSignerResult.Success(
            txId = tx.txid.toString(),
            userPubkey = keyManager.swapInOnChainWallet.userPublicKey.toHex(),
            userSignature = userSig.toString(),
            userRefundKey = swapInProtocol.userRefundKey.toHex(),
            userNonce = userNonce.toString(),
        )
    }
}

sealed class LegacySwapInSignerResult {
    data class Success(
        val txId: String,
        val userPubkey: String,
        val userSignature: String
    ) : LegacySwapInSignerResult()

    sealed class Failure : LegacySwapInSignerResult() {
        data class TransactionMalformed(val details: String) : Failure()
        data class TooManyInputs(val inputSize: Int): Failure()
        data object ParentTxNotFound: Failure()
        data object SigningError : Failure()
    }
}

sealed class TaprootSwapInSignerResult {
    data class Success(
        val txId: String,
        val userPubkey: String,
        val userSignature: String,
        val userRefundKey: String,
        val userNonce: String,
    ) : TaprootSwapInSignerResult()

    sealed class Failure : TaprootSwapInSignerResult() {
        data class TransactionMalformed(val details: String) : Failure()
        data object AddressIndexNotFound : Failure()
        data object NonceGenerationFailure : Failure()
        data class TooManyInputs(val inputSize: Int): Failure()
        data object ParentTxNotFound: Failure()
        data object SwapProtocolError: Failure()
        data object SigningError : Failure()
    }
}