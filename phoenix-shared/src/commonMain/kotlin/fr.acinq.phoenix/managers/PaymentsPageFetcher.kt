package fr.acinq.phoenix.managers

import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.phoenix.data.WalletPaymentInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

data class PaymentsPage(
    /** The offset value you passed to the `subscribeToX()` function. */
    val offset: Int,
    /** The count value you passed to the `subscribeToX()` function. */
    val count: Int,
    /**
     * The rows fetched from the database.
     * If there are fewer items in the database than requested,
     * then PaymentsPage.rows.count will be less than PaymentsPage.count.
     */
    val rows: List<WalletPaymentInfo>
) {
    constructor(): this(0, 0, emptyList())
}

class PaymentsPageFetcher(
    loggerFactory: LoggerFactory,
    private val databaseManager: DatabaseManager,
    private val contactsManager: ContactsManager
): CoroutineScope by MainScope() {

    private val log = loggerFactory.newLogger(this::class)

    private var offset: Int = 0
    private var count: Int = 0
    private var seconds: Int = Int.MIN_VALUE
    private var subscriptionIdx: Int = 0
    private var job: Job? = null
    private var refreshJob: Job? = null

    /**
     * A flow containing a page of payment rows.
     * This is controlled by the `subscribe()` function.
     * You use that function to control initialize the flow, and to modify it.
     *
     * Note:
     * iOS (with SwiftUI & LazyVStack) has some issues supporting a non-zero offset.
     * So on iOS, we're currently only incrementing the count.
     */
    private val _paymentsPage = MutableStateFlow(PaymentsPage())
    val paymentsPage: StateFlow<PaymentsPage> = _paymentsPage

    fun subscribeToAll(offset: Int, count: Int) {
        log.debug { "subscribeToAll(offset=$offset, count=$count)" }

        if (this.offset == offset && this.count == count && this.seconds == Int.MIN_VALUE) {
            // No changes
            log.debug { "ignoring: no changes" }
            return
        }
        this.job?.let {
            log.debug { "cancelling previous job" }
            it.cancel()
            this.job = null
        }

        // There could be a significant delay between requesting the list
        // and receiving the list. So the offset/count are used to track
        // the current request, even if it hasn't completed yet.

        this.offset = offset.coerceAtLeast(minimumValue = 0)
        this.count = count.coerceAtLeast(minimumValue = 1)
        this.seconds = Int.MIN_VALUE
        this.subscriptionIdx += 1

        val offsetSnapshot = offset
        val countSnapshot = count
        val subscriptionIdxSnapshot = subscriptionIdx
        this.job = launch {
            val db = databaseManager.paymentsDb()
            db.listPaymentsAsFlow(
                count = countSnapshot.toLong(),
                skip = offsetSnapshot.toLong()
            ).collect { rows ->
                if (subscriptionIdxSnapshot == subscriptionIdx) {
                    _paymentsPage.value = PaymentsPage(
                        offset = offsetSnapshot,
                        count = countSnapshot,
                        rows = rows
                    )
                }
            }
        }
    }

    fun subscribeToInFlight(offset: Int, count: Int) {
        log.debug { "subscribeToInFlight(offset=$offset, count=$count)" }

        if (this.offset == offset && this.count == count && this.seconds == 0) {
            // No changes
            log.debug { "ignoring: no changes" }
            return
        }
        this.job?.let {
            log.debug { "cancelling previous job" }
            it.cancel()
            this.job = null
        }

        this.offset = offset.coerceAtLeast(minimumValue = 0)
        this.count = count.coerceAtLeast(minimumValue = 1)
        this.seconds = 0
        this.subscriptionIdx += 1

        val offsetSnapshot = offset
        val countSnapshot = count
        val subscriptionIdxSnapshot = subscriptionIdx
        this.job = launch {
            val db = databaseManager.paymentsDb()
            db.listOutgoingInFlightPaymentsAsFlow(
                count = countSnapshot.toLong(),
                skip = offsetSnapshot.toLong()
            ).collect { rows ->
                if (subscriptionIdxSnapshot == subscriptionIdx) {
                    _paymentsPage.value = PaymentsPage(
                        offset = offsetSnapshot,
                        count = countSnapshot,
                        rows = rows
                    )
                }
            }
        }
    }

    fun subscribeToRecent(offset: Int, count: Int, seconds: Int) {
        log.debug { "subscribeToRecent(offset=$offset, count=$count, seconds=$seconds)" }

        if (seconds <= 0) {
            subscribeToInFlight(offset = offset, count = count)
            return
        }
        if (this.offset == offset && this.count == count && this.seconds == seconds) {
            // No changes
            log.debug { "ignoring: no changes" }
            return
        }

        this.offset = offset.coerceAtLeast(minimumValue = 0)
        this.count = count.coerceAtLeast(minimumValue = 1)
        this.seconds = seconds
        this.subscriptionIdx += 1

        resetSubscribeToRecentJob(subscriptionIdx)
    }

    private fun resetSubscribeToRecentJob(idx: Int) {
        log.debug { "resetSubscribeToRecentJob(idx=$idx)" }

        if (idx != subscriptionIdx) {
            log.debug { "resetSubscribeToRecentJob: ignoring: idx mismatch"}
            return
        }
        job?.let {
            log.debug { "cancelling previous job" }
            it.cancel()
            job = null
        }

        val offsetSnapshot = offset
        val countSnapshot = count
        val secondsSnapshot = seconds
        val subscriptionIdxSnapshot = subscriptionIdx
        job = launch {
            val db = databaseManager.paymentsDb()
            val date = Clock.System.now() - secondsSnapshot.seconds
            db.listRecentPaymentsAsFlow(
                count = countSnapshot.toLong(),
                skip = offsetSnapshot.toLong(),
                sinceDate = date.toEpochMilliseconds()
            ).collect { rows ->
                if (subscriptionIdxSnapshot == subscriptionIdx) {
                    _paymentsPage.value = PaymentsPage(
                        offset = offsetSnapshot,
                        count = countSnapshot,
                        rows = rows
                    )
                    resetRefreshJob(idx, rows)
                }
            }
        }
    }

    private fun resetRefreshJob(idx: Int, rows: List<WalletPaymentInfo>) {
        log.debug { "resetRefreshJob(idx=$idx, rows=${rows.size})" }

        if (idx != subscriptionIdx) {
            log.debug { "resetRefreshJob: ignoring: idx mismatch"}
            return
        }
        this.refreshJob?.let {
            log.debug { "cancelling previous refreshJob" }
            it.cancel()
            this.refreshJob = null
        }
        if (this.seconds <= 0) {
            // The refreshJob isn't needed in this scenario
            return
        }

        val oldestCompleted = rows.mapNotNull { it.payment.completedAt }.lastOrNull() ?: return
        val oldestTimestamp = Instant.fromEpochMilliseconds(oldestCompleted)

        val refreshTimestamp = oldestTimestamp + this.seconds.seconds
        val diff = refreshTimestamp - Clock.System.now()

    //  log.debug { "oldestTimestamp: ${oldestTimestamp.toEpochMilliseconds()}"}
    //  log.debug { "refreshTimestamp: ${refreshTimestamp.toEpochMilliseconds()}"}
    //  log.debug { "diff=${diff}"}

        this.refreshJob = launch {
            delay(diff)
            resetSubscribeToRecentJob(idx)
        }
    }
}