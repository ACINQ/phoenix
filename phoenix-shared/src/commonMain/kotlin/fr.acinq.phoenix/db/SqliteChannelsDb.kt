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
import fr.acinq.lightning.serialization.Serialization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class SqliteChannelsDb(private val driver: SqlDriver) : ChannelsDb {

    private val database = ChannelsDatabase(driver)
    private val queries = database.channelsDatabaseQueries

    override suspend fun addOrUpdateChannel(state: PersistedChannelState) {
        val channelId = state.channelId.toByteArray()
        val data = Serialization.serialize(state)
        withContext(Dispatchers.Default) {
            queries.transaction {
                queries.getChannel(channelId).executeAsOneOrNull()?.run {
                    queries.updateChannel(channel_id = this.channel_id, data_ = data)
                } ?: run {
                    queries.insertChannel(channel_id = channelId, data_ = data)
                }
            }
        }
    }

    override suspend fun removeChannel(channelId: ByteVector32) {
        withContext(Dispatchers.Default) {
            queries.deleteHtlcInfo(channel_id = channelId.toByteArray())
            queries.closeLocalChannel(channel_id = channelId.toByteArray())
        }
    }

    override suspend fun listLocalChannels(): List<PersistedChannelState> = withContext(Dispatchers.Default) {
        val bytes = queries.listLocalChannels().executeAsList()
        bytes.mapNotNull {
            when (val res = Serialization.deserialize(it)) {
                is Serialization.DeserializationResult.Success -> res.state
                is Serialization.DeserializationResult.UnknownVersion -> null
            }
        }
    }

    override suspend fun addHtlcInfo(channelId: ByteVector32, commitmentNumber: Long, paymentHash: ByteVector32, cltvExpiry: CltvExpiry) {
        withContext(Dispatchers.Default) {
            queries.insertHtlcInfo(
                channel_id = channelId.toByteArray(),
                commitment_number = commitmentNumber,
                payment_hash = paymentHash.toByteArray(),
                cltv_expiry = cltvExpiry.toLong())
        }
    }

    override suspend fun listHtlcInfos(channelId: ByteVector32, commitmentNumber: Long): List<Pair<ByteVector32, CltvExpiry>> {
        return withContext(Dispatchers.Default) {
            queries.listHtlcInfos(channel_id = channelId.toByteArray(), commitment_number = commitmentNumber, mapper = { payment_hash, cltv_expiry ->
                ByteVector32(payment_hash) to CltvExpiry(cltv_expiry)
            }).executeAsList()
        }
    }

    override fun close() {
        driver.close()
    }
}
