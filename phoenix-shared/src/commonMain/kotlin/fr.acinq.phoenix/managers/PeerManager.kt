package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.CltvExpiryDelta
import fr.acinq.lightning.DefaultSwapInParams
import fr.acinq.lightning.InvoiceDefaultRoutingFees
import fr.acinq.lightning.LiquidityEvents
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.SwapInParams
import fr.acinq.lightning.TrampolineFees
import fr.acinq.lightning.UpgradeRequired
import fr.acinq.lightning.WalletParams
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.blockchain.electrum.FinalWallet
import fr.acinq.lightning.blockchain.electrum.IElectrumClient
import fr.acinq.lightning.blockchain.electrum.SwapInManager
import fr.acinq.lightning.blockchain.electrum.SwapInWallet
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.fee.FeeratePerKw
import fr.acinq.lightning.channel.states.ChannelStateWithCommitments
import fr.acinq.lightning.channel.states.Normal
import fr.acinq.lightning.channel.states.Offline
import fr.acinq.lightning.channel.states.PersistedChannelState
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.Connection
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.InitTlv
import fr.acinq.lightning.wire.TlvStream
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.LocalChannelInfo
import fr.acinq.phoenix.utils.extensions.isTerminated
import fr.acinq.phoenix.utils.extensions.nextTimeout
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.logging.info
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*


