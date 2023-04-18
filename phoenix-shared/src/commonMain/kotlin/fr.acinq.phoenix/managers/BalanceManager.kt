package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.OutPoint
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.ChannelEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.SwapInEvents
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.channel.InteractiveTxInput
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.extensions.*
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
     * read [Peer.swapInWallet].walletStateFlow to get the swap-in balance. We have to ignore [_reservedOutpoints] to
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
     * A map of (channelId -> List<[OutPoint]>) representing the utxos that will be reserved to create a channel.
     *
     * When a channel is creating (see [ChannelEvents.Creating] in [NodeParams.nodeEvents]), this flow is updated
     * with the channel and its outpoints. When a channel has been created, it is removed from this map.
     */
    private val _pendingReservedOutpoints = MutableStateFlow<Map<ByteVector32, List<OutPoint>>>(emptyMap())

    /**
     * A map of (channelId -> List<[OutPoint]>) representing the utxos that are reserved to create a channel.
     *
     * When a channel has been created (see [ChannelEvents.Created]), it is added to this flow and the utxos are
     * manually ignored when computing the swap-in balance.
     *
     * When Electrum updates its view of the swap-in wallet, this flow is updated as well.
     */
    private val _reservedOutpoints = MutableStateFlow<Set<OutPoint>>(emptySet())

    /**
     * The wallet swap-in balance is computed manually, using the Electrum view of the swap-in wallet WITHOUT the
     * [_reservedOutpoints] for pending channels.
     */
    private val _swapInWalletBalance = MutableStateFlow(WalletBalance.empty())
    val swapInWalletBalance: StateFlow<WalletBalance> = _swapInWalletBalance

    init {
        log.info { "init balance manager"}
        launch {
            val peer = peerManager.peerState.filterNotNull().first()
            launch { monitorChannelsBalance(peerManager) }
            launch { monitorSwapInWallet(peer) }
            launch { monitorNodeEvents(peer) }
            launch { monitorSwapInBalance() }
        }
    }

    /** Watches the channels balance, first using the channels data from our database, then the live channels. */
    private suspend fun monitorChannelsBalance(peerManager: PeerManager) {
        peerManager.channelsFlow.collect { channels ->
            _balance.value = channels?.map { it.value.state.localBalance() }?.sum()
        }
    }

    /**
     * Copies [Peer.swapInWallet] changes to our own [_swapInWallet], and refreshes [_reservedOutpoints] so that spent
     * outputs are discarded.
     */
    private suspend fun monitorSwapInWallet(peer: Peer) {
        peer.swapInWallet.walletStateFlow.collect { wallet ->
            _swapInWallet.value = wallet
            _reservedOutpoints.update { it.intersect(wallet.utxos.map { it.outPoint }.toSet()) }
        }
    }

    /**
     * Monitors [NodeParams.nodeEvents] to update the incoming swap-in map as well as [_pendingReservedOutpoints] and
     * [_reservedOutpoints].
     *
     * It also updates the confirmation status of incoming payments received via new channels.
     */
    private suspend fun monitorNodeEvents(peer: Peer) {
        peer.nodeParams.nodeEvents.collect { event ->
            when (event) {
                is SwapInEvents.Requested -> {
                    log.info { "swap-in requested for ${event.req.localFundingAmount} id=${event.req.requestId}" }
                }
                is SwapInEvents.Accepted -> {
                    log.info { "swap-in accepted for id=${event.requestId} with funding_fee=${event.fundingFee} service_fee=${event.serviceFee}" }
                }
                is SwapInEvents.Rejected -> {
                    log.error { "swap-in rejected for id=${event.requestId} with required_fee=${event.requiredFees} error=${event.failure}" }
                }
                is ChannelEvents.Creating -> {
                    log.info { "channel creating with id=${event.state.channelId}" }
                    val channelId = event.state.channelId
                    val channelOutpoints = event.state.interactiveTxSession.localInputs.filterIsInstance<InteractiveTxInput.Local>().map { it.outPoint }
                    _pendingReservedOutpoints.update { it.plus(channelId to channelOutpoints) }
                }
                is ChannelEvents.Created -> {
                    log.info { "channel created with id=${event.state.channelId}" }
                    val channelId = event.state.channelId
                    _pendingReservedOutpoints.value[channelId]?.let { outpoints ->
                        _reservedOutpoints.update { it.union(outpoints) }
                        _pendingReservedOutpoints.update { it.minus(channelId) }
                    }
                }
                is ChannelEvents.Confirmed -> {
                    log.info { "channel confirmed for id=${event.state.channelId}" }
                    databaseManager.paymentsDb().updateNewChannelConfirmed(
                        channelId = event.state.channelId,
                        receivedAt = currentTimestampMillis()
                    )
                }
            }
        }
    }

    /** The swap-in balance is the swap-in wallet's balance without the [_reservedOutpoints]. */
    private suspend fun monitorSwapInBalance() {
        combine(_swapInWallet.filterNotNull(), _reservedOutpoints) { swapInWallet, reservedOutpoints ->
            log.info { "monitorSwapInBalance: reserved_outpoints=$reservedOutpoints swapInWallet=$swapInWallet"}
            val addressMinusReserved = swapInWallet.addresses.mapValues { (_, unspent) ->
                unspent.filterNot { reservedOutpoints.contains(it.outPoint) }
            }
            swapInWallet.copy(addresses = addressMinusReserved)
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
