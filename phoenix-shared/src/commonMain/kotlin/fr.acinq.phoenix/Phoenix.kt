package fr.acinq.phoenix

import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eklair.*
import fr.acinq.eklair.blockchain.fee.FeeEstimator
import fr.acinq.eklair.blockchain.fee.FeeTargets
import fr.acinq.eklair.blockchain.fee.OnChainFeeConf
import fr.acinq.eklair.crypto.LocalKeyManager
import fr.acinq.eklair.db.ChannelsDb
import fr.acinq.eklair.db.Databases
import fr.acinq.eklair.io.TcpSocket
import fr.acinq.eklair.utils.msat
import fr.acinq.eklair.utils.sat
import fr.acinq.phoenix.app.ctrl.AppHomeController
import fr.acinq.phoenix.app.ctrl.AppReceiveController
import fr.acinq.phoenix.ctrl.HomeController
import fr.acinq.phoenix.ctrl.ReceiveController
import fr.acinq.phoenix.utils.screenProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.eagerSingleton
import org.kodein.di.instance
import org.kodein.log.LoggerFactory


@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUnsignedTypes::class)
class Phoenix {

    fun buildPeer(socketBuilder: TcpSocket.Builder, databases: Databases, seed: ByteVector32) : Peer {
        // TODO: This is only valid on Salomon's computer!
        val remoteNodePubKey = PublicKey.fromHex("02d684ecbdbde1b556715a4a56186dfe045df1a0d18fe632843299254b482df7d9")

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
            maxHtlcValueInFlightMsat = 150000000uL,
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
            db = databases,
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

        return Peer(socketBuilder, params, remoteNodePubKey)
    }

    val di = DI {
        bind<LoggerFactory>() with instance(LoggerFactory.default)
        bind<TcpSocket.Builder>() with instance(TcpSocket.Builder())

        bind(tag = "seed") from instance(ByteVector32("0101010101010101010101010101010101010101010101010101010101010101"))

        bind<Databases>() with instance(
            object : Databases {
                override val channels: ChannelsDb get() = TODO("Not yet implemented")
            }
        )

        bind<Peer>() with eagerSingleton { buildPeer(instance(), instance(), instance(tag = "seed")) }

        bind<HomeController>() with screenProvider { AppHomeController(di) }
        bind<ReceiveController>() with screenProvider { AppReceiveController(di) }
    }
}
