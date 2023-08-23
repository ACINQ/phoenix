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

package fr.acinq.phoenix.utils.migrations

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.Feature
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.channel.ChannelCommand
import fr.acinq.lightning.channel.states.ChannelStateWithCommitments
import fr.acinq.lightning.channel.states.Closing
import fr.acinq.lightning.channel.states.Normal
import fr.acinq.lightning.io.WrappedChannelCommand
import fr.acinq.lightning.utils.msat
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.LocalChannelInfo
import fr.acinq.phoenix.managers.PeerManager
import fr.acinq.phoenix.utils.Parser
import fr.acinq.phoenix.utils.extensions.isBeingCreated
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

object ChannelsConsolidationHelper {

    /**
     * Channels can be consolidated if:
     * - there's only 1 active channel but it's not dual-funding.
     * - there are several active channels (even if they are dual-funding).
     */
    fun canConsolidate(channels: List<LocalChannelInfo>): Boolean {
        val activeChannels = channels.filter { it.isUsable }
        return when {
            activeChannels.size == 1 && !isDualFunding(channels.first()) -> true
            activeChannels.size > 1 -> true
            else -> false
        }
    }

    private fun isDualFunding(channel: LocalChannelInfo): Boolean {
        return when (val state = channel.state) {
            is ChannelStateWithCommitments -> {
                state.commitments.params.channelFeatures.hasFeature(Feature.DualFunding)
            }
            else -> {
                false
            }
        }
    }

    suspend fun consolidateChannels(
        biz: PhoenixBusiness,
        ignoreDust: Boolean
    ): ChannelsConsolidationResult {
        return consolidateChannels(
            loggerFactory = biz.loggerFactory,
            chain = biz.chain,
            peerManager = biz.peerManager,
            ignoreDust = ignoreDust
        )
    }

    suspend fun consolidateChannels(
        loggerFactory: LoggerFactory,
        chain: NodeParams.Chain,
        peerManager: PeerManager,
        ignoreDust: Boolean
    ): ChannelsConsolidationResult {
        try {
            val log = loggerFactory.newLogger(this::class)

            val swapInAddress = peerManager.getPeer().swapInAddress
            val closingScript = Parser.addressToPublicKeyScript(chain, swapInAddress)
            if (closingScript == null) {
                log.info { "aborting: could not get a valid closing script" }
                return ChannelsConsolidationResult.Failure.InvalidClosingScript
            }

            // checking channels
            val allChannels = peerManager.channelsFlow.filterNotNull().first().values.toList()
            if (allChannels.isEmpty()) {
                log.info { "aborting: no channels available" }
                return ChannelsConsolidationResult.Failure.NoChannelsAvailable
            } else if (allChannels.any { it.state.isBeingCreated() }) {
                log.info { "aborting: some channels are being created" }
                return ChannelsConsolidationResult.Failure.ChannelsBeingCreated
            }

            // check dust
            val channelsToConsolidate = allChannels.filter { it.state is Normal }
            val dustChannels = channelsToConsolidate.filter {
                val balance = it.localBalance ?: 0.msat
                balance > 0.msat && balance < 546_000.msat
            }
            if (!ignoreDust) {
                if (dustChannels.isNotEmpty()) {
                    log.info { "aborting: ${dustChannels.size}/${channelsToConsolidate.size} dust channels" }
                    return ChannelsConsolidationResult.Failure.DustChannels(
                        dustChannels = dustChannels.map { it.channelId }.toSet(),
                        allChannels = channelsToConsolidate.map { it.channelId }.toSet(),
                    )
                }
            }

            // migrate channels
            log.info { "consolidating ${channelsToConsolidate.size} channels to $swapInAddress" }
            val peer = peerManager.getPeer()
            val command = ChannelCommand.Close.MutualClose(
                scriptPubKey = ByteVector(closingScript),
                feerates = null
            )
            channelsToConsolidate.forEach {
                peer.send(WrappedChannelCommand(ByteVector32.fromValidHex(it.channelId), command))
            }

            // checking every 3s the closing tx publication for each consolidated channel (map of channelId -> isPublished)
            val closingPublishedMap = channelsToConsolidate.map { ByteVector32.fromValidHex(it.channelId) }.associateWith { false }.toMutableMap()
            val mutualClosePublishedTxs = mutableSetOf<ByteVector32>()
            while (closingPublishedMap.any { !it.value }) {
                val notPublished = closingPublishedMap.filter { !it.value }.keys
                log.info { "checking closing status of ${notPublished.size}/${closingPublishedMap.size} channels" }
                notPublished.forEach { channelId ->
                    val channel = peer.channels[channelId]
                    if (channel is Closing && channel.mutualClosePublished.isNotEmpty()) {
                        log.info { "mutual-close published for channel=$channelId" }
                        mutualClosePublishedTxs.add(channel.mutualClosePublished.first().tx.txid)
                        closingPublishedMap[channelId] = true
                    } else {
                        log.info { "ignore channel=$channelId in state=${channel?.stateName}" }
                    }
                }
                log.info { "${closingPublishedMap.filter { it.value }.size}/${closingPublishedMap.size} closing published" }
                delay(3000)
            }
            log.info { "${closingPublishedMap.size} channels closed to $closingScript" }
            return ChannelsConsolidationResult.Success(closingPublishedMap.keys, mutualClosePublishedTxs)
        } catch (e: Exception) {
            return ChannelsConsolidationResult.Failure.Generic(e)
        }
    }
}

sealed class ChannelsConsolidationResult {
    data class Success(val channels: Set<ByteVector32>, val closingTxs: Set<ByteVector32>) : ChannelsConsolidationResult()
    sealed class Failure : ChannelsConsolidationResult() {
        data class Generic(val error: Throwable) : Failure()
        object InvalidClosingScript : Failure()
        object NoChannelsAvailable : Failure()
        object ChannelsBeingCreated : Failure()
        data class DustChannels(val dustChannels: Set<String>, val allChannels: Set<String>) : Failure()
    }
}