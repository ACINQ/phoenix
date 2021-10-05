package fr.acinq.phoenix.managers

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.io.*
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.getValue
import fr.acinq.lightning.utils.setValue
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.db.SqlitePaymentsDb
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsManager(
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

    data class PaymentsPage(
        val offset: Int,
        val count: Int,
        val rows: List<WalletPaymentOrderRow>
    ) {
        constructor(): this(0, 0, emptyList())
    }

    /**
     * A flow containing a page of payment rows.
     * This is controlled by the `subscribeToPaymentsPage()` function.
     * You use that function to control initialize the flow, and to modify it.
     *
     * Note:
     * iOS (with SwiftUI & LazyVStack) has some issues supporting a non-zero offset.
     * So on iOS, we're currently only incrementing the count.
     */
    val paymentsPage = MutableStateFlow<PaymentsPage>(PaymentsPage())
    private var paymentsPage_offset: Int = 0
    private var paymentsPage_count: Int = 0
    private var paymentsPage_job: Job? = null

    /**
     * A flow containing the total number of payments in the database,
     * and automatically refreshed when the database changes.
     */
    internal val paymentsCount = MutableStateFlow<Long>(0)

    /** Flow of map of (bitcoinAddress -> amount) swap-ins. */
    private val _incomingSwaps = MutableStateFlow<Map<String, MilliSatoshi>>(HashMap())
    val incomingSwaps: StateFlow<Map<String, MilliSatoshi>> = _incomingSwaps
    private var _incomingSwapsMap by _incomingSwaps

    /**
     * Broadcasts the most recently completed payment since the app was launched.
     * This includes incoming & outgoing payments (both successful & failed).
     *
     * If we haven't completed any payments since app launch, the value will be null.
     */
    private val _lastCompletedPayment = MutableStateFlow<WalletPayment?>(null)
    val lastCompletedPayment: StateFlow<WalletPayment?> = _lastCompletedPayment

    /**
     * Provides a default PaymentsFetcher for use by the app.
     * (You can also create your own instances if needed.)
     */
    val fetcher: PaymentsFetcher by lazy {
        PaymentsFetcher(paymentsManager = this, cacheSizeLimit = 250)
    }
    
    private val _inFlightOutgoingPayments = MutableStateFlow<Set<UUID>>(setOf())
    val inFlightOutgoingPayments: StateFlow<Set<UUID>> = _inFlightOutgoingPayments

    init {
        launch {
            paymentsDb().listPaymentsCountFlow().collect {
                paymentsCount.value = it
            }
        }

        launch {
            peerManager.getPeer().openListenerEventSubscription().consumeEach { event ->
                when (event) {
                    is PaymentProgress -> {
                        addToInFlightOutgoingPayments(event.request.paymentId)
                    }
                    is PaymentSent -> {
                        _lastCompletedPayment.value = event.payment
                        removeFromInFlightOutgoingPayments(event.request.paymentId)
                    }
                    is PaymentNotSent -> {
                        paymentsDb().getOutgoingPayment(event.request.paymentId)?.let {
                            _lastCompletedPayment.value = it
                        }
                        removeFromInFlightOutgoingPayments(event.request.paymentId)
                    }
                    is PaymentReceived -> {
                        _lastCompletedPayment.value = event.incomingPayment
                    }
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

    /// Adds to StateFlow<Set<UUID>>
    private fun addToInFlightOutgoingPayments(id: UUID) {
        val oldSet = _inFlightOutgoingPayments.value
        val newSet = oldSet.plus(id)
        _inFlightOutgoingPayments.value = newSet
    }

    /// Removes from StateFlow<Set<UUID>>
    private fun removeFromInFlightOutgoingPayments(id: UUID) {
        val oldSet = _inFlightOutgoingPayments.value
        val newSet = oldSet.minus(id)
        _inFlightOutgoingPayments.value = newSet
    }

    private suspend fun paymentsDb(): SqlitePaymentsDb {
        return databaseManager.paymentsDb()
    }

    suspend fun getPayment(
        id: WalletPaymentId,
        options: WalletPaymentFetchOptions
    ): WalletPaymentInfo? = when (id) {
        is WalletPaymentId.IncomingPaymentId -> {
            paymentsDb().getIncomingPayment(id.paymentHash, options)?.let {
                WalletPaymentInfo(
                    payment = it.first,
                    metadata = it.second ?: WalletPaymentMetadata()
                )
            }
        }
        is WalletPaymentId.OutgoingPaymentId -> {
            paymentsDb().getOutgoingPayment(id.id, options)?.let {
                WalletPaymentInfo(
                    payment = it.first,
                    metadata = it.second ?: WalletPaymentMetadata()
                )
            }
        }
    }

    fun subscribeToPaymentsPage(offset: Int, count: Int) {
        if (paymentsPage_offset == offset && paymentsPage_count == count) {
            // No changes
            return
        }
        paymentsPage_job?.let {
            it.cancel()
            paymentsPage_job = null
        }

        // There could be a significant delay between requesting the list
        // and receiving the list. So paymentsPage_offset/count are used to track
        // the current request, even if it hasn't completed yet.

        paymentsPage_offset = offset
        paymentsPage_count = count
        paymentsPage_job = launch {
            paymentsDb().listPaymentsOrderFlow(count = count, skip = offset).collect {
                paymentsPage.value = PaymentsPage(offset = offset, count = count, rows = it)
            }
        }
    }
}
