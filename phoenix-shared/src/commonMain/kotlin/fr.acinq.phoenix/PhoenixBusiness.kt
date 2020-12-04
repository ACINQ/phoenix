package fr.acinq.phoenix

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.ElectrumWatcher
import fr.acinq.eclair.blockchain.fee.OnChainFeeConf
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.sat
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.phoenix.app.*
import fr.acinq.phoenix.app.ctrl.*
import fr.acinq.phoenix.app.ctrl.config.*
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.*
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.utils.NetworkMonitor
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationFilesDirectoryPath
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
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUnsignedTypes::class)
class PhoenixBusiness(private val ctx: PlatformContext) {

    private fun buildPeer(): Peer {
        val wallet = walletManager.getWallet() ?: error("Wallet must be initialized.")

        val genesisBlock = when (chain) {
            Chain.MAINNET -> Block.LivenetGenesisBlock
            Chain.TESTNET -> Block.TestnetGenesisBlock
            Chain.REGTEST -> Block.RegtestGenesisBlock
        }

        val keyManager = LocalKeyManager(wallet.seed.toByteVector32(), genesisBlock.hash)
        newLogger(loggerFactory).info { "NodeId: ${keyManager.nodeId}" }

        val params = NodeParams(
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
                )
            ),
            dustLimit = 546.sat,
            onChainFeeConf = OnChainFeeConf(
                maxFeerateMismatch = 10_000.0,
                closeOnOfflineMismatch = true,
                updateFeeMinDiffRatio = 0.1
            ),
            maxHtlcValueInFlightMsat = 150000000L,
            maxAcceptedHtlcs = 30,
            expiryDeltaBlocks = CltvExpiryDelta(144),
            fulfillSafetyBeforeTimeoutBlocks = CltvExpiryDelta(6),
            htlcMinimum = 1000.msat,
            minDepthBlocks = 3,
            toRemoteDelayBlocks = CltvExpiryDelta(144),
            maxToLocalDelayBlocks = CltvExpiryDelta(1000),
            feeBase = 1000.msat,
            feeProportionalMillionth = 10,
            reserveToFundingRatio = 0.01, // note: not used (overridden below)
            maxReserveToFundingRatio = 0.05,
            revocationTimeout = 20,
            authTimeout = 10,
            initTimeout = 10,
            pingInterval = 30,
            pingTimeout = 10,
            pingDisconnect = true,
            autoReconnect = false,
            initialRandomReconnectDelay = 5,
            maxReconnectInterval = 3600,
            chainHash = genesisBlock.hash,
            channelFlags = 1,
            paymentRequestExpiry = 3600,
            multiPartPaymentExpiry = 30,
            minFundingSatoshis = 1000.sat,
            maxFundingSatoshis = 16777215.sat,
            maxPaymentAttempts = 5,
            trampolineNode = acinqNodeUri,
            enableTrampolinePayment = true
        )

        val peer = Peer(tcpSocketBuilder, params, acinqNodeUri.id, electrumWatcher, channelsDB, MainScope())

        return peer
    }

    public val loggerFactory = LoggerFactory.default
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
    private val channelsDB by lazy { AppChannelsDB(dbFactory) }

    // RegTest
//    val acinqNodeUri = NodeUri(PublicKey.fromHex("039dc0e0b1d25905e44fdf6f8e89755a5e219685840d0bc1d28d3308f9628a3585"), "localhost", 48001)
//    val chain = Chain.REGTEST

    // TestNet
    private val acinqNodeUri = NodeUri(PublicKey.fromHex("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134"), "13.248.222.197", 9735)
    private val chain = Chain.TESTNET

    private val masterPubkeyPath = if (chain == Chain.MAINNET) "m/84'/0'/0'" else "m/84'/1'/0'"

    private val electrumClient by lazy { ElectrumClient(tcpSocketBuilder, MainScope()) }
    private val electrumWatcher by lazy { ElectrumWatcher(electrumClient, MainScope()) }

    private val peer by lazy { buildPeer() }

    private val walletManager by lazy { WalletManager() }
    private val appHistoryManager by lazy { AppHistoryManager(appDB, peer) }
    private val appConfigurationManager by lazy { AppConfigurationManager(appDB, electrumClient, chain, loggerFactory) }

    val currencyManager by lazy { CurrencyManager(loggerFactory, appDB, httpClient) }

    fun start() {
        AppConnectionsDaemon(
            appConfigurationManager,
            walletManager,
            networkMonitor,
            electrumClient,
            acinqNodeUri,
            loggerFactory
        ) {
            // initialize lazy variables
            currencyManager
            peer
        }
    }

    fun loadWallet(seed: ByteArray): Unit {
        if (walletManager.getWallet() == null) {
            walletManager.loadWallet(seed)
        }
    }

    fun prepWallet(mnemonics: List<String>, passphrase: String = ""): ByteArray {
        MnemonicCode.validate(mnemonics)
        return MnemonicCode.toSeed(mnemonics, passphrase)
    }

    val controllers: ControllerFactory = object : ControllerFactory {
        override fun content(): ContentController =
            AppContentController(loggerFactory, walletManager)

        override fun initialization(): InitializationController =
            AppInitController(loggerFactory, walletManager)

        override fun home(): HomeController =
            AppHomeController(loggerFactory, peer, electrumClient, networkMonitor, appHistoryManager)

        override fun receive(): ReceiveController =
            AppReceiveController(loggerFactory, peer)

        override fun scan(): ScanController =
            AppScanController(loggerFactory, peer)

        override fun restoreWallet(): RestoreWalletController =
            AppRestoreWalletController(loggerFactory, walletManager)

        override fun configuration(): ConfigurationController =
            AppConfigurationController(loggerFactory, walletManager)

        override fun displayConfiguration(): DisplayConfigurationController =
            AppDisplayConfigurationController(loggerFactory, appConfigurationManager)

        override fun electrumConfiguration(): ElectrumConfigurationController =
            AppElectrumConfigurationController(loggerFactory, appConfigurationManager, chain, masterPubkeyPath, walletManager, electrumClient)

        override fun channelsConfiguration(): ChannelsConfigurationController =
            AppChannelsConfigurationController(loggerFactory, peer, chain)
    }
}
