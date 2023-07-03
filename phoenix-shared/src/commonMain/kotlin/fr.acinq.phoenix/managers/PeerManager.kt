package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.UpgradeRequired
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.states.ChannelStateWithCommitments
import fr.acinq.lightning.channel.states.Offline
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.wire.InitTlv
import fr.acinq.lightning.wire.TlvStream
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.LocalChannelInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class PeerManager(
    loggerFactory: LoggerFactory,
    private val nodeParamsManager: NodeParamsManager,
    private val databaseManager: DatabaseManager,
    private val configurationManager: AppConfigurationManager,
    private val notificationsManager: NotificationsManager,
    private val electrumWatcher: ElectrumWatcher,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        nodeParamsManager = business.nodeParamsManager,
        databaseManager = business.databaseManager,
        configurationManager = business.appConfigurationManager,
        notificationsManager = business.notificationsManager,
        electrumWatcher = business.electrumWatcher,
    )

    private val logger = newLogger(loggerFactory)

    private val _peer = MutableStateFlow<Peer?>(null)
    val peerState: StateFlow<Peer?> = _peer

    /**
     * Our local view of our channels. It is initialized with data from the local db, then with the actual
     * channels once they have been reestablished.
     */
    private val _channelsFlow = MutableStateFlow<Map<ByteVector32, LocalChannelInfo>?>(null)
    val channelsFlow: StateFlow<Map<ByteVector32, LocalChannelInfo>?> = _channelsFlow

    /** Forward compatibility check. [UpgradeRequired] is sent by the peer when an old version of Phoenix restores a wallet that has been used with new channel types. */
    private val _upgradeRequired = MutableStateFlow(false)
    val upgradeRequired = _upgradeRequired.asStateFlow()

    /** Flow of the peer's final wallet [WalletState.WalletWithConfirmations]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val finalWallet = peerState.filterNotNull().flatMapLatest { peer ->
        combine(peer.currentTipFlow.filterNotNull(), peer.finalWallet.walletStateFlow) { (currentBlockHeight, _), wallet ->
            wallet.withConfirmations(
                currentBlockHeight = currentBlockHeight,
                minConfirmations = 0 // the final wallet does not need to distinguish between weakly/deeply confirmed txs
            )
        }
    }.stateIn(
        scope = this,
        started = SharingStarted.Lazily,
        initialValue = null,
    )

    /** Flow of the peer's swap-in wallet [WalletState.WalletWithConfirmations]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val swapInWallet = peerState.filterNotNull().flatMapLatest { peer ->
        combine(peer.currentTipFlow.filterNotNull(), peer.swapInWallet.walletStateFlow) { (currentBlockHeight, _), wallet ->
            wallet.withConfirmations(
                currentBlockHeight = currentBlockHeight,
                minConfirmations = peer.walletParams.swapInConfirmations
            )
        }
    }.stateIn(
        scope = this,
        started = SharingStarted.Lazily,
        initialValue = null,
    )

    init {
        launch {
            val nodeParams = nodeParamsManager.nodeParams.filterNotNull().first()
            val walletParams = configurationManager.chainContext.filterNotNull().first().walletParams()
            val startupParams = configurationManager.startupParams.filterNotNull().first()

            var initTlvs = TlvStream.empty<InitTlv>()
            if (startupParams.requestCheckLegacyChannels) {
                val legacyKey = nodeParams.keyManager.nodeKeys.legacyNodeKey
                val signature = Crypto.sign(
                    data = Crypto.sha256(legacyKey.publicKey.toUncompressedBin()),
                    privateKey = legacyKey.privateKey
                )
                initTlvs = initTlvs.addOrUpdate(InitTlv.PhoenixAndroidLegacyNodeId(legacyNodeId = legacyKey.publicKey, signature = signature))
            }

            logger.debug { "instantiating peer with walletParams=$walletParams initTlvs=$initTlvs startupParams=$startupParams" }

            val peer = Peer(
                initTlvStream = initTlvs,
                nodeParams = nodeParams,
                walletParams = walletParams,
                watcher = electrumWatcher,
                db = databaseManager.databases.filterNotNull().first(),
            //  trustedSwapInTxs = startupParams.trustedSwapInTxs,
                socketBuilder = null,
                scope = MainScope()
            )
            _peer.value = peer

            launch { monitorNodeEvents(nodeParams) }
            launch { updatePeerSwapInFeerate(peer) }

            // The local channels flow must use `bootFlow` first, as `channelsFlow` is empty when the wallet starts.
            // `bootFlow` data come from the local database and will be overridden by fresh data once the connection
            // with the peer has been established.
            val bootFlow = peer.bootChannelsFlow.filterNotNull()
            val channelsFlow = peer.channelsFlow
            var isBoot = true

            combine(bootFlow, channelsFlow) { bootChannels, channels ->
                // bootFlow will fire once, after the channels have been read from the database.
                if (isBoot) {
                    isBoot = false
                    bootChannels.entries.associate { it.key to LocalChannelInfo(it.key.toHex(), it.value, isBooting = true) }
                } else {
                    channels.entries.associate { it.key to LocalChannelInfo(it.key.toHex(), it.value, isBooting = false) }
                }
            }.collect {
                _channelsFlow.value = it
            }
        }
    }

    suspend fun getPeer() = peerState.filterNotNull().first()

    /**
     * Returns the underlying channel, if it's of type ChannelStateWithCommitments.
     * Note that Offline channels are automatically unwrapped.
     */
    fun getChannelWithCommitments(channelId: ByteVector32): ChannelStateWithCommitments? {
        val peer = peerState.value ?: return null
        var channel = peer.channels[channelId] ?: return null
        channel = when (channel) {
            is Offline -> channel.state
            else -> channel
        }
        return when (channel) {
            is ChannelStateWithCommitments -> channel
            else -> null
        }
    }

    /** Override the liquidity policy setting used by the node. */
    suspend fun updatePeerLiquidityPolicy(newPolicy: LiquidityPolicy) {
        getPeer().nodeParams.liquidityPolicy.value = newPolicy
    }

    /** Update the peer's swap-in feerate with values from mempool.space estimator. */
    private suspend fun updatePeerSwapInFeerate(peer: Peer) {
        configurationManager.mempoolFeerate.filterNotNull().collect { feerate ->
            logger.info { "using mempool.space feerate=$feerate" }
            peer.swapInFeeratesFlow.value = FeeratePerKw(feerate.hour)
        }
    }

    private suspend fun monitorNodeEvents(nodeParams: NodeParams) {
        nodeParams.nodeEvents.collect { event ->
            logger.info { "collecting node_event=$event" }
            when (event) {
                is LiquidityEvents.Rejected -> {
                    notificationsManager.saveLiquidityEventNotification(event)
                }
                is UpgradeRequired -> {
                    _upgradeRequired.value = true
                }
                else -> {}
            }
        }
    }
}
