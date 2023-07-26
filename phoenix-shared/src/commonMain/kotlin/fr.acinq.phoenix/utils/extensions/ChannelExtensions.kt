/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.phoenix.utils.extensions

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.channel.states.*
import fr.acinq.lightning.utils.msat


fun ChannelStateWithCommitments.minDepthForFunding(nodeParams: NodeParams): Int {
    return Helpers.minDepthForFunding(
        nodeParams = nodeParams,
        fundingAmount = commitments.latest.fundingAmount
    )
}

fun ChannelState.isTerminated(): Boolean {
    return when (this) {
        is Syncing, is Offline -> this.isTerminated()
        is Closing, is Closed, is Aborted -> true
        else -> false
    }
}

fun ChannelState.isBeingCreated(): Boolean {
    return when (this) {
        is Syncing, is Offline -> this.isBeingCreated()
        is LegacyWaitForFundingLocked,
        is LegacyWaitForFundingConfirmed,
        is WaitForAcceptChannel,
        is WaitForChannelReady,
        is WaitForFundingConfirmed,
        is WaitForFundingCreated,
        is WaitForFundingSigned,
        is WaitForInit,
        is WaitForOpenChannel,
        is WaitForRemotePublishFutureCommitment -> true
        else -> false
    }
}

/**
 * The balance that we can use to spend. Uses the [Commitment.availableBalanceForSend] method under the hood.
 * For some states, this balance is forced to null.
 */
fun ChannelState.localBalance(): MilliSatoshi? {
    return when (this) {
        // if offline or syncing, check the underlying state.
        is Offline -> state.localBalance()
        is Syncing -> state.localBalance()
        // for some states the balance should be 0
        is Closing -> 0.msat
        is Closed -> 0.msat
        is Aborted -> null
        // balance is unknown
        is LegacyWaitForFundingLocked -> null
        is LegacyWaitForFundingConfirmed -> null
        is WaitForAcceptChannel -> null
        is WaitForChannelReady -> null
        is WaitForFundingConfirmed -> null
        is WaitForFundingCreated -> null
        is WaitForFundingSigned -> null
        is WaitForInit -> null
        is WaitForOpenChannel -> null
        is WaitForRemotePublishFutureCommitment -> null
        // regular case
        is ChannelStateWithCommitments -> commitments.availableBalanceForSend()
        else -> null
    }
}
