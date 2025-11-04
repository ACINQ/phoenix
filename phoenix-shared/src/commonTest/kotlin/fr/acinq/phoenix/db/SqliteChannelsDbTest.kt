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
import fr.acinq.phoenix.utils.runTest
import fr.acinq.phoenix.utils.testLoggerFactory
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.*

class SqliteChannelsDbTest : UsingContextTest() {

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setupDatabases() {
        Dispatchers.setMain(mainThreadSurrogate)
        val sampleDbs = "src/commonTest/resources/sampledbs/channelsdb"
        val v1: List<Path> = FileSystem.SYSTEM.list("$sampleDbs/v1".toPath())
        setUpDatabase(getPlatformContext(), v1)
    }

    @Test
    fun `read v1 db`() = runTest {
        val driver = createChannelsDbDriver(getPlatformContext(), fileName = "channels-testnet-fe646b99.sqlite")
        val channelsDb = createSqliteChannelsDb(driver, testLoggerFactory)

        val channels = channelsDb.listLocalChannels()

        // note: this test only checks that the app is able to read v1 of the channels database is readable.
        // it does not test the channel data blob serialization ; that is already done in lightning-kmp.

        assertEquals(1, channels.size)
        assertEquals(ByteVector32.fromValidHex("b96f5cad6ecde09ca60c2aa4ad280cb9bb5e09f357306a65b349471fcc55a983"), channels[0].channelId)

        driver.close()
    }
}

expect fun testChannelsDriver(): SqlDriver