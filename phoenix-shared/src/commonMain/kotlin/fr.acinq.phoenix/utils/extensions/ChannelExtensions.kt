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
import fr.acinq.lightning.transactions.CommitmentSpec


val ChannelState.localCommitmentSpec: CommitmentSpec? get() =
    when (this) {
        is ChannelStateWithCommitments -> commitments.latest.localCommit.spec
        is Offline -> state.localCommitmentSpec
        is Syncing -> state.localCommitmentSpec
        else -> null
    }

fun ChannelStateWithCommitments.minDepthForFunding(nodeParams: NodeParams): Int {
    return Helpers.minDepthForFunding(
        nodeParams = nodeParams,
        fundingAmount = commitments.latest.fundingAmount
    )
}

fun ChannelState.localBalance(): MilliSatoshi {
    val channel = when (this) {
        is Offline -> this.state
        is Syncing -> {
            // https://github.com/ACINQ/phoenix-kmm/issues/195
            when (val underlying = this.state) {
                is WaitForFundingConfirmed -> null
                else -> underlying
            }
        }
        else -> this
    }
    return when (channel) {
        is Closing -> MilliSatoshi(0)
        is Closed -> MilliSatoshi(0)
        is Aborted -> MilliSatoshi(0)
        is ErrorInformationLeak -> MilliSatoshi(0)
        is WaitForChannelReady -> MilliSatoshi(0)
        is LegacyWaitForFundingLocked -> MilliSatoshi(0)
        is WaitForFundingConfirmed -> MilliSatoshi(0)
        is LegacyWaitForFundingConfirmed -> MilliSatoshi(0)
        else -> channel?.localCommitmentSpec?.toLocal ?: MilliSatoshi(0)
    }
}
