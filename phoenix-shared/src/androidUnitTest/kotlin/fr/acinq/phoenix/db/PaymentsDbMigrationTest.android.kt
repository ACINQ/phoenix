/*
 * Copyright 2024 ACINQ SAS
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
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import fr.acinq.phoenix.db.migrations.v10.AfterVersion10
import fr.acinq.phoenix.db.migrations.v11.AfterVersion11
import kotlinx.datetime.Clock
import java.io.File
import java.io.FileOutputStream
import java.util.Properties


actual fun testPaymentsDriverFromFile(): SqlDriver {

    // loading original database file
    val loader = PaymentsDbMigrationTest::class.java.classLoader!!
    val originalDb = loader.getResourceAsStream("sampledbs/v10/payments-testnet-28903aff.sqlite")!!

    // make a copy in a temporary folder that we can safely edit later when testing the migration
    // TODO: fix temporary file creation
    val testFile = File.createTempFile("phoenix_testdb_${Clock.System.now().toEpochMilliseconds()}", ".sqlite", File("phoenix_tests").apply { mkdir() })
    testFile.deleteOnExit()
    val fos = FileOutputStream(testFile)
    fos.write(originalDb.readBytes())

    val driver: SqlDriver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${testFile.path}",
        properties = Properties(),
        schema = PaymentsDatabase.Schema,
        migrateEmptySchema = false,
        AfterVersion10,
        AfterVersion11,
    )

    return driver
}