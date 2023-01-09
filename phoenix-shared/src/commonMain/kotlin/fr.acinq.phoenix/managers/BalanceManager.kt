package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.ChannelEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.SwapInEvents
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.extensions.calculateBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class BalanceManager(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val databaseManager: DatabaseManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        databaseManager = business.databaseManager
    )

    private val log = newLogger(loggerFactory)

    /** This balance is the sum of the channels' balance. This is the user's LN funds in the wallet. */
    private val _balance = MutableStateFlow<MilliSatoshi?>(null)
    val balance: StateFlow<MilliSatoshi?> = _balance

    /**
     * The swap-in wallet as seen by the current Electrum server. Contains a map of utxos and parent txs. It's
     * a copy of [Peer.swapInWallet].
     *
     * Since it reflects what the Electrum server sees, there can be a few seconds delay between actions initiated
     * by Phoenix and this view of the wallet. This delay can cause confusion and that's why the UI cannot directly
     * read [Peer.swapInWallet].walletStateFlow to get the swap-in balance. We have to ignore [_reservedUtxos] to
     * get the actual swap-in balance.
     *
     * Examples of the delay:
     * - when we consider the channel opened (+ update balance, + create IncomingPayment)
     * - when that updated balance is bounced back from the Electrum server
     *
     * Even on a fast connection, that takes several seconds.
     * And during that time, the UI is in a bad state:
     * - the (lightning) balance has been updated to reflect the new channel;
     * - the incoming payment is reflected in the payments list;
     * - but the wallet incorrectly says "+ X sat incoming".
     */
    private val _swapInWallet = MutableStateFlow<WalletState?>(null)

    /**
     * A map of (channelId -> List<Utxos>) representing the utxos that will be reserved to create a channel.
     *
     * When a channel is creating (see [ChannelEvents.Creating] in [NodeParams.nodeEvents]), this flow is updated
     * with the channel and its utxos. When a channel has been created, it is removed from this map.
     */
    private val _pendingReservedUtxos = MutableStateFlow<Map<ByteVector32, List<WalletState.Utxo>>>(emptyMap())

    /**
     * A map of (channelId -> List<Utxos>) representing the utxos that are reserved to create a channel.
     *
     * When a channel has been created (see [ChannelEvents.Created]), it is added to this flow and the utxos are
     * manually ignored when computing the swap-in balance.
     *
     * When Electrum updates its view of the swap-in wallet, this flow is updated as well.
     */
    private val _reservedUtxos = MutableStateFlow<Set<WalletState.Utxo>>(emptySet())

    /**
     * The wallet swap-in balance is computed manually, using the Electrum view of the swap-in wallet WITHOUT the
     * [_reservedUtxos] for pending channels.
     */
    private val _swapInWalletBalance = MutableStateFlow(WalletBalance.empty())
    val swapInWalletBalance: StateFlow<WalletBalance> = _swapInWalletBalance

    /**
     * Flow of map of (bitcoinAddress -> amount) swap-ins.
     * DEPRECATED: Replace with swapInWalletBalance
     */
    private val _incomingSwaps = MutableStateFlow<Map<String, MilliSatoshi>>(HashMap())
    val incomingSwaps: StateFlow<Map<String, MilliSatoshi>> = _incomingSwaps
    private var _incomingSwapsMap by _incomingSwaps

    init {
        launch {
            val peer = peerManager.peerState.filterNotNull().first()
            launch { monitorChannelsBalance(peer) }
            launch { monitorSwapInWallet(peer) }
            launch { monitorNodeEvents(peer) }
            launch { monitorSwapInBalance() }
        }
    }

    /**
     * Watches the channels balance. It is first initialized with the channels in the database, then
     * later uses the actual channels as they reestablish. This avoids having a misleading zero balance
     * at startup, when [Peer.channelsFlow] is not yet available.
     */
    private suspend fun monitorChannelsBalance(peer: Peer) {
        var isBoot = true
        val bootFlow = peer.bootChannelsFlow.filterNotNull()
        val channelsFlow = peer.channelsFlow
        combine(bootFlow, channelsFlow) { bootChannels, channels ->
            // The bootFlow will fire once, after the channels have been read from the database.
            if (isBoot) {
                isBoot = false
                bootChannels
            } else {
                channels
            }
        }.collect { channels ->
            _balance.value = calculateBalance(channels)
        }
    }

    /**
     * Copies [Peer.swapInWallet] changes to our own [_swapInWallet], and refreshes [_reservedUtxos] so that spent
     * outputs are discarded.
     */
    private suspend fun monitorSwapInWallet(peer: Peer) {
        peer.swapInWallet.walletStateFlow.collect { wallet ->
            _swapInWallet.value = wallet
            _reservedUtxos.update { it.intersect(wallet.utxos.toSet()) }
        }
    }

    /**
     * Monitors [NodeParams.nodeEvents] to update the incoming swap-in map as well as [_pendingReservedUtxos] and
     * [_reservedUtxos].
     *
     * It also updates the confirmation status of incoming payments received via new channels.
     */
    private suspend fun monitorNodeEvents(peer: Peer) {
        peer.nodeParams.nodeEvents.collect { event ->
            when (event) {
                is SwapInEvents.Requested -> {
                    // Using a placeholder address because it's not exposed by lightning-kmp.
                    _incomingSwapsMap = _incomingSwapsMap + ("foobar" to event.req.localFundingAmount.toMilliSatoshi())
                }
                is SwapInEvents.Accepted -> {
                    log.info { "swap-in request=${event.requestId} has been accepted for funding_fee=${event.fundingFee} service_fee=${event.serviceFee}" }
                }
                is SwapInEvents.Rejected -> {
                    log.error { "rejected swap-in for required_fee=${event.requiredFees} with error=${event.failure}" }
                    _incomingSwapsMap = _incomingSwapsMap - "foobar"
                }
                is ChannelEvents.Creating -> {
                    log.info { "channel=${event.state.channelId} is being created" }
                    val channelId = event.state.channelId
                    val channelUtxos = event.state.wallet.confirmedUtxos
                    _pendingReservedUtxos.update { it.plus(channelId to channelUtxos) }
                }
                is ChannelEvents.Created -> {
                    log.info { "channel=${event.state.channelId} has been successfully created!" }
                    val channelId = event.state.channelId
                    _pendingReservedUtxos.value[channelId]?.let { channelUtxos ->
                        _reservedUtxos.update { it.union(channelUtxos) }
                        _pendingReservedUtxos.update { it.minus(channelId) }
                    }
                    _incomingSwapsMap = _incomingSwapsMap - "foobar"
                }
                is ChannelEvents.Confirmed -> {
                    databaseManager.paymentsDb().updateNewChannelConfirmed(
                        channelId = event.state.channelId,
                        receivedAt = currentTimestampMillis()
                    )
                }
            }
        }
    }

    /** The swap-in balance is the swap-in wallet's balance without the [_reservedUtxos]. */
    private suspend fun monitorSwapInBalance() {
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

data class WalletBalance(
    val confirmed: Satoshi,
    val unconfirmed: Satoshi
) {
    val total get() = confirmed + unconfirmed

    companion object {
        fun empty() = WalletBalance(0.sat, 0.sat)
    }
}
