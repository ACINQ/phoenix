package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.ChannelEvents
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.electrum.WalletState.Utxo
import fr.acinq.lightning.channel.ChannelStateWithCommitments
import fr.acinq.lightning.channel.*
import fr.acinq.lightning.channel.Offline
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.InitTlv
import fr.acinq.lightning.wire.TlvStream
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.extensions.calculateBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class PeerManager(
    loggerFactory: LoggerFactory,
    private val nodeParamsManager: NodeParamsManager,
    private val databaseManager: DatabaseManager,
    private val configurationManager: AppConfigurationManager,
    private val tcpSocketBuilder: TcpSocket.Builder,
    private val electrumWatcher: ElectrumWatcher,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        nodeParamsManager = business.nodeParamsManager,
        databaseManager = business.databaseManager,
        configurationManager = business.appConfigurationManager,
        tcpSocketBuilder = business.tcpSocketBuilder,
        electrumWatcher = business.electrumWatcher
    )

    private val logger = newLogger(loggerFactory)

    private val _peer = MutableStateFlow<Peer?>(null)
    val peerState: StateFlow<Peer?> = _peer

    private val _balance = MutableStateFlow<MilliSatoshi?>(null)
    val balance: StateFlow<MilliSatoshi?> = _balance

    private val _swapInWallet = MutableStateFlow<WalletState?>(null)
    private val _pendingReservedUtxos = MutableStateFlow<Map<ByteVector32, List<Utxo>>>(emptyMap())
    private val _reservedUtxos = MutableStateFlow<Set<Utxo>>(emptySet())

    private val _swapInWalletBalance = MutableStateFlow(WalletBalance.empty())
    val swapInWalletBalance: StateFlow<WalletBalance> = _swapInWalletBalance

    init {
        launch {
            val nodeParams = nodeParamsManager.nodeParams.filterNotNull().first()
            val walletParams = configurationManager.chainContext.filterNotNull().first().walletParams()
            val startupParams = configurationManager.startupParams.filterNotNull().first()

            var initTlvs = TlvStream.empty<InitTlv>()
            if (startupParams.requestCheckLegacyChannels) {
                val legacyKey = nodeParams.keyManager.legacyNodeKey
                val signature = Crypto.sign(
                    data = Crypto.sha256(legacyKey.publicKey.toUncompressedBin()),
                    privateKey = legacyKey.privateKey
                )
                initTlvs = initTlvs.addOrUpdate(InitTlv.PhoenixAndroidLegacyNodeId(legacyNodeId = legacyKey.publicKey, signature = signature))
            }

            logger.debug { "instantiating peer with nodeParams=$nodeParams walletParams=$walletParams initTlvs=$initTlvs" }

            val peer = Peer(
                initTlvStream = initTlvs,
                nodeParams = nodeParams,
                walletParams = walletParams,
                watcher = electrumWatcher,
                db = databaseManager.databases.filterNotNull().first(),
                socketBuilder = tcpSocketBuilder,
                scope = MainScope()
            )
            _peer.value = peer
            monitorPeer(peer)
        }
    }

    private fun monitorPeer(peer: Peer) {
        launch {
            peer.channelsFlow.collect { channels ->
                _balance.value = calculateBalance(channels)
            }
        }

        /**
         * What is the swap-in wallet's balance ?
         *
         * At first glance, it looks like we should be able to use:
         * `peer.swapInWallet.walletStateFlow`
         *
         * However, this is a reflection of the balance on the Electrum server.
         * Which means, there's a *DELAY* between:
         * - when we consider the channel opened (+ update balance, + create IncomingPayment)
         * - when that updated balance is bounced back from the Electrum server
         *
         * Even on a fast connection, that takes several seconds.
         * And during that time, the UI is in a bad state:
         *
         * - the (lightning) balance has been updated to reflect the new channel
         * - the incoming payment is reflected in the payments list
         * - but the wallet incorrectly says "+ X sat incoming"
         *
         * So we work around this shortcoming by taking advantage of:
         * - ChannelEvents.Creating
         * - ChannelEvents.Created
         *
         * Which gives us the `List<Utxo>` that we can manually ignore,
         * while we wait for the Electrum wallet to catch up.
         */

        launch {
            peer.swapInWallet.walletStateFlow.collect { wallet ->
                _swapInWallet.value = wallet
                _reservedUtxos.update { it.intersect(wallet.utxos) }
            }
        }
        launch {
            peer.nodeParams.nodeEvents.collect { event ->
                when (event) {
                    is ChannelEvents.Creating -> {
                        val channelId = event.state.channelId
                        val channelUtxos = event.state.wallet.confirmedUtxos
                        _pendingReservedUtxos.update { it.plus(channelId to channelUtxos) }
                    }
                    is ChannelEvents.Created -> {
                        val channelId = event.state.channelId
                        _pendingReservedUtxos.value[channelId]?.let { channelUtxos ->
                            _reservedUtxos.update { it.union(channelUtxos) }
                            _pendingReservedUtxos.update { it.minus(channelId) }
                        }
                    }
                }
            }
        }
        launch {
            combine(_swapInWallet.filterNotNull(), _reservedUtxos) { swapInWallet, reservedUtxos ->
                swapInWallet.minus(reservedUtxos)
            }.collect { availableWallet ->
                _swapInWalletBalance.value = WalletBalance(
                    confirmed = availableWallet.confirmedBalance,
                    unconfirmed = availableWallet.unconfirmedBalance
                )
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
}

data class WalletBalance(
    val confirmed: Satoshi,
    val unconfirmed: Satoshi
) {
    val total get() = confirmed + unconfirmed

    companion object {
        fun empty() = WalletBalance(0.sat, 0.sat)
    }
}