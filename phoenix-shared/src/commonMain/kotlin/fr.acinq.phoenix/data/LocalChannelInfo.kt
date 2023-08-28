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

package fr.acinq.phoenix.data

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.states.*
import fr.acinq.lightning.json.JsonSerializers
import fr.acinq.phoenix.utils.extensions.*
import kotlinx.serialization.encodeToString

/**
 * This class exposes a channel's more important information in an easy-to-consume format.
 *
 * @param channelId the channel's identifier as a hexadecimal string.
 * @param state the channel's state that may contain commitments information.
 * @param isBooting if true, this data comes from the local database and the channel has not yet been reestablished
 *      with the peer. As such, the data may be obsolete (for example, if the peer actually force-closed the channel
 *      while Phoenix was disconnected). If false, this data is the live view of the channel as negotiated with the
 *      peer and can be considered up-to-date (but there's no guarantee, as the channel may actually be disconnected!).
 */
data class LocalChannelInfo(
    val channelId: String,
    val state: ChannelState,
    val isBooting: Boolean,
) {
    /** True if the channel is terminated and will never be usable again. */
    val isTerminated by lazy { state.isTerminated() }
    /** True if the channel can be used to send/receive payments. */
    val isUsable by lazy { state is Normal && !isBooting }
    /** A string version of the state's class. */
    val stateName by lazy { state.stateName }
    // FIXME: we should also expose the raw channel's balance, which is what should be used in the channel's details screen, rather than the "smart" spendable balance returned by `localBalance()`
    /** The channel's spendable balance, as seen in [ChannelState.localBalance]. */
    val localBalance by lazy { state.localBalance() }
    /** The channel's receive capacity - should be accurate but still depends on the network feerate. */
    val availableForReceive by lazy {
        when (state) {
            is ChannelStateWithCommitments -> state.commitments.availableBalanceForReceive()
            else -> null
        }
    }
    /** The channel's current capacity. It actually is the funding capacity of the latest commitment. */
    val currentFundingAmount by lazy { if (state is ChannelStateWithCommitments) state.commitments.latest.fundingAmount else null }
    /** A channel may have several active commitments. */
    val commitmentsInfo: List<CommitmentInfo> by lazy {
        when (state) {
            is ChannelStateWithCommitments -> {
                val params = state.commitments.params
                val changes = state.commitments.changes
                state.commitments.active.map {
                    CommitmentInfo(
                        fundingTxId = it.fundingTxId.toHex(),
                        fundingTxIndex = it.fundingTxIndex,
                        fundingAmount = it.fundingAmount,
                        balanceForSend = it.availableBalanceForSend(params, changes)
                    )
                }.sortedByDescending { it.fundingTxIndex }
            }
            else -> emptyList()
        }
    }
    /** A channel may have several inactive commitments. */
    val inactiveCommitmentsInfo: List<CommitmentInfo> by lazy {
        when (state) {
            is ChannelStateWithCommitments -> {
                val params = state.commitments.params
                val changes = state.commitments.changes
                state.commitments.inactive.map {
                    CommitmentInfo(
                        fundingTxId = it.fundingTxId.toHex(),
                        fundingTxIndex = it.fundingTxIndex,
                        fundingAmount = it.fundingAmount,
                        balanceForSend = it.availableBalanceForSend(params, changes)
                    )
                }.sortedByDescending { it.fundingTxIndex }
            }
            else -> emptyList()
        }
    }
    /** The channel's data serialized in a json string. */
    val json: String by lazy { JsonSerializers.json.encodeToString(state) }

    /** Stripped-down commitment, easier to consume from the frontend. */
    data class CommitmentInfo(
        val fundingTxId: String,
        val fundingTxIndex: Long,
        val fundingAmount: Satoshi,
        val balanceForSend: MilliSatoshi,
    )
}