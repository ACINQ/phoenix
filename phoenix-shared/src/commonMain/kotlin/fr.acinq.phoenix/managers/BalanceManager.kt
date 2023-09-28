package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.*
import fr.acinq.lightning.blockchain.electrum.SwapInManager
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.channel.states.ChannelState
import fr.acinq.lightning.channel.states.PersistedChannelState
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.extensions.localBalance
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

    /** The aggregated channels' balance. This is the user's LN funds in the wallet. See [ChannelState.localBalance] */
    private val _balance = MutableStateFlow<MilliSatoshi?>(null)
    val balance: StateFlow<MilliSatoshi?> = _balance

    /** The swap-in wallet. Reserved utxos are filtered out. */
    private val _swapInWallet = MutableStateFlow<WalletState.WalletWithConfirmations?>(null)
    val swapInWallet: StateFlow<WalletState.WalletWithConfirmations?> = _swapInWallet

    /** The swap-in wallet balance. Reserved utxos are filtered out. */
    private val _swapInWalletBalance = MutableStateFlow(WalletBalance.empty())
    val swapInWalletBalance: StateFlow<WalletBalance> = _swapInWalletBalance

    /** The balance of incoming payments whose funding tx is not yet confirmed - as seen from the database. */
    private val _pendingChannelsBalance = MutableStateFlow(0.msat)
    val pendingChannelsBalance: StateFlow<MilliSatoshi> = _pendingChannelsBalance

    init {
        launch {
            val peer = peerManager.peerState.filterNotNull().first()
            launch { monitorChannelsBalance(peerManager) }
            launch { monitorSwapInBalance(peer) }
            launch { monitorIncomingPaymentNotYetConfirmed() }
        }
    }

    /** Monitors the channels' balance, first using the channels data from our database, then the live channels. */
    private suspend fun monitorChannelsBalance(peerManager: PeerManager) {
        peerManager.channelsFlow.collect { channels ->
            _balance.value = channels?.mapNotNull { it.value.state.localBalance() }?.sum()
        }
    }

    /** Monitors the database for incoming payments that are received but whose funds are not yet usable (e.g., need confirmation). */
    private suspend fun monitorIncomingPaymentNotYetConfirmed() {
        databaseManager.paymentsDb().listIncomingPaymentsNotYetConfirmed().collect { payments ->
            val unconfirmedOnchain = payments.filter { it.completedAt == null }
            log.debug { "monitoring ${unconfirmedOnchain.size} unconfirmed on-chain payments" }
            _pendingChannelsBalance.value = unconfirmedOnchain.map { it.amount }.sum()
        }
    }

    /**
     * Constructs a user-friendly [WalletBalance] from [Peer.swapInWallet].
     *
     * Utxos that are reserved for channels are excluded. This prevents a scenario where a channel is being created - and
     * the Lightning balance is updated - but the utxos for this channel are not yet spent and are such still listed in
     * the swap-in wallet flow. The UI would be incorrect for a while.
     *
     * See [SwapInManager.reservedWalletInputs] for details.
     */
    private suspend fun monitorSwapInBalance(peer: Peer) {
        val swapInParams = peer.walletParams.swapInParams
        combine(peer.currentTipFlow.filterNotNull(), peer.channelsFlow, peer.swapInWallet.walletStateFlow) { (currentBlockHeight, _), channels, swapInWallet ->
            val reservedInputs = SwapInManager.reservedWalletInputs(channels.values.filterIsInstance<PersistedChannelState>())
            val walletWithoutReserved = WalletState(
                addresses = swapInWallet.addresses.map { (address, unspent) ->
                    address to unspent.filterNot { reservedInputs.contains(it.outPoint) }
                }.toMap().filter { it.value.isNotEmpty() },
                parentTxs = swapInWallet.parentTxs,
            )
            walletWithoutReserved.withConfirmations(
                currentBlockHeight = currentBlockHeight,
                swapInParams = swapInParams
            )
        }.collect { wallet ->
            _swapInWallet.value = wallet
            _swapInWalletBalance.value = WalletBalance(
                deeplyConfirmed = wallet.deeplyConfirmed.balance,
                weaklyConfirmed = wallet.weaklyConfirmed.balance,
                weaklyConfirmedMinBlockNeeded = wallet.weaklyConfirmed.minOfOrNull {
                    wallet.confirmationsNeeded(it)
                },
                unconfirmed = wallet.unconfirmed.balance,
            )
        }
    }
}

/**
 * Helper class representing the balance of the swap-in wallet. See [WalletState.WalletWithConfirmations].
 *
 * @param deeplyConfirmed amount that is confirmed and that can be used for a swap. This amount would always be 0 if we were to
 *      systematically accept swaps. But swaps can fail, or be rejected (fee too high).
 * @param weaklyConfirmed  amount that is confirmed but not deep enough for a swap.
 * @param weaklyConfirmedMinBlockNeeded minimum depth that the wallet's weakly confirmed utxos must reach.
 * @param unconfirmed amount that is not confirmed yet.
 */
data class WalletBalance(
    val deeplyConfirmed: Satoshi,
    val weaklyConfirmed: Satoshi,
    val weaklyConfirmedMinBlockNeeded: Int?,
    val unconfirmed: Satoshi,
) {
    val total get() = deeplyConfirmed + weaklyConfirmed + unconfirmed

    companion object {
        fun empty() = WalletBalance(0.sat, 0.sat, null, 0.sat)
    }
}
