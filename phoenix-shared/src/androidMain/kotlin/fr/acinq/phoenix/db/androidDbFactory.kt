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

import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.Bitcoin.Chain
import fr.acinq.phoenix.utils.PlatformContext
import java.util.*

actual fun createChannelsDbDriver(ctx: PlatformContext, chain: Chain, nodeIdHash: String): SqlDriver {
    return AndroidSqliteDriver(ChannelsDatabase.Schema, ctx.applicationContext, "channels-${chain.name.lowercase()}-$nodeIdHash.sqlite")
}

actual fun createPaymentsDbDriver(ctx: PlatformContext, chain: Chain, nodeIdHash: String): SqlDriver {
    return AndroidSqliteDriver(PaymentsDatabase.Schema, ctx.applicationContext, "payments-${chain.name.lowercase()}-$nodeIdHash.sqlite")
}

actual fun createAppDbDriver(ctx: PlatformContext): SqlDriver {
    return AndroidSqliteDriver(AppDatabase.Schema, ctx.applicationContext, "appdb.sqlite")
}