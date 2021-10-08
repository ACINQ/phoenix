package fr.acinq.phoenix

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.setLightningLoggerFactory
import fr.acinq.phoenix.controllers.*
import fr.acinq.phoenix.managers.*
import fr.acinq.phoenix.controllers.config.*
import fr.acinq.phoenix.controllers.init.AppInitController
import fr.acinq.phoenix.controllers.init.AppRestoreWalletController
import fr.acinq.phoenix.controllers.main.AppContentController
import fr.acinq.phoenix.controllers.main.AppHomeController
import fr.acinq.phoenix.controllers.payments.AppReceiveController
import fr.acinq.phoenix.controllers.payments.AppScanController
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.createAppDbDriver
import fr.acinq.phoenix.controllers.payments.Scan
import fr.acinq.phoenix.utils.*
import io.ktor.client.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.serialization.json.Json
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.defaultLogFrontend
import org.kodein.log.newLogger
import org.kodein.log.withShortPackageKeepLast
import org.kodein.memory.file.Path
import org.kodein.memory.file.resolve


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUnsignedTypes::class)
class PhoenixBusiness(
    internal val ctx: PlatformContext
) {
    internal val logMemory = LogMemory(Path(getApplicationFilesDirectoryPath(ctx)).resolve("logs"))

    val loggerFactory = LoggerFactory(
        defaultLogFrontend.withShortPackageKeepLast(1),
        logMemory.withShortPackageKeepLast(1)
    )

    private val logger = loggerFactory.newLogger(this::class)

    internal val tcpSocketBuilder = TcpSocket.Builder()

    internal val networkMonitor by lazy { NetworkManager(loggerFactory, ctx) }
    internal val httpClient by lazy {
        HttpClient {
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    val chain = Chain.Testnet

    internal val electrumClient by lazy { ElectrumClient(tcpSocketBuilder, MainScope()) }
    internal val electrumWatcher by lazy { ElectrumWatcher(electrumClient, MainScope()) }

    var appConnectionsDaemon: AppConnectionsDaemon? = null

    internal val walletManager by lazy { WalletManager(chain) }
    internal val appDb by lazy { SqliteAppDb(createAppDbDriver(ctx)) }

    val nodeParamsManager by lazy { NodeParamsManager(this) }
    val databaseManager by lazy { DatabaseManager(this) }
    val peerManager by lazy { PeerManager(this) }
    val paymentsManager by lazy { PaymentsManager(this) }
    val appConfigurationManager by lazy { AppConfigurationManager(this) }
    val currencyManager by lazy { CurrencyManager(this) }
    val connectionsManager by lazy { ConnectionsManager(this) }
    val lnUrlManager by lazy { LNUrlManager(this) }
    val util by lazy { Utilities(this) }
    val blockchainExplorer by lazy { BlockchainExplorer(chain) }

    init {
        setLightningLoggerFactory(loggerFactory)
    }

    fun start() {
        if (appConnectionsDaemon == null) {
            logger.debug { "start business" }
            appConnectionsDaemon = AppConnectionsDaemon(this)
        }
    }

    // Converts a mnemonics list to a seed.
    // This is generally called with a mnemonics list that has been previously saved.
    fun prepWallet(mnemonics: List<String>, passphrase: String = ""): ByteArray {
        MnemonicCode.validate(mnemonics)
        return MnemonicCode.toSeed(mnemonics, passphrase)
    }

    fun loadWallet(seed: ByteArray): Pair<ByteVector32, String>? {
        if (walletManager.wallet.value == null) {
            walletManager.loadWallet(seed)
            return walletManager.wallet.value?.cloudKeyAndEncryptedNodeId()
        }
        return null
    }

    fun getXpub(): Pair<String, String>? = walletManager.wallet.value?.xpub()

    fun peerState() = peerManager.peerState

    // The (node_id, fcm_token) tuple only needs to be registered once.
    // And after that, only if the tuple changes (e.g. different fcm_token).
    suspend fun registerFcmToken(token: String?) {
        logger.info { "registering token=$token" }
        peerManager.getPeer().registerFcmToken(token)
    }

    fun updateTorUsage(isEnabled: Boolean) = appConfigurationManager.updateTorUsage(isEnabled)

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

        override fun channelsConfiguration(): ChannelsConfigurationController =
            AppChannelsConfigurationController(_this)

        override fun logsConfiguration(): LogsConfigurationController =
            AppLogsConfigurationController(_this)

        override fun closeChannelsConfiguration(): CloseChannelsConfigurationController =
            AppCloseChannelsConfigurationController(_this, isForceClose = false)

        override fun forceCloseChannelsConfiguration(): CloseChannelsConfigurationController =
            AppCloseChannelsConfigurationController(_this, isForceClose = true)
    }
}
