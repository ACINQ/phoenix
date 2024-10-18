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

package fr.acinq.phoenix.android.payments.send.spliceout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.ChannelCommand
import fr.acinq.lightning.channel.ChannelFundingResponse
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class SpliceOutState {
    data object Init: SpliceOutState()
    data class Preparing(val userAmount: Satoshi, val feeratePerByte: Satoshi): SpliceOutState()
    data class ReadyToSend(val userAmount: Satoshi, val userFeerate: FeeratePerKw, val actualFeerate: FeeratePerKw, val estimatedFee: Satoshi): SpliceOutState()
    data class Executing(val userAmount: Satoshi, val feerate: FeeratePerKw): SpliceOutState()
    sealed class Complete: SpliceOutState() {
        abstract val userAmount: Satoshi
        abstract val feerate: FeeratePerKw
        abstract val result: ChannelFundingResponse
        data class Success(override val userAmount: Satoshi, override val feerate: FeeratePerKw, override val result: ChannelFundingResponse.Success): Complete()
        data class Failure(override val userAmount: Satoshi, override val feerate: FeeratePerKw, override val result: ChannelFundingResponse.Failure): Complete()
    }
    sealed class Error: SpliceOutState() {
        data class Thrown(val e: Throwable): Error()
        data object NoChannels: Error()
    }
}

class SpliceOutViewModel(private val peerManager: PeerManager, private val chain: Chain): ViewModel() {
    val log = LoggerFactory.getLogger(this::class.java)
    var state by mutableStateOf<SpliceOutState>(SpliceOutState.Init)

    /** Estimate the fee for the splice-out, given a feerate. */
    fun prepareSpliceOut(
        amount: Satoshi,
        feeratePerByte: Satoshi,
        address: String,
    ) {
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("error when preparing splice-out: ", e)
            state = SpliceOutState.Error.Thrown(e)
        }) {
            state = SpliceOutState.Preparing(userAmount = amount, feeratePerByte = feeratePerByte)
            log.debug("preparing splice-out for amount={} feerate={}sat/vb address={}", amount, feeratePerByte, address)
            val userFeerate = FeeratePerKw(FeeratePerByte(feeratePerByte))
            val scriptPubKey = Parser.addressToPublicKeyScriptOrNull(chain, address)!!
            val res = peerManager.getPeer().estimateFeeForSpliceOut(
                amount = amount,
                targetFeerate = userFeerate,
                scriptPubKey = scriptPubKey
            )
            state = when (res) {
                null -> SpliceOutState.Error.NoChannels
                else -> {
                    val (actualFeerate, fee) = res
                    log.info("received actual feerate=$actualFeerate from splice-out estimate fee")
                    if (fee.serviceFee > 0.sat) {
                        throw IllegalArgumentException("service fee above 0")
                    } else {
                        SpliceOutState.ReadyToSend(amount, userFeerate, actualFeerate, estimatedFee = fee.miningFee)
                    }
                }
            }
        }
    }

    fun executeSpliceOut(
        amount: Satoshi,
        feerate: FeeratePerKw,
        address: String,
    ) {
        if (state is SpliceOutState.ReadyToSend) {
            state = SpliceOutState.Executing(amount, feerate)
            log.debug("executing splice-out with for={} feerate={}sat/vb address={}", amount, feerate, address)
            viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
                log.error("error when executing splice-out: ", e)
                state = SpliceOutState.Error.Thrown(e)
            }) {
                val response = peerManager.getPeer().spliceOut(
                    amount = amount,
                    scriptPubKey = Parser.addressToPublicKeyScriptOrNull(chain, address)!!,
                    feerate = feerate,
                )
                when (response) {
                    is ChannelFundingResponse.Success -> {
                        log.info("successfully executed splice-out: $response")
                        state = SpliceOutState.Complete.Success(amount, feerate, response)
                    }
                    is ChannelFundingResponse.Failure -> {
                        log.info("failed to execute splice-out: $response")
                        state = SpliceOutState.Complete.Failure(amount, feerate, response)
                    }
                    null -> {
                        log.info("failed to execute splice-out: assuming no channels available")
                        state = SpliceOutState.Error.NoChannels
                    }
                }
            }
        }
    }

    class Factory(
        private val peerManager: PeerManager, private val chain: Chain
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SpliceOutViewModel(peerManager, chain) as T
        }
    }
}