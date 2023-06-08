package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.*
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.channel.Helpers
import fr.acinq.lightning.channel.states.PersistedChannelState
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.extensions.deeplyConfirmedBalance
import fr.acinq.phoenix.utils.extensions.localBalance
import fr.acinq.phoenix.utils.extensions.unconfirmedBalance
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
     * The wallet swap-in balance is computed manually, using the Electrum view of the swap-in wallet WITHOUT the
     * [_reservedOutpoints] for pending channels.
     */
    private val _swapInWalletBalance = MutableStateFlow(WalletBalance.empty())
    val swapInWalletBalance: StateFlow<WalletBalance> = _swapInWalletBalance

    /** Flow of incoming payment whose funding tx is not yet confirmed - as seen from the database. */
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

    /** Watches the channels balance, first using the channels data from our database, then the live channels. */
    private suspend fun monitorChannelsBalance(peerManager: PeerManager) {
        peerManager.channelsFlow.collect { channels ->
            _balance.value = channels?.mapNotNull { it.value.state.localBalance() }?.sum()
        }
    }

    /** Monitors the database for incoming payments that are received but funds are not yet usable (e.g., need confirmation). */
    private suspend fun monitorIncomingPaymentNotYetConfirmed() {
        databaseManager.paymentsDb().listIncomingPaymentsNotYetConfirmed().collect { payments ->
            log.info { "unconfirmed payments=$payments" }
            val unconfirmedOnchain = payments.filter { it.completedAt == null }
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
     * See [Helpers.reservedWalletInputs] for details.
     */
    private suspend fun monitorSwapInBalance(peer: Peer) {
        val swapInConfirmations = peer.walletParams.swapInConfirmations
        combine(peer.currentTipFlow.filterNotNull(), peer.channelsFlow, peer.swapInWallet.walletStateFlow) { (currentBlockHeight, _), channels, swapInWallet ->
            val reservedInputs = Helpers.reservedWalletInputs(channels.values.filterIsInstance<PersistedChannelState>())
            val walletWithoutReserved = WalletState(
                addresses = swapInWallet.addresses.map { (address, unspent) ->
                    address to unspent.filterNot { reservedInputs.contains(it.outPoint) }
                }.toMap().filter { it.value.isNotEmpty() },
                parentTxs = swapInWallet.parentTxs,
            )
            val withConfirmations = walletWithoutReserved.withConfirmations(
                currentBlockHeight = currentBlockHeight,
                minConfirmations = swapInConfirmations
            )
            WalletBalance(
                deeplyConfirmed = withConfirmations.deeplyConfirmedBalance(),
                weaklyConfirmed = withConfirmations.weaklyConfirmed.map {
                    it.blockHeight.toInt() + swapInConfirmations - currentBlockHeight to it.amount
                }.reduceOrNull { (height1, amount1), (height2, amount2) ->
                    minOf(height1, height2) to amount1 + amount2
                },
                unconfirmed = withConfirmations.unconfirmedBalance(),
            )
        }.collect { balance ->
            _swapInWalletBalance.value = balance
        }
    }
}

/**
 * Represents the balance of the swap-in wallet.
 * @param deeplyConfirmed the amount that is confirmed and that can be used for a swap. This amount would always be 0 if we were to
 *      accept swaps. But swaps can be rejected (e.g. the fee is too high) or can fail.
 * @param weaklyConfirmed the amount that is confirmed but not deep enough that a swap will be attempted. The first element of the
 *      pair is the minimum number of blocks that need to be added to the chain before the amount (or at least a part of it) is
 *      deemed safe enough to be considered as deeply confirmed, and hence can be swapped.
 * @param unconfirmed the amount that is not confirmed yet.
 */
data class WalletBalance(
    val deeplyConfirmed: Satoshi,
    val weaklyConfirmed: Pair<Int, Satoshi>?,
    val unconfirmed: Satoshi,
) {
    val total get() = deeplyConfirmed + (weaklyConfirmed?.second ?: 0.sat) + unconfirmed
    companion object {
        fun empty() = WalletBalance(0.sat, null, 0.sat)
    }
}
