/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.phoenix.android.payments.liquidity

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.ChannelCommand
import fr.acinq.lightning.channel.ChannelManagementFees
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.PeerManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


sealed class RequestLiquidityState {
    object Init: RequestLiquidityState()
    object Estimating: RequestLiquidityState()
    data class Estimation(val amount: Satoshi, val fees: ChannelManagementFees, val actualFeerate: FeeratePerKw, val fundingRate: LiquidityAds.FundingRate): RequestLiquidityState()
    object Requesting: RequestLiquidityState()
    sealed class Complete: RequestLiquidityState() {
        abstract val response: ChannelCommand.Commitment.Splice.Response
        data class Success(override val response: ChannelCommand.Commitment.Splice.Response.Created): Complete()
        data class Failed(override val response: ChannelCommand.Commitment.Splice.Response.Failure): Complete()
    }
    sealed class Error: RequestLiquidityState() {
        data class Thrown(val cause: Throwable): Error()
        data object NoChannelsAvailable: Error()
        data object InvalidFundingAmount: Error()
    }
}

class RequestLiquidityViewModel(val peerManager: PeerManager, val appConfigManager: AppConfigurationManager): ViewModel() {

    private val log = LoggerFactory.getLogger(this::class.java)
    val state = mutableStateOf<RequestLiquidityState>(RequestLiquidityState.Init)

    fun estimateFeeForInboundLiquidity(amount: Satoshi) {
        if (state.value is RequestLiquidityState.Estimating || state.value is RequestLiquidityState.Requesting) return
        state.value = RequestLiquidityState.Estimating
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to estimate fee for inbound liquidity: ", e)
            state.value = RequestLiquidityState.Error.Thrown(e)
        }) {
            val peer = peerManager.getPeer()
            val feerate = appConfigManager.mempoolFeerate.filterNotNull().first().hour
            val fundingRate = peer.remoteFundingRates.filterNotNull().first().findRate(amount)
            if (fundingRate == null) {
                state.value = RequestLiquidityState.Error.InvalidFundingAmount
                return@launch
            }

            peer.estimateFeeForInboundLiquidity(
                amount = amount,
                targetFeerate = FeeratePerKw(feerate),
                fundingRate = fundingRate,
            ).let { response ->
                state.value = when (response) {
                    null -> RequestLiquidityState.Error.NoChannelsAvailable
                    else -> {
                        val (actualFeerate, fees) = response
                        RequestLiquidityState.Estimation(amount, fees, actualFeerate, fundingRate)
                    }
                }
            }
        }
    }

    fun requestInboundLiquidity(amount: Satoshi, feerate: FeeratePerKw, fundingRate: LiquidityAds.FundingRate) {
        if (state.value is RequestLiquidityState.Requesting) return
        state.value = RequestLiquidityState.Requesting
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            log.error("failed to request inbound liquidity: ", e)
            state.value = RequestLiquidityState.Error.Thrown(e)
        }) {
            val peer = peerManager.getPeer()
            peer.requestInboundLiquidity(
                amount = amount,
                feerate = feerate,
                fundingRate = fundingRate,
            ).let { response ->
                state.value = when (response) {
                    null -> RequestLiquidityState.Error.NoChannelsAvailable
                    is ChannelCommand.Commitment.Splice.Response.Failure -> RequestLiquidityState.Complete.Failed(response)
                    is ChannelCommand.Commitment.Splice.Response.Created -> RequestLiquidityState.Complete.Success(response)
                }
            }
        }
    }

    class Factory(
        private val peerManager: PeerManager,
        private val appConfigManager: AppConfigurationManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return RequestLiquidityViewModel(peerManager, appConfigManager) as T
        }
    }
}