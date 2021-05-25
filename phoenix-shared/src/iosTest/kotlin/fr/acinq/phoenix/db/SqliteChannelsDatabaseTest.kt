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

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver
import fr.acinq.lightning.Lightning

actual fun testDriver(): SqlDriver {
    return NativeSqliteDriver(ChannelsDatabase.Schema, ":memory:")
}

actual fun testPaymentsDriver(): SqlDriver {
    // In-memory databases don't seem to work on native/iOS.
    // This creates a persistent database, which breaks our unit test logic.
//  return NativeSqliteDriver(PaymentsDatabase.Schema, ":memory:")
    // The docs reference other ways of making in-memory databases:
    // https://sqlite.org/inmemorydb.html
    // But none of them seem to work (at the time of writing).
    //
    // Current workaround is to create a fresh database for each test.
    val randomName = Lightning.randomBytes32().toHex()
    return NativeSqliteDriver(PaymentsDatabase.Schema, randomName)
}