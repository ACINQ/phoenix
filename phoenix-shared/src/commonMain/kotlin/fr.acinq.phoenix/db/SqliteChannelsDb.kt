/*
 * Copyright 2020 ACINQ SAS
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

package fr.acinq.phoenix.db

import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.CltvExpiry
import fr.acinq.lightning.channel.states.PersistedChannelState
import fr.acinq.lightning.db.ChannelsDb
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.phoenix.db.sqldelight.ChannelsDatabase
import kotlin.collections.List
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqliteChannelsDb(val driver: SqlDriver, database: ChannelsDatabase, loggerFactory: LoggerFactory) : ChannelsDb {

    val log = loggerFactory.newLogger(this::class)
    private val queries = database.channelsDatabaseQueries

    override suspend fun addOrUpdateChannel(state: PersistedChannelState) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                queries.getChannel(state.channelId).executeAsOneOrNull()?.run {
                    queries.updateChannel(channel_id = state.channelId, data_ = PersistedChannelStateAdapter.encode(state))
                } ?: run {
                    queries.insertChannel(channel_id = state.channelId, data_ = PersistedChannelStateAdapter.encode(state))
                }
            }
        }
    }

    suspend fun getChannel(channelId: ByteVector32): Triple<ByteVector32, PersistedChannelState, Boolean>? {
        return withContext(Dispatchers.Default) {
            queries.getChannel(channelId).executeAsOneOrNull()?.let { (channelId, data, isClosed) ->
                mapChannelData(channelId, data)?.let {
                    Triple(channelId, it, isClosed)
                }
            }
        }
    }

    override suspend fun removeChannel(channelId: ByteVector32) {
        withContext(Dispatchers.Default) {
            queries.deleteHtlcInfo(channel_id = channelId)
            queries.closeLocalChannel(channel_id = channelId)
        }
    }

    override suspend fun listLocalChannels(): List<PersistedChannelState> = withContext(Dispatchers.Default) {
        queries.listLocalChannels().executeAsList().mapNotNull { (channelId, data) -> mapChannelData(channelId, data) }
    }

    override suspend fun addHtlcInfo(channelId: ByteVector32, commitmentNumber: Long, paymentHash: ByteVector32, cltvExpiry: CltvExpiry) {
        withContext(Dispatchers.Default) {
            queries.insertHtlcInfo(
                channel_id = channelId,
                commitment_number = commitmentNumber,
                payment_hash = paymentHash,
                cltv_expiry = cltvExpiry.toLong()
            )
        }
    }

    override suspend fun listHtlcInfos(channelId: ByteVector32, commitmentNumber: Long): List<Pair<ByteVector32, CltvExpiry>> {
        return withContext(Dispatchers.Default) {
            queries.listHtlcInfos(channel_id = channelId, commitment_number = commitmentNumber, mapper = { payment_hash, cltv_expiry ->
                payment_hash to CltvExpiry(cltv_expiry)
            }).executeAsList()
        }
    }

    private fun mapChannelData(channelId: ByteVector32, data: ByteArray): PersistedChannelState? {
        return try {
            PersistedChannelStateAdapter.decode(data)
        } catch (e: Exception) {
            log.e(e) { "failed to read channel data for channel=$channelId :" }
            null
        }
    }

    override fun close() {
        driver.close()
    }
}