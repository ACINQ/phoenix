package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.*
import fr.acinq.lightning.blockchain.electrum.SwapInManager
import fr.acinq.lightning.blockchain.electrum.WalletState
import fr.acinq.lightning.blockchain.electrum.balance
import fr.acinq.lightning.channel.states.ChannelState
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.utils.extensions.localBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    /** The aggregated channels' balance. This is the user's LN funds in the wallet. See [ChannelState.localBalance] */
    private val _balance = MutableStateFlow<MilliSatoshi?>(null)
    val balance: StateFlow<MilliSatoshi?> = _balance

    /** The swap-in wallet balance. Reserved utxos are filtered out. */
    private val _swapInWalletBalance = MutableStateFlow(WalletBalance.empty())
    val swapInWalletBalance: StateFlow<WalletBalance> = _swapInWalletBalance

    init {
        launch { monitorChannelsBalance(peerManager) }
        launch { monitorSwapInBalance() }
    }

    /** Monitors the channels' balance, first using the channels data from our database, then the live channels. */
    private suspend fun monitorChannelsBalance(peerManager: PeerManager) {
        peerManager.channelsFlow.collect { channels ->
            _balance.value = channels?.mapNotNull { it.value.state.localBalance() }?.sum()
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
    private suspend fun monitorSwapInBalance() {
        peerManager.swapInWallet.filterNotNull().collect { wallet ->
            _swapInWalletBalance.value = WalletBalance(
                deeplyConfirmed = wallet.deeplyConfirmed.balance,
                weaklyConfirmed = wallet.weaklyConfirmed.balance,
                weaklyConfirmedMinBlockNeeded = wallet.weaklyConfirmed.minOfOrNull {
                    wallet.confirmationsNeeded(it)
                },
                unconfirmed = wallet.unconfirmed.balance,
                locked = wallet.lockedUntilRefund.balance,
                readyForRefund = wallet.readyForRefund.balance
            )
        }
    }
}

/**
 * Helper class representing the balance of the swap-in wallet. See [WalletState.WalletWithConfirmations].
 *
 * @param deeplyConfirmed balance that is confirmed and that can be used for a swap. This amount would always be 0 if we were to
 *      systematically accept swaps. But swaps can fail, or be rejected (fee too high).
 * @param weaklyConfirmed  balance that is confirmed but not deep enough for a swap.
 * @param weaklyConfirmedMinBlockNeeded minimum depth that the wallet's weakly confirmed utxos must reach.
 * @param unconfirmed balance that is not confirmed yet.
 * @param locked balance that cannot be swapped anymore, but cannot be spent yet either.
 * @param readyForRefund balance that cannot be swapped anymore, but can be spent unilaterally.
 */
data class WalletBalance(
    val deeplyConfirmed: Satoshi,
    val weaklyConfirmed: Satoshi,
    val weaklyConfirmedMinBlockNeeded: Int?,
    val locked: Satoshi,
    val readyForRefund: Satoshi,
    val unconfirmed: Satoshi,
) {
    val total get() = readyForRefund + locked + deeplyConfirmed + weaklyConfirmed + unconfirmed

    companion object {
        fun empty() = WalletBalance(0.sat, 0.sat, null, 0.sat, 0.sat, 0.sat)
    }
}
