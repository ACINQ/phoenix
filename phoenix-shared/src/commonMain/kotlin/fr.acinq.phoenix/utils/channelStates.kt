package fr.acinq.phoenix.utils

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
