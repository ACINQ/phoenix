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
import fr.acinq.bitcoin.TxId
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.Feature
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.channel.ChannelCommand
import fr.acinq.lightning.channel.states.*
import fr.acinq.lightning.io.WrappedChannelCommand
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.LocalChannelInfo
import fr.acinq.phoenix.utils.Parser
import fr.acinq.phoenix.utils.extensions.isBeingCreated
import fr.acinq.lightning.logging.info
import fr.acinq.lightning.logging.warning
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object IosMigrationHelper {

    /**
     * We should migrate channels if there is at least 1 active channels that is not dual-funding.
     */
    fun shouldMigrateChannels(channels: List<LocalChannelInfo>): Boolean {
        return channels.any {
            when (val state = it.state) {
                is Offline -> state.state.isLegacy()
                is Syncing -> state.state.isLegacy()
                else -> state.isLegacy()
            }
        }
    }

    private fun ChannelState.isLegacy(): Boolean {
        return this is ChannelStateWithCommitments
            && this !is ShuttingDown && this !is Negotiating && this !is Closing && this !is Closed && this !is Aborted
            && !this.commitments.params.channelFeatures.hasFeature(Feature.DualFunding)
    }

    /**
     * There may be certain "costs" associated with closing a channel: if the channel's balance is below the dust limit,
     * then the funds go to the miners.
     *
     * Channels with balance under 1 sat are ignored.
     */
    fun migrationCosts(channels: List<LocalChannelInfo>): MilliSatoshi {
        return channels.filter { channel ->
            when (val state = channel.state) {
                is Offline -> state.state.isLegacy()
                is Syncing -> state.state.isLegacy()
                else -> state.isLegacy()
            }
        }.mapNotNull { channel ->
            channel.localBalance?.takeIf { it >= 1_000.msat && it < 546_000.msat }
        }.sum()
    }

    suspend fun doMigrateChannels(
        biz: PhoenixBusiness,
    ): IosMigrationResult {

        val loggerFactory = biz.loggerFactory
        val peerManager = biz.peerManager
        val chain = biz.chain

        try {
            val log = loggerFactory.newLogger(this::class)

            val peer = peerManager.getPeer()
            val swapInAddress = peer.swapInWallet.swapInAddressFlow.filterNotNull().first().first
            val closingScript = Parser.addressToPublicKeyScriptOrNull(chain, swapInAddress)
            if (closingScript == null) {
                log.warning { "aborting: could not get a valid closing script" }
                return IosMigrationResult.Failure.InvalidClosingScript
            }

            // checking channels
            val channelsToMigrate = peerManager.channelsFlow.filterNotNull().first().values.toList().filter { it.state.isLegacy() }
            if (channelsToMigrate.isEmpty()) {
                log.info { "aborting: no channels to migrate" }
                return IosMigrationResult.Failure.NoChannelsAvailable
            } else if (channelsToMigrate.any { it.state.isBeingCreated() }) {
                log.info { "aborting: some channels are being created" }
                return IosMigrationResult.Failure.ChannelsBeingCreated
            }

            // Tell the swap-in wallet to "pause".
            // That is: don't try to use any of the UTXOs until we're done with our migration.
            peer.stopWatchSwapInWallet()

            log.info { "migrating ${channelsToMigrate.size} channels to $swapInAddress" }
            // Close all channels in parallel
            val command = ChannelCommand.Close.MutualClose(
                scriptPubKey = ByteVector(closingScript),
                feerates = null
            )
            channelsToMigrate.forEach {
                peer.send(WrappedChannelCommand(ByteVector32.fromValidHex(it.channelId), command))
            }
            // Wait for the closing tx publication for each consolidated channel (map of channelId -> closing tx id)
            val closingTxs: MutableMap<ByteVector32, TxId> = mutableMapOf()
            channelsToMigrate.map { ByteVector32.fromValidHex(it.channelId) }.forEach { channelId ->
                // Wait until closing tx is published
                val channel = peer.channelsFlow
                    .map { it[channelId] }
                    .filterNotNull()
                    .filterIsInstance<Closing>()
                    .first { it.mutualClosePublished.isNotEmpty() }
                val closingTx = channel.mutualClosePublished.first()
                log.info { "mutual-close txid=${closingTx.tx.txid} published for channel=$channelId" }
                if (closingTx.toLocalOutput != null) {
                    closingTxs[channelId] = closingTx.tx.txid
                } else {
                    log.info { "txid=${closingTx.tx.txid} ignored (dust)" }
                }
            }
            log.info { "${closingTxs.size} channels closed to ${closingScript.byteVector().toHex()}" }

            // Wait for all UTXOs to arrive in swap-in wallet.
            peer.swapInWallet.wallet.walletStateFlow
                .map { it.utxos.map { it.outPoint.txid } }
                .first { txidsInWallet -> closingTxs.values.all { txid -> txidsInWallet.contains(txid) } }
            log.info { "all mutual-close txids found in swap-in wallet" }
            // Resume swap-in
            peer.startWatchSwapInWallet()

            return IosMigrationResult.Success(closingTxs)
        } catch (e: Exception) {
            return IosMigrationResult.Failure.Generic(e)
        }
    }
}

sealed class IosMigrationResult {
    data class Success(val closingTxs: Map<ByteVector32, TxId>) : IosMigrationResult()
    sealed class Failure : IosMigrationResult() {
        data class Generic(val error: Throwable) : Failure()
        object InvalidClosingScript : Failure()
        object NoChannelsAvailable : Failure()
        object ChannelsBeingCreated : Failure()
    }
}