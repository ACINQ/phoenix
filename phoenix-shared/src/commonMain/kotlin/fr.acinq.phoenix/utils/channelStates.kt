package fr.acinq.phoenix.utils

import fr.acinq.lightning.channel.ChannelState
import fr.acinq.lightning.channel.ChannelStateWithCommitments
import fr.acinq.lightning.channel.Offline
import fr.acinq.lightning.channel.Syncing
import fr.acinq.lightning.transactions.CommitmentSpec


val ChannelState.localCommitmentSpec: CommitmentSpec? get() =
    when (this) {
        is ChannelStateWithCommitments -> commitments.localCommit.spec
        is Offline -> state.localCommitmentSpec
        is Syncing -> state.localCommitmentSpec
        else -> null
    }
