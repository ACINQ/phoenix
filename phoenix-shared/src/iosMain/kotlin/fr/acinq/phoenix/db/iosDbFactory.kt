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
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import fr.acinq.bitcoin.Chain
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.phoenix.db.migrations.v10.AfterVersion10
import fr.acinq.phoenix.db.migrations.v11.AfterVersion11
import fr.acinq.phoenix.db.sqldelight.AppDatabase
import fr.acinq.phoenix.db.sqldelight.ChannelsDatabase
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getDatabaseFilesDirectoryPath
import fr.acinq.phoenix.utils.extensions.phoenixName

actual fun createChannelsDbDriver(
    ctx: PlatformContext,
    chain: Chain,
    nodeIdHash: String
): SqlDriver {
    val schema = ChannelsDatabase.Schema
    val name = "channels-${chain.phoenixName}-$nodeIdHash.sqlite"

    // The foreign_keys constraint needs to be set via the DatabaseConfiguration:
    // https://github.com/cashapp/sqldelight/issues/1356

    val dbDir = getDatabaseFilesDirectoryPath(ctx)
    val configuration = DatabaseConfiguration(
        name = name,
        version = schema.version.toInt(),
        extendedConfig = DatabaseConfiguration.Extended(
            basePath = dbDir,
            foreignKeyConstraints = true
        ),
        create = { connection ->
            wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
        }
    )
    return NativeSqliteDriver(configuration)
}

actual fun createPaymentsDbDriver(
    ctx: PlatformContext,
    chain: Chain,
    nodeIdHash: String,
    onError: (String) -> Unit,
): SqlDriver {
    val schema = PaymentsDatabase.Schema
    val name = "payments-${chain.phoenixName}-$nodeIdHash.sqlite"

    val dbDir = getDatabaseFilesDirectoryPath(ctx)
    val configuration = DatabaseConfiguration(
        name = name,
        version = schema.version.toInt(),
        extendedConfig = DatabaseConfiguration.Extended(
            basePath = dbDir,
            foreignKeyConstraints = true
        ),
        create = { connection ->
            wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong(), AfterVersion10(onError), AfterVersion11(onError)) }
        }
    )
    return NativeSqliteDriver(configuration)
}

actual fun createAppDbDriver(
    ctx: PlatformContext
): SqlDriver {
    val schema = AppDatabase.Schema
    val name = "app.sqlite"

    val dbDir = getDatabaseFilesDirectoryPath(ctx)
    val configuration = DatabaseConfiguration(
        name = name,
        version = schema.version.toInt(),
        extendedConfig = DatabaseConfiguration.Extended(
            basePath = dbDir,
            foreignKeyConstraints = true
        ),
        create = { connection ->
            wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
        }
    )
    return NativeSqliteDriver(configuration)
}