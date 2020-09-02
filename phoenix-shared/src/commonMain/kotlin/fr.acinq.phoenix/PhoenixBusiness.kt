package fr.acinq.phoenix

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eklair.*
import fr.acinq.eklair.blockchain.electrum.*
import fr.acinq.eklair.blockchain.fee.FeeEstimator
import fr.acinq.eklair.blockchain.fee.FeeTargets
import fr.acinq.eklair.blockchain.fee.OnChainFeeConf
import fr.acinq.eklair.crypto.LocalKeyManager
import fr.acinq.eklair.db.ChannelsDb
import fr.acinq.eklair.io.Peer
import fr.acinq.eklair.io.TcpSocket
import fr.acinq.eklair.utils.msat
import fr.acinq.eklair.utils.sat
import fr.acinq.phoenix.app.AppChannelsDB
import fr.acinq.phoenix.app.AppConnectionsDaemon
import fr.acinq.phoenix.app.AppHistoryManager
import fr.acinq.phoenix.app.ctrl.AppHomeController
import fr.acinq.phoenix.app.ctrl.AppReceiveController
import fr.acinq.phoenix.app.ctrl.AppScanController
import fr.acinq.phoenix.ctrl.HomeController
import fr.acinq.phoenix.ctrl.ReceiveController
import fr.acinq.phoenix.ctrl.ScanController
import fr.acinq.phoenix.utils.NetworkMonitor
import fr.acinq.phoenix.utils.getApplicationFilesDirectoryPath
import fr.acinq.phoenix.utils.screenProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import org.kodein.db.DB
import org.kodein.db.DBFactory
import org.kodein.db.impl.factory
import org.kodein.db.inDir
import org.kodein.di.*
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUnsignedTypes::class)
class PhoenixBusiness {

    fun buildPeer(socketBuilder: TcpSocket.Builder, watcher: ElectrumWatcher, channelsDB: ChannelsDb, seed: ByteVector32) : Peer {
        val remoteNodePubKey = PublicKey.fromHex("039dc0e0b1d25905e44fdf6f8e89755a5e219685840d0bc1d28d3308f9628a3585")

        val PeerFeeEstimator = object : FeeEstimator {
            override fun getFeeratePerKb(target: Int): Long = Eclair.feerateKw2KB(10000)
            override fun getFeeratePerKw(target: Int): Long = 10000
        }

        val keyManager = LocalKeyManager(seed, Block.RegtestGenesisBlock.hash)

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

        bind<ChannelsDb>() with singleton { AppChannelsDB(instance()) }
//        bind<ChannelsDb>() with singleton { MemoryChannelsDB() }

        constant(tag = "seed") with ByteVector32("0101010101010101010101010101010101010101010101010101010101010101")

        bind<ElectrumClient>() with singleton { ElectrumClient("localhost", 51001, null, MainScope()) }
        bind<ElectrumWatcher>() with singleton { ElectrumWatcher(instance(), MainScope()) }
        bind<Peer>() with singleton { buildPeer(instance(), instance(), instance(), instance(tag = "seed")) }
        bind<AppHistoryManager>() with singleton { AppHistoryManager(di) }

        bind<HomeController>() with screenProvider { AppHomeController(di) }
        bind<ReceiveController>() with screenProvider { AppReceiveController(di) }
        bind<ScanController>() with screenProvider { AppScanController(di) }

        bind() from eagerSingleton { AppConnectionsDaemon(di) }
    }
}
