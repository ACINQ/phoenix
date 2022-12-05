package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.ChannelEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.SwapInEvents
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.io.Peer
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

    private val _balance = MutableStateFlow<MilliSatoshi?>(null)
    val balance: StateFlow<MilliSatoshi?> = _balance

    private val _swapInWallet = MutableStateFlow<WalletState?>(null)
    private val _pendingReservedUtxos = MutableStateFlow<Map<ByteVector32, List<WalletState.Utxo>>>(emptyMap())
    private val _reservedUtxos = MutableStateFlow<Set<WalletState.Utxo>>(emptySet())

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
