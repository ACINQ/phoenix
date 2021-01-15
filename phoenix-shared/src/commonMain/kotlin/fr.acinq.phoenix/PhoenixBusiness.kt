package fr.acinq.phoenix

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.ElectrumWatcher
import fr.acinq.eclair.blockchain.fee.FeerateTolerance
import fr.acinq.eclair.blockchain.fee.OnChainFeeConf
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.db.ChannelsDb
import fr.acinq.eclair.db.Databases
import fr.acinq.eclair.db.PaymentsDb
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.sat
import fr.acinq.eclair.utils.setEclairLoggerFactory
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.phoenix.app.*
import fr.acinq.phoenix.app.ctrl.*
import fr.acinq.phoenix.app.ctrl.config.*
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.*
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.db.SqliteChannelsDb
import fr.acinq.phoenix.db.SqlitePaymentsDb
import fr.acinq.phoenix.db.createChannelsDbDriver
import fr.acinq.phoenix.db.createPaymentsDbDriver
import fr.acinq.phoenix.utils.*
import io.ktor.client.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.consumeAsFlow
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

    private fun buildPeer(): Peer {
        val wallet = walletManager.wallet ?: error("Wallet must be initialized.")

        val genesisBlock = when (chain) {
            Chain.MAINNET -> Block.LivenetGenesisBlock
            Chain.TESTNET -> Block.TestnetGenesisBlock
            Chain.REGTEST -> Block.RegtestGenesisBlock
        }

        val keyManager = LocalKeyManager(wallet.seed.toByteVector32(), genesisBlock.hash)
        newLogger(loggerFactory).info { "nodeid=${keyManager.nodeId}" }

        val nodeParams = NodeParams(
            keyManager = keyManager,
            alias = "phoenix",
            features = Features(
                setOf(
                    ActivatedFeature(Feature.OptionDataLossProtect, FeatureSupport.Mandatory),
                    ActivatedFeature(Feature.VariableLengthOnion, FeatureSupport.Optional),
                    ActivatedFeature(Feature.PaymentSecret, FeatureSupport.Optional),
                    ActivatedFeature(Feature.BasicMultiPartPayment, FeatureSupport.Optional),
                    ActivatedFeature(Feature.Wumbo, FeatureSupport.Optional),
                    ActivatedFeature(Feature.StaticRemoteKey, FeatureSupport.Optional),
                    ActivatedFeature(Feature.TrampolinePayment, FeatureSupport.Optional),
                    ActivatedFeature(Feature.AnchorOutputs, FeatureSupport.Optional),
                )
            ),
            dustLimit = 546.sat,
            onChainFeeConf = OnChainFeeConf(
                closeOnOfflineMismatch = true,
                updateFeeMinDiffRatio = 0.1,
                feerateTolerance = FeerateTolerance(ratioLow = 0.01, ratioHigh = 100.0)
            ),
            maxHtlcValueInFlightMsat = 150000000L,
            maxAcceptedHtlcs = 30,
            expiryDeltaBlocks = CltvExpiryDelta(144),
            fulfillSafetyBeforeTimeoutBlocks = CltvExpiryDelta(6),
            checkHtlcTimeoutAfterStartupDelaySeconds = 15,
            htlcMinimum = 1000.msat,
            minDepthBlocks = 3,
            toRemoteDelayBlocks = CltvExpiryDelta(144),
            maxToLocalDelayBlocks = CltvExpiryDelta(1000),
            feeBase = 1000.msat,
            feeProportionalMillionth = 10,
            reserveToFundingRatio = 0.01, // note: not used (overridden below)
            maxReserveToFundingRatio = 0.05,
            revocationTimeoutSeconds = 20,
            authTimeoutSeconds = 10,
            initTimeoutSeconds = 10,
            pingIntervalSeconds = 30,
            pingTimeoutSeconds = 10,
            pingDisconnect = true,
            autoReconnect = false,
            initialRandomReconnectDelaySeconds = 5,
            maxReconnectIntervalSeconds = 3600,
            chainHash = genesisBlock.hash,
            channelFlags = 1,
            paymentRequestExpirySeconds = 3600,
            multiPartPaymentExpirySeconds = 60,
            minFundingSatoshis = 1000.sat,
            maxFundingSatoshis = 16777215.sat,
            maxPaymentAttempts = 5,
            enableTrampolinePayment = true
        )

        val trampolineFees = listOf(
            TrampolineFees(0.sat, 0, CltvExpiryDelta(576)),
            TrampolineFees(1.sat, 100, CltvExpiryDelta(576)),
            TrampolineFees(3.sat, 100, CltvExpiryDelta(576)),
            TrampolineFees(5.sat, 500, CltvExpiryDelta(576)),
            TrampolineFees(5.sat, 1000, CltvExpiryDelta(576)),
            TrampolineFees(5.sat, 1200, CltvExpiryDelta(576))
        )

        val walletParams = WalletParams(acinqNodeUri, trampolineFees)

        newLogger(loggerFactory).info { "nodeParams=$nodeParams" }
        newLogger(loggerFactory).info { "walletParams=$walletParams" }

        val databases = object : Databases {
            override val channels: ChannelsDb get() = channelsDb
            override val payments: PaymentsDb get() = paymentsDb
        }

        return Peer(tcpSocketBuilder, nodeParams, walletParams, electrumWatcher, databases, MainScope())
    }

    private val logMemory = LogMemory(Path(getApplicationFilesDirectoryPath(ctx)).resolve("logs"))

    val loggerFactory = LoggerFactory(
        defaultLogFrontend.withShortPackageKeepLast(1),
        logMemory.withShortPackageKeepLast(1)
    )

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
    private val dbFactory by lazy { DB.factory.inDir(getApplicationFilesDirectoryPath(ctx)) }
    private val appDB by lazy { dbFactory.open("application", KotlinxSerializer()) }
    private val channelsDb by lazy { SqliteChannelsDb(createChannelsDbDriver(ctx)) }
    private val paymentsDb by lazy { SqlitePaymentsDb(createPaymentsDbDriver(ctx)) }

    // TestNet
    private val acinqNodeUri = NodeUri(PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"), "13.248.222.197", 9735)
    private val chain = Chain.TESTNET

    private val masterPubkeyPath = if (chain == Chain.MAINNET) "m/84'/0'/0'" else "m/84'/1'/0'"

    private val electrumClient by lazy { ElectrumClient(tcpSocketBuilder, MainScope()) }
    private val electrumWatcher by lazy { ElectrumWatcher(electrumClient, MainScope()) }

    private val peer by lazy { buildPeer() }

    private var appConnectionsDaemon: AppConnectionsDaemon? = null

    private val walletManager by lazy { WalletManager() }
    private val appHistoryManager by lazy { AppHistoryManager(loggerFactory, appDB, peer) }
    private val appConfigurationManager by lazy { AppConfigurationManager(appDB, electrumClient, chain, loggerFactory) }

    val currencyManager by lazy { CurrencyManager(loggerFactory, appDB, httpClient) }
    val connectionsMonitor by lazy { ConnectionsMonitor(peer, electrumClient, networkMonitor) }
    val util by lazy { Utilities(loggerFactory, chain) }

    init {
        setEclairLoggerFactory(loggerFactory)
    }

    fun start() {
        if (appConnectionsDaemon == null) {
            appConnectionsDaemon = AppConnectionsDaemon(
                appConfigurationManager,
                walletManager,
                currencyManager,
                networkMonitor,
                electrumClient,
                loggerFactory,
                getPeer = { peer } // lazy getter
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
        if (walletManager.wallet == null) {
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
        return peer.nodeParams.nodeId.toString()
    }

    // The (node_id, fcm_token) tuple only needs to be registered once.
    // And after that, only if the tuple changes (e.g. different fcm_token).
    fun registerFcmToken(token: String?) {
        peer.registerFcmToken(token)
    }

    fun incomingTransactionFlow() =
        appHistoryManager.openIncomingTransactionSubscription().consumeAsFlow()

    val controllers: ControllerFactory = object : ControllerFactory {
        override fun content(): ContentController = AppContentController(loggerFactory, walletManager)
        override fun initialization(): InitializationController = AppInitController(loggerFactory, walletManager)
        override fun home(): HomeController = AppHomeController(loggerFactory, peer, appHistoryManager)
        override fun receive(): ReceiveController = AppReceiveController(loggerFactory, peer)
        override fun scan(): ScanController = AppScanController(loggerFactory, peer)
        override fun restoreWallet(): RestoreWalletController = AppRestoreWalletController(loggerFactory, walletManager)
        override fun configuration(): ConfigurationController = AppConfigurationController(loggerFactory, walletManager)
        override fun electrumConfiguration(): ElectrumConfigurationController = AppElectrumConfigurationController(loggerFactory, appConfigurationManager, chain, masterPubkeyPath, walletManager, electrumClient)
        override fun channelsConfiguration(): ChannelsConfigurationController = AppChannelsConfigurationController(loggerFactory, peer, chain)
        override fun logsConfiguration(): LogsConfigurationController = AppLogsConfigurationController(ctx, loggerFactory, logMemory)
        override fun closeChannelsConfiguration(): CloseChannelsConfigurationController = AppCloseChannelsConfigurationController(loggerFactory, peer, util)
    }
}
