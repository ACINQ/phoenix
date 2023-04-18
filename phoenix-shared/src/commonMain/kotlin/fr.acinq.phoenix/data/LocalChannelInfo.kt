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

import fr.acinq.lightning.channel.*
import fr.acinq.lightning.transactions.*
import fr.acinq.phoenix.utils.extensions.*

/**
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
    val isTerminated by lazy { state is Closing || state is Closed || state is Aborted }
    /** True if the channel can be used to forward payments. */
    val isUsable by lazy { state is Normal && !isBooting }
    /** Smart local balance after taking the state into account. For the raw balance, see [CommitmentSpec.toLocal]. */
    val localBalance by lazy { state.localBalance() }
    /** Raw balance of the peer */
    val remoteBalance by lazy { state.localCommitmentSpec?.toRemote }
    val fundingTx by lazy { (state as? ChannelStateWithCommitments)?.commitments?.latest?.fundingTxId?.toHex() }
}