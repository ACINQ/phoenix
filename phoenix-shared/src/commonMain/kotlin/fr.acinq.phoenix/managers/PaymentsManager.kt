package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.io.PaymentNotSent
import fr.acinq.lightning.io.PaymentProgress
import fr.acinq.lightning.io.PaymentSent
import fr.acinq.lightning.io.ReceivePayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqlitePaymentsDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.random.Random
import kotlinx.coroutines.*

class PaymentsManager(
    private val loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val databaseManager: DatabaseManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        peerManager = business.peerManager,
        databaseManager = business.databaseManager
    )

    private val log = newLogger(loggerFactory)

    /**
     * A flow containing the total number of payments in the database,
     * and automatically refreshed when the database changes.
     */
    private val _paymentsCount = MutableStateFlow<Long>(0)
    val paymentsCount: StateFlow<Long> = _paymentsCount

    /**
     * Broadcasts the most recently completed payment since the app was launched.
     * This includes incoming & outgoing payments (both successful & failed).
     *
     * If we haven't completed any payments since app launch, the value will be null.
     */
    private val _lastCompletedPayment = MutableStateFlow<WalletPayment?>(null)
    val lastCompletedPayment: StateFlow<WalletPayment?> = _lastCompletedPayment

    private val _inFlightOutgoingPayments = MutableStateFlow<Set<UUID>>(setOf())
    val inFlightOutgoingPayments: StateFlow<Set<UUID>> = _inFlightOutgoingPayments

    /**
     * Provides a default PaymentsFetcher for use by the app.
     * (You can also create your own instances if needed.)
     */
    val fetcher: PaymentsFetcher by lazy {
        PaymentsFetcher(loggerFactory = loggerFactory, paymentsManager = this, cacheSizeLimit = 250)
    }

    fun makePageFetcher(): PaymentsPageFetcher {
        return PaymentsPageFetcher(loggerFactory, databaseManager)
    }

    init {
        launch {
            paymentsDb().listPaymentsCountFlow().collect {
                _paymentsCount.value = it
            }
        }

        launch {
            val appLaunch: Long = currentTimestampMillis()
            var isFirstCollection = true

            paymentsDb().listPaymentsOrderFlow(count = 25, skip = 0).collect { list ->

                // NB: lastCompletedPayment should NOT fire under any of the following conditions:
                // - relaunching app with completed payments in database
                // - restoring old wallet and downloading transaction history

                if (isFirstCollection) {
                    isFirstCollection = false
                } else {
                    for (row in list) {
                        val paymentInfo = fetcher.getPayment(row, WalletPaymentFetchOptions.None)
                        if (paymentInfo != null) {
                            val completedAt = paymentInfo.payment.completedAt()
                            if (completedAt > 0) {
                                // This is the most recent completed payment in the database
                                if (completedAt > appLaunch) {
                                    _lastCompletedPayment.value = paymentInfo.payment
                                }
                                break
                            }
                        }
                    }
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

    suspend fun generateInvoice(
        amount: MilliSatoshi,
        descriptionHash: ByteVector32,
        expirySeconds: Long
    ): String {
        val deferred = CompletableDeferred<PaymentRequest>()
        val preimage = ByteVector32(Random.secure().nextBytes(32)) // must be different everytime
        peerManager.getPeer().send(
            ReceivePayment(
                paymentPreimage = preimage,
                amount = amount,
                description = Either.Right(descriptionHash),
                expirySeconds = expirySeconds,
                result = deferred
            )
        )
        val request = deferred.await()
        return request.write()
    }

    suspend fun updateMetadata(id: WalletPaymentId, userDescription: String?) {
        paymentsDb().updateMetadata(
            id = id,
            userDescription = userDescription,
            userNotes = null
        )
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
