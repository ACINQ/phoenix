/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.send.cpfp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.ChannelFundingResponse
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.WalletId
import fr.acinq.phoenix.android.components.getLogger
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.logger.LogHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class CpfpState {
    object Init : CpfpState()
    data class EstimatingFee(val userFeerate: FeeratePerKw) : CpfpState()
    data class ReadyToExecute(val userFeerate: FeeratePerKw, val actualFeerate: FeeratePerKw, val fee: Satoshi) : CpfpState()
    data class Executing(val actualFeerate: FeeratePerKw) : CpfpState()
    sealed class Complete : CpfpState() {
        object Success: Complete()
        data class Failed(val failure: ChannelFundingResponse.Failure): Complete()
    }
    sealed class Error: CpfpState() {
        data class Thrown(val e: Throwable): Error()
        object NoChannels: Error()
        data class FeerateTooLow(val userFeerate: FeeratePerKw, val actualFeerate: FeeratePerKw): Error()
    }
}


class CpfpViewModel(
    val application: PhoenixApplication,
    val walletId: WalletId,
    val peerManager: PeerManager
) : ViewModel() {
    private val log = LogHelper.getLogger(application.applicationContext, walletId, this)
    var state by mutableStateOf<CpfpState>(CpfpState.Init)

    fun estimateFee(
        channelId: ByteVector32,
        feerate: Satoshi,
    ) {
        if (state is CpfpState.EstimatingFee) return
        val userFeerate = FeeratePerKw(FeeratePerByte(feerate))
        state = CpfpState.EstimatingFee(userFeerate)
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to estimate cpfp fee on channel=$channelId: ", e)
            state = CpfpState.Error.Thrown(e)
        }) {
            val res = peerManager.getPeer().estimateFeeForSpliceCpfp(
                channelId = channelId,
                targetFeerate = userFeerate
            )
            state = when (res) {
                null -> CpfpState.Error.NoChannels
                else -> {
                    val (actualFeerate, fees) = res
                    if (actualFeerate <= userFeerate * 1.10) {
                        CpfpState.Error.FeerateTooLow(userFeerate = userFeerate, actualFeerate = actualFeerate)
                    } else if (fees.serviceFee > 0.sat) {
                        throw IllegalArgumentException("service fee above 0")
                    } else {
                        CpfpState.ReadyToExecute(userFeerate = userFeerate, actualFeerate = res.first, fee = fees.miningFee)
                    }
                }
            }
        }
    }

    fun executeCpfp(
        channelId: ByteVector32,
        actualFeerate: FeeratePerKw,
    ) {
        if (state is CpfpState.ReadyToExecute) {
            state = CpfpState.Executing(actualFeerate)
            viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
                log.error("failed to execute cpfp: ", e)
                state = CpfpState.Error.Thrown(e)
            }) {
                when (val res = peerManager.getPeer().spliceCpfp(channelId, actualFeerate)) {
                    null -> {
                        log.info("failed to execute cpfp splice: assuming no channels")
                        state = CpfpState.Error.NoChannels
                    }
                    is ChannelFundingResponse.Success -> {
                        log.info("successfully executed cpfp splice: $res")
                        state = CpfpState.Complete.Success
                    }
                    is ChannelFundingResponse.Failure -> {
                        log.info("failed to execute cpfp splice: $res")
                        state = CpfpState.Complete.Failed(res)
                    }
                }
            }
        }

    }

    class Factory(
        val application: PhoenixApplication,
        val walletId: WalletId,
        private val peerManager: PeerManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CpfpViewModel(application, walletId, peerManager) as T
        }
    }
}