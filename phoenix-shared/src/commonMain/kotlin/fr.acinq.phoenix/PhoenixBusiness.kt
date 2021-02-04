package fr.acinq.phoenix

import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.ElectrumWatcher
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.setEclairLoggerFactory
import fr.acinq.phoenix.app.*
import fr.acinq.phoenix.app.ctrl.*
import fr.acinq.phoenix.app.ctrl.config.*
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.*
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.db.*
import fr.acinq.phoenix.utils.*
import io.ktor.client.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.serialization.json.Json
import org.kodein.db.DB
import org.kodein.db.impl.factory
import org.kodein.db.inDir
import org.kodein.db.orm.kotlinx.KotlinxSerializer
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

    // TODO to be remove
    private val noSqlDbFactory by lazy { DB.factory.inDir(getApplicationFilesDirectoryPath(ctx)) }
    private val noSqlAppDB by lazy { noSqlDbFactory.open("application", KotlinxSerializer()) }
    private val appDb by lazy { SqliteAppDb(createAppDbDriver(ctx)) }
    private val paymentsDb by lazy { SqlitePaymentsDb(createPaymentsDbDriver(ctx)) }

    // TestNet
    private val chain = Chain.TESTNET

    private val masterPubkeyPath = if (chain == Chain.MAINNET) "m/84'/0'/0'" else "m/84'/1'/0'"

    private val electrumClient by lazy { ElectrumClient(tcpSocketBuilder, MainScope()) }
    private val electrumWatcher by lazy { ElectrumWatcher(electrumClient, MainScope()) }

    private var appConnectionsDaemon: AppConnectionsDaemon? = null

    private val walletManager by lazy { WalletManager() }
    private val peerManager by lazy { PeerManager(loggerFactory, walletManager, appConfigurationManager, paymentsDb, tcpSocketBuilder, electrumWatcher, chain, ctx) }
    private val paymentsManager by lazy { PaymentsManager(loggerFactory, paymentsDb, peerManager) }
    private val appConfigurationManager by lazy { AppConfigurationManager(noSqlAppDB, appDb, httpClient, electrumClient, chain, loggerFactory) }

    val currencyManager by lazy { CurrencyManager(loggerFactory, noSqlAppDB, httpClient) }
    val connectionsMonitor by lazy { ConnectionsMonitor(peerManager, electrumClient, networkMonitor) }
    val util by lazy { Utilities(loggerFactory, chain) }

    init {
        setEclairLoggerFactory(loggerFactory)
    }

    fun start() {
        if (appConnectionsDaemon == null) {
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

    fun nodeID(): String {
        return peerManager.peerState.value?.nodeParams?.nodeId?.toString() ?: error("Peer must be initialized!")
    }

    // The (node_id, fcm_token) tuple only needs to be registered once.
    // And after that, only if the tuple changes (e.g. different fcm_token).
    suspend fun registerFcmToken(token: String?) {
        logger.info { "registering token=$token" }
        peerManager.getPeer().registerFcmToken(token)
    }

    fun incomingPaymentFlow() =
        paymentsManager.subscribeToLastIncomingPayment()

    fun updateTorUsage(isEnabled: Boolean) = appConfigurationManager.updateTorUsage(isEnabled)

    val controllers: ControllerFactory = object : ControllerFactory {
        override fun content(): ContentController = AppContentController(loggerFactory, walletManager)
        override fun initialization(): InitializationController = AppInitController(loggerFactory, walletManager)
        override fun home(): HomeController = AppHomeController(loggerFactory, peerManager, paymentsManager)
        override fun receive(): ReceiveController = AppReceiveController(loggerFactory, peerManager)
        override fun scan(): ScanController = AppScanController(loggerFactory, peerManager)
        override fun restoreWallet(): RestoreWalletController = AppRestoreWalletController(loggerFactory)
        override fun configuration(): ConfigurationController = AppConfigurationController(loggerFactory, walletManager)
        override fun electrumConfiguration(): ElectrumConfigurationController = AppElectrumConfigurationController(loggerFactory, appConfigurationManager, chain, masterPubkeyPath, walletManager, electrumClient)
        override fun channelsConfiguration(): ChannelsConfigurationController = AppChannelsConfigurationController(loggerFactory, peerManager, chain)
        override fun logsConfiguration(): LogsConfigurationController = AppLogsConfigurationController(ctx, loggerFactory, logMemory)
        override fun closeChannelsConfiguration(): CloseChannelsConfigurationController = AppCloseChannelsConfigurationController(loggerFactory, peerManager, util)
    }
}
