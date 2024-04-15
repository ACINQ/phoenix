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

import co.touchlab.sqliter.DatabaseConfiguration
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.drivers.native.NativeSqliteDriver
import app.cash.sqldelight.drivers.native.wrapConnection
import fr.acinq.lightning.Lightning
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getDatabaseFilesDirectoryPath

actual fun testChannelsDriver(): SqlDriver {
    val schema = ChannelsDatabase.Schema

    // In-memory databases don't seem to work on native/iOS.
    // The call succeeds, but in reality it creates a persistent database,
    // which then breaks our unit test logic.
    // The docs reference other ways of making in-memory databases:
    // https://sqlite.org/inmemorydb.html
    // But none of them seem to work (at the time of writing).
    // So the current workaround is to create a fresh database for each test.
//  val name = ":memory:"
    val name = Lightning.randomBytes32().toHex()

    val dbDir = getDatabaseFilesDirectoryPath(PlatformContext())
    val configuration = DatabaseConfiguration(
        name = name,
        version = schema.version,
        extendedConfig = DatabaseConfiguration.Extended(
            basePath = dbDir,
            foreignKeyConstraints = true // <= official solution doesn't work :(
        ),
        create = { connection ->
            wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion, newVersion) }
        }
    )
    return NativeSqliteDriver(configuration)
}

actual fun testPaymentsDriver(): SqlDriver {
    val schema = PaymentsDatabase.Schema

    // In-memory databases don't seem to work on native/iOS.
    // See explanation above.
//  val name = ":memory:"
    val name = Lightning.randomBytes32().toHex()

    val dbDir = getDatabaseFilesDirectoryPath(PlatformContext())
    val configuration = DatabaseConfiguration(
        name = name,
        version = schema.version,
        extendedConfig = DatabaseConfiguration.Extended(
            basePath = dbDir,
            foreignKeyConstraints = true // <= official solution doesn't work :(
        ),
        create = { connection ->
            wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion, newVersion) }
        }
    )
    return NativeSqliteDriver(configuration)
}

// Workaround for known bugs in SQLDelight on native/iOS.
actual fun isIOS(): Boolean {
    return true
}