class PeerManager(
    loggerFactory: LoggerFactory,
    private val nodeParamsManager: NodeParamsManager,
    private val databaseManager: DatabaseManager,
    private val configurationManager: AppConfigurationManager,
    private val notificationsManager: NotificationsManager,
    private val electrumClient: ElectrumClient,
    private val electrumWatcher: ElectrumWatcher,
) : CoroutineScope by CoroutineScope(CoroutineName("peer") + SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
    println("error in Peer coroutine scope: ${e.message}")
    val logger = loggerFactory.newLogger("PeerManager")
    logger.error(e) { "error in Peer scope: " }
}) {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        nodeParamsManager = business.nodeParamsManager,
        databaseManager = business.databaseManager,
        configurationManager = business.appConfigurationManager,
        notificationsManager = business.notificationsManager,
        electrumClient = business.electrumClient,
        electrumWatcher = business.electrumWatcher,
    )

    private val logger = loggerFactory.newLogger(this::class)

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
        combine(peer.currentTipFlow.filterNotNull(), peer.phoenixFinalWallet.wallet.walletStateFlow) { currentBlockHeight, wallet ->
            wallet.withConfirmations(
                currentBlockHeight = currentBlockHeight,
                // the final wallet does not need to distinguish between weak/deep/locked txs
                swapInParams = SwapInParams(
                    minConfirmations = 0,
                    maxConfirmations = Int.MAX_VALUE,
                    refundDelay = Int.MAX_VALUE,
                )
            )
        }
    }.stateIn(
        scope = this,
        started = SharingStarted.Lazily,
        initialValue = null,
    )

    /**
     * Flow of the peer's swap-in wallet [WalletState.WalletWithConfirmations], without the utxos reserved for channels.
     *
     * Utxos that are reserved for channels are excluded. This prevents a scenario where a channel is being created - and
     * the Lightning balance is updated - but the utxos for this channel are not yet spent and are such still listed in
     * the swap-in wallet flow. The UI would be incorrect for a while.
     *
     * See [SwapInManager.reservedWalletInputs] for details.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val swapInWallet = peerState.filterNotNull().flatMapLatest { peer ->
        combine(peer.currentTipFlow.filterNotNull(), peer.channelsFlow, peer.phoenixSwapInWallet.wallet.walletStateFlow) { currentBlockHeight, channels, swapInWallet ->
            val reservedInputs = SwapInManager.reservedWalletInputs(channels.values.filterIsInstance<PersistedChannelState>())
            val walletWithoutReserved = swapInWallet.withoutReservedUtxos(reservedInputs)
            walletWithoutReserved.withConfirmations(
                currentBlockHeight = currentBlockHeight,
                swapInParams = peer.walletParams.swapInParams
            )
        }
    }.stateIn(
        scope = this,
        started = SharingStarted.Lazily,
        initialValue = null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val swapInNextTimeout = swapInWallet.filterNotNull().mapLatest { it.nextTimeout }

    /**
     * FIXME: Temporary workaround. Must be done in lightning-kmp with proper testing.
     * See [Peer.waitForPeerReady]
     *
     * Return true if peer is connected & channels are normal. Terminated channels are ignored.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val mayDoPayments = peerState.filterNotNull().flatMapLatest { peer ->
        combine(peer.connectionState, peer.channelsFlow) { connectionState, channels ->
            when {
                connectionState !is Connection.ESTABLISHED -> false
                channels.isEmpty() -> true
                else -> channels.values.filterNot { it.isTerminated() }.all { it is Normal }
            }
        }
    }.stateIn(
        scope = this,
        started = SharingStarted.Lazily,
        initialValue = false
    )

    init {
        launch {
            val nodeParams = nodeParamsManager.nodeParams.filterNotNull().first()
            val startupParams = configurationManager.startupParams.filterNotNull().first()

            val walletParams = WalletParams(
                trampolineNode = NodeParamsManager.trampolineNodeUri,
                trampolineFees = listOf(
                    TrampolineFees(
                        feeBase = 4.sat,
                        feeProportional = 4_000,
                        cltvExpiryDelta = CltvExpiryDelta(576)
                    )
                ),
                invoiceDefaultRoutingFees = InvoiceDefaultRoutingFees(
                    feeBase = 1_000.msat,
                    feeProportional = 100,
                    cltvExpiryDelta = CltvExpiryDelta(144)
                ),
                swapInParams = SwapInParams(
                    minConfirmations = DefaultSwapInParams.MinConfirmations,
                    maxConfirmations = DefaultSwapInParams.MaxConfirmations,
                    refundDelay = DefaultSwapInParams.RefundDelay,
                ),
            )

            var initTlvs = TlvStream.empty<InitTlv>()
            if (startupParams.requestCheckLegacyChannels) {
                val legacyKey = nodeParams.keyManager.nodeKeys.legacyNodeKey
                val signature = Crypto.sign(
                    data = Crypto.sha256(legacyKey.publicKey.toUncompressedBin()),
                    privateKey = legacyKey.privateKey
                )
                initTlvs = initTlvs.addOrUpdate(InitTlv.PhoenixAndroidLegacyNodeId(legacyNodeId = legacyKey.publicKey, signature = signature))
            }

            logger.info { "instantiating peer with:\n    walletParams=$walletParams\n    initTlvs=$initTlvs\n    startupParams=$startupParams" }

            val peer = Peer(
                initTlvStream = initTlvs,
                nodeParams = nodeParams,
                walletParams = walletParams,
                client = electrumClient,
                watcher = electrumWatcher,
                db = databaseManager.databases.filterNotNull().first(),
                socketBuilder = null,
                scope = MainScope()
            )
            _peer.value = peer

            launch { monitorNodeEvents(nodeParams) }

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

    private suspend fun monitorNodeEvents(nodeParams: NodeParams) {
        nodeParams.nodeEvents.collect { event ->
            logger.debug { "collecting node_event=${event::class.simpleName}" }
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


/** The peer's swap-in wallet for Phoenix is always not null, because the client is always an [IElectrumClient] (see how this Peer is built in `PeerManager.init`). */
val Peer.phoenixSwapInWallet: SwapInWallet
    get() = this.swapInWallet!!

/** The peer's final wallet for Phoenix is always not null, because the client is always an [IElectrumClient] (see how this Peer is built in `PeerManager.init`). */
val Peer.phoenixFinalWallet: FinalWallet
    get() = this.finalWallet!!
