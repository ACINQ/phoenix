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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.transactions.CommitmentSpec
import fr.acinq.lightning.utils.sum


val ChannelState.localCommitmentSpec: CommitmentSpec? get() =
    when (this) {
        is ChannelStateWithCommitments -> commitments.localCommit.spec
        is Offline -> state.localCommitmentSpec
        is Syncing -> state.localCommitmentSpec
        else -> null
    }

fun calculateBalance(channels: Map<ByteVector32, ChannelState>): MilliSatoshi {
    return channels.values.map {
        // If the channel is offline, we want to display the underlying balance.
        // The alternative is to display a zero balance whenever the user is offline.
        // And if there's one sure way to make user's freak out,
        // it's showing them a zero balance...
        val channel = when (it) {
            is Offline -> it.state
            is Syncing -> {
                // https://github.com/ACINQ/phoenix-kmm/issues/195
                when (val underlying = it.state) {
                    is WaitForFundingConfirmed -> null
                    else -> underlying
                }
            }
            else -> it
        }
        when (channel) {
            is Closing -> MilliSatoshi(0)
            is Closed -> MilliSatoshi(0)
            is Aborted -> MilliSatoshi(0)
            is ErrorInformationLeak -> MilliSatoshi(0)
            else -> channel?.localCommitmentSpec?.toLocal ?: MilliSatoshi(0)
        }
    }.sum()
}
