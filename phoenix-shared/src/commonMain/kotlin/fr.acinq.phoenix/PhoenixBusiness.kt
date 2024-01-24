/*
 * Copyright 2022 ACINQ SAS
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

import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.NoTagFormatter
import co.touchlab.kermit.platformLogWriter
import co.touchlab.kermit.Severity
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.phoenix.controllers.*
import fr.acinq.phoenix.controllers.config.*
import fr.acinq.phoenix.controllers.init.AppInitController
import fr.acinq.phoenix.controllers.init.AppRestoreWalletController
import fr.acinq.phoenix.controllers.main.AppContentController
import fr.acinq.phoenix.controllers.main.AppHomeController
import fr.acinq.phoenix.controllers.payments.AppReceiveController
import fr.acinq.phoenix.controllers.payments.AppScanController
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.data.StartupParams
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.createAppDbDriver
import fr.acinq.phoenix.managers.*
import fr.acinq.phoenix.utils.*
import fr.acinq.phoenix.utils.loggerExtensions.*
import fr.acinq.tor.Tor
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.defaultLogFrontend
import org.kodein.log.withShortPackageKeepLast
import kotlin.time.Duration.Companion.seconds

object globalLoggerFactory : Logger(
    config = loggerConfigInit(platformLogWriter(NoTagFormatter),
        minSeverity = Severity.Info
    ),
    tag = "PhoenixShared"
)

class PhoenixBusiness(
    internal val ctx: PlatformContext
) {

    val oldLoggerFactory = LoggerFactory(
        defaultLogFrontend.withShortPackageKeepLast(1)
    )

    val newLoggerFactory = globalLoggerFactory

    private val logger = newLoggerFactory.appendingTag("PhoenixBusiness")

    private val tcpSocketBuilder = TcpSocket.Builder()
    internal val tcpSocketBuilderFactory = suspend {
        val isTorEnabled = appConfigurationManager.isTorEnabled.filterNotNull().first()
        if (isTorEnabled) {
            tcpSocketBuilder.torProxy(newLoggerFactory)
        } else {
            tcpSocketBuilder
        }
    }

    internal val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(json = Json { ignoreUnknownKeys = true })
            }
        }
    }

    val chain: NodeParams.Chain = NodeParamsManager.chain

    val electrumClient by lazy { ElectrumClient(scope = MainScope(), loggerFactory = oldLoggerFactory, pingInterval = 30.seconds, rpcTimeout = 10.seconds) }
    internal val electrumWatcher by lazy { ElectrumWatcher(electrumClient, MainScope(), oldLoggerFactory) }

    var appConnectionsDaemon: AppConnectionsDaemon? = null

    val appDb by lazy { SqliteAppDb(createAppDbDriver(ctx)) }
    val networkMonitor by lazy { NetworkMonitor(newLoggerFactory, ctx) }
    val walletManager by lazy { WalletManager(chain) }
    val nodeParamsManager by lazy { NodeParamsManager(this) }
    val databaseManager by lazy { DatabaseManager(this) }
    val peerManager by lazy { PeerManager(this) }
    val paymentsManager by lazy { PaymentsManager(this) }
    val balanceManager by lazy { BalanceManager(this) }
    val appConfigurationManager by lazy { AppConfigurationManager(this) }
    val currencyManager by lazy { CurrencyManager(this) }
    val connectionsManager by lazy { ConnectionsManager(this) }
    val lnurlManager by lazy { LnurlManager(this) }
    val notificationsManager by lazy { NotificationsManager(this) }
    val blockchainExplorer by lazy { BlockchainExplorer(chain) }
    val tor by lazy { Tor(getApplicationCacheDirectoryPath(ctx), TorHelper.torLogger(newLoggerFactory)) }

    fun start(startupParams: StartupParams) {
        logger.debug { "starting with params=$startupParams" }
        if (appConnectionsDaemon == null) {
            logger.debug { "start business" }
            appConfigurationManager.setStartupParams(startupParams)
            appConnectionsDaemon = AppConnectionsDaemon(this)
        }
    }

    /**
     * Cancels the CoroutineScope of all managers, and closes all database connections.
     * It's recommended that you close the network connections (electrum + peer)
     * BEFORE invoking this function, to ensure a clean disconnect from the server.
     */
    fun stop() {
        electrumClient.stop()
        electrumWatcher.stop()
        electrumWatcher.cancel()
        appConnectionsDaemon?.cancel()
        appDb.close()
        networkMonitor.stop()
        walletManager.cancel()
        nodeParamsManager.cancel()
        databaseManager.close()
        databaseManager.cancel()
        databaseManager.cancel()
        peerManager.cancel()
        paymentsManager.cancel()
        appConfigurationManager.cancel()
        currencyManager.cancel()
        lnurlManager.cancel()
        notificationsManager.cancel()
    }

    // The (node_id, fcm_token) tuple only needs to be registered once.
    // And after that, only if the tuple changes (e.g. different fcm_token).
    suspend fun registerFcmToken(token: String?) {
        logger.debug { "registering token=$token" }
        peerManager.getPeer().registerFcmToken(token)
    }

    private val _this = this
    val controllers: ControllerFactory = object : ControllerFactory {
        override fun content(): ContentController =
            AppContentController(_this)

        override fun initialization(): InitializationController =
            AppInitController(_this)

        override fun home(): HomeController =
            AppHomeController(_this)

        override fun receive(): ReceiveController =
            AppReceiveController(_this)

        override fun scan(firstModel: Scan.Model): ScanController =
            AppScanController(_this, firstModel)

        override fun restoreWallet(): RestoreWalletController =
            AppRestoreWalletController(_this)

        override fun configuration(): ConfigurationController =
            AppConfigurationController(_this)

        override fun electrumConfiguration(): ElectrumConfigurationController =
            AppElectrumConfigurationController(_this)

        override fun closeChannelsConfiguration(): CloseChannelsConfigurationController =
            AppCloseChannelsConfigurationController(_this, isForceClose = false)

        override fun forceCloseChannelsConfiguration(): CloseChannelsConfigurationController =
            AppCloseChannelsConfigurationController(_this, isForceClose = true)
    }
}
