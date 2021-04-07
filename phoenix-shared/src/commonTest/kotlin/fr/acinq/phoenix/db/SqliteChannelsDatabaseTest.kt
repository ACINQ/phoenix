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

import fr.acinq.phoenix.TestConstants.channel1
import fr.acinq.phoenix.TestConstants.channel2
import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.CltvExpiry
import fr.acinq.phoenix.TestConstants
import fr.acinq.phoenix.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class SqliteChannelsDatabaseTest {
    private val db = SqliteChannelsDb(testDriver(), TestConstants.nodeParams)

    @ExperimentalCoroutinesApi
    @Test
    fun basic() {
        runTest {
            val commitNumber = 42L
            val paymentHash1 = ByteVector32.Zeroes
            val cltvExpiry1 = CltvExpiry(123)
            val paymentHash2 = ByteVector32.One
            val cltvExpiry2 = CltvExpiry(456)

            assertFails { db.addHtlcInfo(channel1.channelId, commitNumber, paymentHash1, cltvExpiry1) }

            // local channels
            assertEquals(0, db.listLocalChannels().size)
            db.addOrUpdateChannel(channel1)
            assertEquals(1, db.listLocalChannels().size)
            db.addOrUpdateChannel(channel1)
            assertEquals(listOf(channel1), db.listLocalChannels())

            db.addOrUpdateChannel(channel2)
            assertEquals(listOf(channel1, channel2), db.listLocalChannels())
            db.removeChannel(channel2.channelId)
            assertEquals(listOf(channel1), db.listLocalChannels())

            // htlc infos
            assertEquals(listOf(), db.listHtlcInfos(channel1.channelId, commitNumber))
            db.addHtlcInfo(channel1.channelId, commitNumber, paymentHash1, cltvExpiry1)
            db.addHtlcInfo(channel1.channelId, commitNumber, paymentHash2, cltvExpiry2)
            assertEquals(listOf(Pair(paymentHash1, cltvExpiry1), Pair(paymentHash2, cltvExpiry2)), db.listHtlcInfos(channel1.channelId, commitNumber))
            assertEquals(listOf(), db.listHtlcInfos(channel1.channelId, commitNumber + 1))
            db.removeChannel(channel1.channelId)
            assertEquals(0, db.listLocalChannels().size)
            assertEquals(listOf(), db.listHtlcInfos(channel1.channelId, commitNumber))
        }
    }
}

expect fun testDriver(): SqlDriver