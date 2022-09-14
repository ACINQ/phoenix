package fr.acinq.phoenix.managers

import fr.acinq.phoenix.db.WalletPaymentOrderRow
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime

data class PaymentsPage(
    val offset: Int,
    val count: Int,
    val rows: List<WalletPaymentOrderRow>
) {
    constructor(): this(0, 0, emptyList())
}

@OptIn(ExperimentalTime::class)
class PaymentsPageFetcher(
    loggerFactory: LoggerFactory,
    private val databaseManager: DatabaseManager
): CoroutineScope by MainScope() {

    private val log = newLogger(loggerFactory)

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
            return
        }
        this.job?.let {
            it.cancel()
            this.job = null
        }

        // There could be a significant delay between requesting the list
        // and receiving the list. So the offset/count are used to track
        // the current request, even if it hasn't completed yet.

        this.offset = offset
        this.count = count
        this.seconds = Int.MIN_VALUE
        this.subscriptionIdx += 1

        val offsetSnapshot = offset
        val countSnapshot = count
        this.job = launch {
            val db = databaseManager.paymentsDb()
            db.listPaymentsOrderFlow(
                count = countSnapshot,
                skip = offsetSnapshot
            ).collect {
                _paymentsPage.value = PaymentsPage(
                    offset = offsetSnapshot,
                    count = countSnapshot,
                    rows = it
                )
            }
        }
    }

    fun subscribeToRecent(offset: Int, count: Int, seconds: Int) {
        log.debug { "subscribeToRecent(offset=$offset, count=$count, seconds=$seconds)" }

        if (this.offset == offset && this.count == count && this.seconds == seconds) {
            log.debug { "ignoring: no changes" }
            // No changes
            return
        }

        this.offset = offset
        this.count = count
        this.seconds = seconds
        this.subscriptionIdx += 1

        resetJob(subscriptionIdx)
    }

    private fun resetJob(idx: Int) {
        log.debug { "resetJob(idx=$idx)" }

        if (idx != subscriptionIdx) {
            log.debug { "resetJob: ignoring: idx mismatch"}
            return
        }
        job?.let {
            it.cancel()
            job = null
        }

        val offsetSnapshot = offset
        val countSnapshot = count
        val secondsSnapshot = seconds
        job = launch {
            val db = databaseManager.paymentsDb()
            if (secondsSnapshot > 0) {
                val date = Clock.System.now() - Duration.seconds(secondsSnapshot)
                db.listRecentPaymentsOrderFlow(
                    date = date.toEpochMilliseconds(),
                    count = countSnapshot,
                    skip = offsetSnapshot
                ).collect { rows ->
                    _paymentsPage.value = PaymentsPage(
                        offset = offsetSnapshot,
                        count = countSnapshot,
                        rows = rows
                    )
                    resetRefreshJob(idx, rows)
                }
            } else {
                db.listOutgoingInFlightPaymentsOrderFlow(
                    count = countSnapshot,
                    skip = offsetSnapshot
                ).collect { rows ->
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

    private fun resetRefreshJob(idx: Int, rows: List<WalletPaymentOrderRow>) {
        log.debug { "resetRefreshJob(idx=$idx, rows=${rows.size})" }

        if (idx != subscriptionIdx) {
            log.debug { "resetRefreshJob: ignoring: idx mismatch"}
            return
        }
        this.refreshJob?.let {
            it.cancel()
            this.refreshJob = null
        }
        if (this.seconds <= 0) {
            // The refreshJob isn't needed in this scenario
            return
        }

        val oldestCompleted = rows.lastOrNull { it.completedAt != null } ?: return
        val oldestTimestamp = Instant.fromEpochMilliseconds(oldestCompleted.completedAt!!)

        val refreshTimestamp = oldestTimestamp + Duration.seconds(this.seconds)
        val diff = refreshTimestamp - Clock.System.now()

        log.debug { "oldestTimestamp: ${oldestTimestamp.toEpochMilliseconds()}"}
        log.debug { "refreshTimestamp: ${refreshTimestamp.toEpochMilliseconds()}"}
        log.debug { "diff=${diff}"}

        this.refreshJob = launch {
            delay(diff)
            resetJob(idx)
        }
    }
}