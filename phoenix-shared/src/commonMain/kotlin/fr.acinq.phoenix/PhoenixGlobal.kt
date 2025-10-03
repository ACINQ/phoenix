/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.phoenix

import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.info
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.createAppDbDriver
import fr.acinq.phoenix.managers.AppConnectionsDaemon
import fr.acinq.phoenix.managers.global.CurrencyManager
import fr.acinq.phoenix.managers.global.FeerateManager
import fr.acinq.phoenix.managers.global.NetworkMonitor
import fr.acinq.phoenix.managers.global.WalletContextManager
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.logger.PhoenixLoggerConfig


class PhoenixGlobal(val ctx: PlatformContext) {

    val loggerFactory = LoggerFactory(PhoenixLoggerConfig(ctx))
    private val logger = loggerFactory.newLogger(this::class)

    val appDb by lazy { SqliteAppDb(createAppDbDriver(ctx)) }
    val networkMonitor by lazy { NetworkMonitor(loggerFactory, ctx) }
    val currencyManager by lazy { CurrencyManager(loggerFactory, appDb) }
    val feerateManager by lazy { FeerateManager(loggerFactory) }
    val walletContextManager by lazy { WalletContextManager(loggerFactory) }

    init {
        logger.info { "init PhoenixGlobal..." }
    }

    /** Called by [AppConnectionsDaemon] when internet is available. */
    internal fun enableNetworkAccess() {
        feerateManager.startMonitoringFeerate()
        walletContextManager.stopJobs()
        currencyManager.enableNetworkAccess()
    }

    /** Called by [AppConnectionsDaemon] when no connection is available. */
    internal fun disableNetworkAccess() {
        feerateManager.stopMonitoringFeerate()
        walletContextManager.stopJobs()
        currencyManager.disableNetworkAccess()
    }

}