package fr.acinq.phoenix.managers

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.io.*
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
     * Flow of map of (bitcoinAddress -> amount) swap-ins.
     */
    private val _incomingSwaps = MutableStateFlow<Map<String, MilliSatoshi>>(HashMap())
    val incomingSwaps: StateFlow<Map<String, MilliSatoshi>> = _incomingSwaps
    private var _incomingSwapsMap by _incomingSwaps

    init {
        log.info { "init balance manager"}
        launch {
            val peer = peerManager.peerState.filterNotNull().first()
            launch { monitorChannelsBalance(peerManager) }
            launch { monitorIncomingSwapsMap(peer) }
        }
    }

    /** Watches the channels balance, first using the channels data from our database, then the live channels. */
    private suspend fun monitorChannelsBalance(peerManager: PeerManager) {
        peerManager.channelsFlow.collect { channels ->
            _balance.value = channels?.map { it.value.state.localBalance() }?.sum()
        }
    }

    private suspend fun monitorIncomingSwapsMap(peer: Peer) {
        launch {
            // iOS Note:
            // If the payment was received via the notification-service-extension
            // (which runs in a separate process), then you won't receive the
            // corresponding notifications (PaymentReceived) thru this mechanism.
            //
            peer.eventsFlow.collect { event ->
                when (event) {
                    is SwapInPendingEvent -> {
                        _incomingSwapsMap += (event.swapInPending.bitcoinAddress to event.swapInPending.amount.toMilliSatoshi())
                    }
                    is SwapInConfirmedEvent -> {
                        _incomingSwapsMap -= event.swapInConfirmed.bitcoinAddress
                    }
                    else -> Unit
                }
            }
        }
    }
}
