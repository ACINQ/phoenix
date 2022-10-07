package fr.acinq.phoenix.managers

import fr.acinq.lightning.ChannelEvents
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.SwapInEvents
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.io.PaymentNotSent
import fr.acinq.lightning.io.PaymentProgress
import fr.acinq.lightning.io.PaymentSent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


class PaymentsManager(
    private val loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val databaseManager: DatabaseManager,
    private val nodeParamsManager: NodeParamsManager,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        databaseManager = business.databaseManager,
        nodeParamsManager = business.nodeParamsManager,
    )

    private val log = newLogger(loggerFactory)

    /**
     * A flow containing the total number of payments in the database,
     * and automatically refreshed when the database changes.
     */
    private val _paymentsCount = MutableStateFlow<Long>(0)
    val paymentsCount: StateFlow<Long> = _paymentsCount

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

    fun makeFetcher(cacheSizeLimit: Int = 250): PaymentsFetcher {
        return PaymentsFetcher(this, cacheSizeLimit)
    }

    fun makePageFetcher(): PaymentsPageFetcher {
        return PaymentsPageFetcher(loggerFactory, databaseManager)
    }
    
    private val _inFlightOutgoingPayments = MutableStateFlow<Set<UUID>>(setOf())
    val inFlightOutgoingPayments: StateFlow<Set<UUID>> = _inFlightOutgoingPayments

    init {
        launch {
            paymentsDb().listPaymentsCountFlow().collect {
                _paymentsCount.value = it
            }
        }

        launch {
            var mostRecentCompleted_prvLaunch: WalletPayment? = null
            var isFirstCollection = true

            paymentsDb().listPaymentsOrderFlow(count = 25, skip = 0).collect { list ->
                var mostRecentCompleted: WalletPayment? = null
                for (row in list) {
                    val paymentInfo = fetcher.getPayment(row, WalletPaymentFetchOptions.None)
                    if (paymentInfo != null && paymentInfo.payment.completedAt() > 0) {
                        mostRecentCompleted = paymentInfo.payment
                        break
                    }
                }

                if (isFirstCollection) {
                    isFirstCollection = false
                    mostRecentCompleted_prvLaunch = mostRecentCompleted
                } else if (mostRecentCompleted != null &&
                           mostRecentCompleted != mostRecentCompleted_prvLaunch) {
                    _lastCompletedPayment.value = mostRecentCompleted
                }
            }
        }

        launch {
            // iOS Note:
            // If the payment was received via the notification-service-extension
            // (which runs in a separate process), then you won't receive the
            // corresponding notifications (PaymentReceived) thru this mechanism.
            //
            peerManager.getPeer().eventsFlow.collect { event ->
                when (event) {
                    is PaymentProgress -> {
                        addToInFlightOutgoingPayments(event.request.paymentId)
                    }
                    is PaymentSent -> {
                        removeFromInFlightOutgoingPayments(event.request.paymentId)
                    }
                    is PaymentNotSent -> {
                        removeFromInFlightOutgoingPayments(event.request.paymentId)
                    }
                    else -> Unit
                }
            }
        }
        launch {
            nodeParamsManager.nodeParams.filterNotNull().first().nodeEvents.collect {
                when (it) {
                    is SwapInEvents.Requested -> {
                        // for now, we use a placeholder address because it's not yet exposed by the lightning-kmp API
                        _incomingSwapsMap = _incomingSwapsMap + ("foobar" to it.req.localFundingAmount.toMilliSatoshi())
                    }
                    is SwapInEvents.Accepted -> {
                        log.info { "swap-in request=${it.requestId} has been accepted for funding_fee=${it.fundingFee} service_fee=${it.serviceFee}" }
                    }
                    is SwapInEvents.Rejected -> {
                        log.error { "rejected swap-in for required_fee=${it.requiredFees} with error=${it.failure}" }
                        _incomingSwapsMap = _incomingSwapsMap - "foobar"
                    }
                    is ChannelEvents.Creating -> {
                        log.info { "channel=${it.state.channelId} is being created" }
                    }
                    is ChannelEvents.Created -> {
                        log.info { "channel=${it.state.channelId} has been successfully created!" }
                        _incomingSwapsMap = _incomingSwapsMap - "foobar"
                    }
                }
            }
        }
    }

    /** Adds to StateFlow<Set<UUID>> */
    private fun addToInFlightOutgoingPayments(id: UUID) {
        val oldSet = _inFlightOutgoingPayments.value
        val newSet = oldSet.plus(id)
        _inFlightOutgoingPayments.value = newSet
    }

    /** Removes from StateFlow<Set<UUID>> */
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
                    metadata = it.second ?: WalletPaymentMetadata(),
                    fetchOptions = options
                )
            }
        }
        is WalletPaymentId.OutgoingPaymentId -> {
            paymentsDb().getOutgoingPayment(id.id, options)?.let {
                WalletPaymentInfo(
                    payment = it.first,
                    metadata = it.second ?: WalletPaymentMetadata(),
                    fetchOptions = options
                )
            }
        }
    }
}
