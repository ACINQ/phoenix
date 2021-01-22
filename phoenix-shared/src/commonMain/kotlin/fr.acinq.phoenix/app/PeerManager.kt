package fr.acinq.phoenix.app

import fr.acinq.bitcoin.Block
import fr.acinq.eclair.*
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
import fr.acinq.eclair.utils.toByteVector
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.db.SqliteChannelsDb
import fr.acinq.phoenix.db.SqlitePaymentsDb
import fr.acinq.phoenix.db.createChannelsDbDriver
import fr.acinq.phoenix.db.createPaymentsDbDriver
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalCoroutinesApi::class)
class PeerManager(
    loggerFactory: LoggerFactory,
    private val walletManager: WalletManager,
    private val configurationManager: AppConfigurationManager,
    private val paymentsDb: PaymentsDb,
    private val tcpSocketBuilder: TcpSocket.Builder,
    private val electrumWatcher: ElectrumWatcher,
    private val chain: Chain,
    private val ctx: PlatformContext
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)
    private val _peer = MutableStateFlow<Peer?>(null)
    public val peerState: StateFlow<Peer?> = _peer

    init {
        launch {
            _peer.value = buildPeer(
                wallet = walletManager.wallet.filterNotNull().first(),
                walletParams = configurationManager.walletParams.filterNotNull().first(),
            )
        }
    }

    suspend fun getPeer() = peerState.filterNotNull().first()

    private fun buildPeer(wallet: Wallet, walletParams: WalletParams): Peer {
        val genesisBlock = when (chain) {
            Chain.MAINNET -> Block.LivenetGenesisBlock
            Chain.TESTNET -> Block.TestnetGenesisBlock
            Chain.REGTEST -> Block.RegtestGenesisBlock
        }

        val keyManager = LocalKeyManager(wallet.seed.toByteVector(), genesisBlock.hash)
        logger.info { "nodeid=${keyManager.nodeId}" }

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

        logger.info { "nodeParams=$nodeParams" }
        logger.info { "walletParams=$walletParams" }

        val databases = object : Databases {
            override val channels: ChannelsDb get() = SqliteChannelsDb(createChannelsDbDriver(ctx), nodeParams.copy())
            override val payments: PaymentsDb get() = paymentsDb
        }

        return Peer(tcpSocketBuilder, nodeParams, walletParams, electrumWatcher, databases, MainScope())
    }
}