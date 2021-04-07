package fr.acinq.phoenix

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.setLightningLoggerFactory
import fr.acinq.phoenix.app.*
import fr.acinq.phoenix.app.ctrl.*
import fr.acinq.phoenix.app.ctrl.config.*
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.*
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.createAppDbDriver
import fr.acinq.phoenix.utils.*
import io.ktor.client.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
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
class PhoenixBusiness(private val ctx: PlatformContext) {

    private val logMemory = LogMemory(Path(getApplicationFilesDirectoryPath(ctx)).resolve("logs"))

    val loggerFactory = LoggerFactory(
        defaultLogFrontend.withShortPackageKeepLast(1),
        logMemory.withShortPackageKeepLast(1)
    )

    private val logger = loggerFactory.newLogger(this::class)

    private val tcpSocketBuilder = TcpSocket.Builder()

    private val networkMonitor by lazy { NetworkMonitor(loggerFactory, ctx) }
    private val httpClient by lazy {
        HttpClient {
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    val chain = Chain.Testnet

    private val electrumClient by lazy { ElectrumClient(tcpSocketBuilder, MainScope()) }
    private val electrumWatcher by lazy { ElectrumWatcher(electrumClient, MainScope()) }

    private var appConnectionsDaemon: AppConnectionsDaemon? = null

    private val walletManager by lazy { WalletManager() }
    private val nodeParamsManager by lazy { NodeParamsManager(loggerFactory, ctx, chain, walletManager) }
    private val peerManager by lazy { PeerManager(loggerFactory, nodeParamsManager, appConfigurationManager, tcpSocketBuilder, electrumWatcher) }
    val paymentsManager by lazy { PaymentsManager(loggerFactory, peerManager, nodeParamsManager) }

    private val appDb by lazy { SqliteAppDb(createAppDbDriver(ctx)) }
    val appConfigurationManager by lazy { AppConfigurationManager(appDb, httpClient, electrumClient, chain, loggerFactory) }
    val currencyManager by lazy { CurrencyManager(loggerFactory, appDb, httpClient) }

    val connectionsMonitor by lazy { ConnectionsMonitor(peerManager, electrumClient, networkMonitor) }
    val util by lazy { Utilities(loggerFactory, chain) }

    init {
        setLightningLoggerFactory(loggerFactory)
    }

    fun start() {
        if (appConnectionsDaemon == null) {
            logger.debug { "start business" }
            appConnectionsDaemon = AppConnectionsDaemon(
                appConfigurationManager,
                walletManager,
                peerManager,
                currencyManager,
                networkMonitor,
                electrumClient,
                loggerFactory,
            )
        }
    }

    // Converts a mnemonics list to a seed.
    // This is generally called with a mnemonics list that has been previously saved.
    fun prepWallet(mnemonics: List<String>, passphrase: String = ""): ByteArray {
        MnemonicCode.validate(mnemonics)
        return MnemonicCode.toSeed(mnemonics, passphrase)
    }

    fun loadWallet(seed: ByteArray): Unit {
        if (walletManager.wallet.value == null) {
            walletManager.loadWallet(seed)
        }
    }

    fun incrementDisconnectCount(): Unit {
        appConnectionsDaemon?.incrementDisconnectCount()
    }

    fun decrementDisconnectCount(): Unit {
        appConnectionsDaemon?.decrementDisconnectCount()
    }

    fun getXpub(): Pair<String, String>? = walletManager.wallet.value?.xpub(chain.isMainnet())

    fun peerState() = peerManager.peerState

    // The (node_id, fcm_token) tuple only needs to be registered once.
    // And after that, only if the tuple changes (e.g. different fcm_token).
    suspend fun registerFcmToken(token: String?) {
        logger.info { "registering token=$token" }
        peerManager.getPeer().registerFcmToken(token)
    }

    fun updateTorUsage(isEnabled: Boolean) = appConfigurationManager.updateTorUsage(isEnabled)

    val controllers: ControllerFactory = object : ControllerFactory {
        override fun content(): ContentController =
            AppContentController(loggerFactory, walletManager)

        override fun initialization(): InitializationController =
            AppInitController(loggerFactory, walletManager)

        override fun home(): HomeController =
            AppHomeController(loggerFactory, peerManager, paymentsManager)

        override fun receive(): ReceiveController =
            AppReceiveController(loggerFactory, chain, peerManager)

        override fun scan(firstModel: Scan.Model): ScanController =
            AppScanController(loggerFactory, firstModel, peerManager)

        override fun restoreWallet(): RestoreWalletController =
            AppRestoreWalletController(loggerFactory)

        override fun configuration(): ConfigurationController =
            AppConfigurationController(loggerFactory, walletManager)

        override fun electrumConfiguration(): ElectrumConfigurationController =
            AppElectrumConfigurationController(loggerFactory, appConfigurationManager, electrumClient)

        override fun channelsConfiguration(): ChannelsConfigurationController =
            AppChannelsConfigurationController(loggerFactory, peerManager, chain)

        override fun logsConfiguration(): LogsConfigurationController =
            AppLogsConfigurationController(ctx, loggerFactory, logMemory)

        override fun closeChannelsConfiguration(): CloseChannelsConfigurationController =
            AppCloseChannelsConfigurationController(loggerFactory, peerManager, walletManager, chain, util, false)

        override fun forceCloseChannelsConfiguration(): CloseChannelsConfigurationController =
            AppCloseChannelsConfigurationController(loggerFactory, peerManager, walletManager, chain, util, true)
    }
}
