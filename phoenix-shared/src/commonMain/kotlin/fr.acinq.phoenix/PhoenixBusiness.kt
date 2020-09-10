package fr.acinq.phoenix

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.ElectrumWatcher
import fr.acinq.eclair.blockchain.fee.FeeEstimator
import fr.acinq.eclair.blockchain.fee.FeeTargets
import fr.acinq.eclair.blockchain.fee.OnChainFeeConf
import fr.acinq.eclair.crypto.LocalKeyManager
import fr.acinq.eclair.db.ChannelsDb
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.msat
import fr.acinq.eclair.utils.sat
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.phoenix.app.*
import fr.acinq.phoenix.app.ctrl.*
import fr.acinq.phoenix.app.ctrl.config.AppConfigurationController
import fr.acinq.phoenix.app.ctrl.config.AppDisplayConfigurationController
import fr.acinq.phoenix.app.ctrl.config.AppElectrumConfigurationController
import fr.acinq.phoenix.ctrl.*
import fr.acinq.phoenix.ctrl.config.ConfigurationController
import fr.acinq.phoenix.ctrl.config.DisplayConfigurationController
import fr.acinq.phoenix.ctrl.config.ElectrumConfigurationController
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.utils.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.kodein.db.DB
import org.kodein.db.DBFactory
import org.kodein.db.impl.factory
import org.kodein.db.inDir
import org.kodein.db.orm.kotlinx.KotlinxSerializer
import org.kodein.di.*
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUnsignedTypes::class)
class PhoenixBusiness {

    @Serializable
    object PeerFeeEstimator : FeeEstimator {
        override fun getFeeratePerKb(target: Int): Long = Eclair.feerateKw2KB(10000)
        override fun getFeeratePerKw(target: Int): Long = 10000

        val serializersModule = SerializersModule {
            polymorphic(FeeEstimator::class) {
                subclass(PeerFeeEstimator::class)
            }
        }
    }

    fun buildPeer(socketBuilder: TcpSocket.Builder, watcher: ElectrumWatcher, channelsDB: ChannelsDb, wallet: Wallet) : Peer {
        val remoteNodePubKey = PublicKey.fromHex("039dc0e0b1d25905e44fdf6f8e89755a5e219685840d0bc1d28d3308f9628a3585")

        val keyManager = LocalKeyManager(wallet.seed.toByteVector32(), Block.RegtestGenesisBlock.hash)
        println("nodeId:${keyManager.nodeId}") // TODO remove this!

        val params = NodeParams(
            keyManager = keyManager,
            alias = "alice",
            features = Features(
                setOf(
                    ActivatedFeature(Feature.OptionDataLossProtect, FeatureSupport.Optional),
                    ActivatedFeature(Feature.VariableLengthOnion, FeatureSupport.Optional)
                )
            ),
            dustLimit = 100.sat,
            onChainFeeConf = OnChainFeeConf(
                feeTargets = FeeTargets(6, 2, 2, 6),
                feeEstimator = PeerFeeEstimator,
                maxFeerateMismatch = 1.5,
                closeOnOfflineMismatch = true,
                updateFeeMinDiffRatio = 0.1
            ),
            maxHtlcValueInFlightMsat = 150000000L,
            maxAcceptedHtlcs = 100,
            expiryDeltaBlocks = CltvExpiryDelta(144),
            fulfillSafetyBeforeTimeoutBlocks = CltvExpiryDelta(6),
            htlcMinimum = 0.msat,
            minDepthBlocks = 3,
            toRemoteDelayBlocks = CltvExpiryDelta(144),
            maxToLocalDelayBlocks = CltvExpiryDelta(1000),
            feeBase = 546000.msat,
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
            chainHash = Block.RegtestGenesisBlock.hash,
            channelFlags = 1,
            paymentRequestExpiry = 3600,
            multiPartPaymentExpiry = 30,
            minFundingSatoshis = 1000.sat,
            maxFundingSatoshis = 16777215.sat,
            maxPaymentAttempts = 5,
            enableTrampolinePayment = true
        )

        val peer = Peer(socketBuilder, params, remoteNodePubKey, watcher, channelsDB, MainScope())

        return peer
    }

    val di = DI {
        bind<LoggerFactory>() with instance(LoggerFactory.default)
        bind<TcpSocket.Builder>() with instance(TcpSocket.Builder())
        bind<NetworkMonitor>() with singleton { NetworkMonitor() }

        bind<DBFactory<DB>>() with singleton { DB.factory.inDir(getApplicationFilesDirectoryPath(di)) }
        bind<DB>(tag = TAG_APPLICATION) with singleton {
            instance<DBFactory<DB>>().open("application", KotlinxSerializer())
        }

        bind<ChannelsDb>() with singleton { AppChannelsDB(instance()) }
//        bind<ChannelsDb>() with singleton { MemoryChannelsDB() }

        constant(tag = TAG_ACINQ_ADDRESS) with "localhost"
        bind<Boolean>(tag = TAG_IS_MAINNET) with singleton { instance<AppConfigurationManager>().getAppConfiguration().chain == Chain.MAINNET }

        bind<ElectrumClient>() with singleton { ElectrumClient(instance(), MainScope()) }
        bind<ElectrumWatcher>() with singleton { ElectrumWatcher(instance(), MainScope()) }
        bind<Peer>() with singleton {
            instance<WalletManager>().getWallet()?.let {
                buildPeer(instance(), instance(), instance(), it)
            } ?: error("Wallet must be initialized.")
        }
        bind<AppHistoryManager>() with singleton { AppHistoryManager(di) }
        bind<WalletManager>() with singleton { WalletManager(di) }
        bind<AppConfigurationManager>() with singleton { AppConfigurationManager(di) }

        // MVI controllers
        bind<ContentController>() with screenProvider { AppContentController(di) }
        bind<InitController>() with screenProvider { AppInitController(di) }
        bind<HomeController>() with screenProvider { AppHomeController(di) }
        bind<ReceiveController>() with screenProvider { AppReceiveController(di) }
        bind<ScanController>() with screenProvider { AppScanController(di) }
        bind<RestoreWalletController>() with screenProvider { AppRestoreWalletController(di) }

        // App Configuration
        bind<ConfigurationController>() with screenProvider { AppConfigurationController(di) }
        bind<DisplayConfigurationController>() with screenProvider { AppDisplayConfigurationController(di) }
        bind<ElectrumConfigurationController>() with screenProvider { AppElectrumConfigurationController(di) }

        // App daemons
        bind() from eagerSingleton { AppConnectionsDaemon(di) }
    }
}
