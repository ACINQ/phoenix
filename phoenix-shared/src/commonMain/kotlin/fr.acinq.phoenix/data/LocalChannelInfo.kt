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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.states.*
import fr.acinq.lightning.json.JsonSerializers
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.managers.PeerManager
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
    /** True if the channel is `LegacyWaitForFundingConfirmed`, i.e., it may be a zombie channel. */
    val isLegacyWait by lazy { state.isLegacyWait() }
    /** A string version of the state's class. */
    val stateName by lazy { state.stateName }
    // FIXME: we should also expose the raw channel's balance, which is what should be used in the channel's details screen, rather than the "smart" spendable balance returned by `localBalance()`
    /** The channel's spendable balance, as seen in [ChannelState.localBalance]. */
    val localBalance by lazy { state.localBalance() }
    /**
     * The channel's receive capacity - should be accurate but still depends on the network feerate.
     *
     * @return null if the channel is not NORMAL, otherwise the receive capacity.
     */
    val availableForReceive by lazy {
        when (state) {
            is Normal -> state.commitments.availableBalanceForReceive()
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
                        fundingTxId = it.fundingTxId,
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
                        fundingTxId = it.fundingTxId,
                        fundingTxIndex = it.fundingTxIndex,
                        fundingAmount = it.fundingAmount,
                        balanceForSend = it.availableBalanceForSend(params, changes)
                    )
                }.sortedByDescending { it.fundingTxIndex }
            }
            else -> emptyList()
        }
    }
    /** Returns the count of payments being sent or received by this channel. */
    val inFlightPaymentsCount: Int by lazy {
        when (state) {
            is ChannelStateWithCommitments -> {
                buildSet {
                    state.commitments.latest.localCommit.spec.htlcs.forEach { add(it.add.paymentHash) }
                    state.commitments.latest.remoteCommit.spec.htlcs.forEach { add(it.add.paymentHash) }
                    state.commitments.latest.nextRemoteCommit?.commit?.spec?.htlcs?.forEach { add(it.add.paymentHash) }
                }.size
            }
            else -> 0
        }
    }
    /** The channel's data serialized in a json string. */
    val json: String by lazy { JsonSerializers.json.encodeToString(state) }

    /** Stripped-down commitment, easier to consume from the frontend. */
    data class CommitmentInfo(
        val fundingTxId: TxId,
        val fundingTxIndex: Long,
        val fundingAmount: Satoshi,
        val balanceForSend: MilliSatoshi,
    )

    companion object { /* allow companion extensions; see PhoenixExposure.kt */ }
}

/**
 * Helper method that returns the **relevant** receive balance of channels exposed in the [PeerManager]'s channels flow.
 *
 * The point is to avoid taking into account the liquidity of non-usable channels - for example, syncing channels, or channels being reestablished.
 *
 * @return Null if:
 *          - the node is not yet fully initialized and the channels flow is in limbo.
 *          - the node is initialized, but channels are still local (not yet reestablished with the peer)
 *      0 msat if:
 *          - the map is empty (no active channels in the node);
 *          - the map is not empty, but all active channels are in a non-NORMAL state. For example, channels being closed.
 *      the actual receive balance (that may be 0) if:
 *          - there's at least 1 NORMAL channel.
 */
fun Map<ByteVector32, LocalChannelInfo>?.availableForReceive(): MilliSatoshi? {
    return this?.values?.availableForReceive()
}

fun Collection<LocalChannelInfo>.availableForReceive(): MilliSatoshi? {
    return when {
        this.isEmpty() -> 0.msat
        this.all { it.isBooting } -> null
        this.all { it.state is Syncing } -> null
        else -> this.map {
            if (it.state is Syncing || it.isBooting) {
                null
            } else {
                it.availableForReceive ?: 0.msat
            }
        }.reduce { a, b ->
            when {
                a == null && b == null -> null
                a != null && b != null -> a + b
                a == null && b != null -> b
                else -> a
            }
        }
    }
}

/** Liquidity can be requested if you have at least 1 usable channel. */
fun Map<ByteVector32, LocalChannelInfo>?.canRequestLiquidity(): Boolean {
    return this?.values?.canRequestLiquidity() ?: false
}

fun Collection<LocalChannelInfo>.canRequestLiquidity(): Boolean {
    return this.any { it.isUsable }
}

fun Map<ByteVector32, LocalChannelInfo>?.inFlightPaymentsCount(): Int {
    return this?.values?.inFlightPaymentsCount() ?: 0
}

fun Collection<LocalChannelInfo>.inFlightPaymentsCount(): Int {
    return this.sumOf { it.inFlightPaymentsCount }
}
