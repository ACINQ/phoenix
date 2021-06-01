package fr.acinq.phoenix.app

import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.db.WalletPaymentId
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.utils.Cache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

/**
 * The PaymentsFetcher simplifies fetching & caching items from the database.
 *
 * The keys used for fetching & caching are `WalletPaymentOrderRow` which include:
 * - paymentId: [IncomingPaymentId || OutgoingPaymentId]
 * - created: Date
 * - completed: Date
 *
 * Thus, since the key changes when the row is updated, the fetcher (and built-in cache)
 * always provide an up-to-date WalletPayment instance in response to your query.
 */
class PaymentsFetcher(
    paymentsManager: PaymentsManager,
    cacheSizeLimit: Int
): CoroutineScope by MainScope() {

    // We're using an explicit Result type because:
    //   If the client is attempting to fetch a non-existent row from the database,
    //   we want to cache the fact that the row doesn't exist.
    //   And this is easier to do if we have a non-null Result item in the cache.
    //
    data class Result(val payment: WalletPayment?) {

        val incomingPayment: IncomingPayment? get() {
            return payment as? IncomingPayment
        }
        val outgoingPayment: OutgoingPayment? get() {
            return payment as? OutgoingPayment
        }
    }

    /// Used to reference database
    private var _paymentsManager: PaymentsManager = paymentsManager

    /// Using a strict cache to ensure eviction based on actual usage
    private var _cache = Cache<String, Result>(sizeLimit = cacheSizeLimit)

    /// Used to consolidate database lookups for the same item
    private var pendingFetches = mutableMapOf<String, List<Continuation<Result>>>()

    var cacheSizeLimit: Int
        get() = _cache.sizeLimit
        set(value) {
            _cache.sizeLimit = value
        }

    /**
     * Returns the payment if it exists in the cache.
     * A database fetch is not performed.
     */
    fun getCachedPayment(row: WalletPaymentOrderRow): Result {

        val key = row.identifier
        return _cache[key] ?: Result(payment = null)
    }

    /**
     * Searches the cache for a stale version of the payment.
     * Sometimes a stale version of the object is better than nothing.
     */
    fun getCachedStalePayment(row: WalletPaymentOrderRow): Result {

        // The keys in the cache look like:
        // - "incoming|<id>|<createdAt>|<completedAt || null>"
        // - "outgoing|<id>|<createdAt>|<completedAt || null>"
        //
        // When a row is updated, only the `completedAt` value changes.
        // So we can find a stale value by searching for another key with the same prefix.

        val prefix = row.staleIdentifierPrefix
        val mostRecentStaleEntry = _cache.filteredKeys { key ->
            key.startsWith(prefix)
        }.associateWith { key ->
            val suffix = key.substring(prefix.length)
            suffix.toLongOrNull() ?: 0
        }.maxByOrNull {
            it.value
        }

        val mostRecentStaleValue = mostRecentStaleEntry?.let {
            _cache[it.key]
        }

        return mostRecentStaleValue ?: Result(payment = null)
    }

    /**
     * Fetches the payment, either via the cache or via a database fetch.
     *
     * If multiple queries for the same row arrive simultaneously,
     * they will be automatically consolidated into a single database fetch.
     */
    suspend fun getPayment(row: WalletPaymentOrderRow): Result = suspendCoroutine { continuation ->

        val key = row.identifier
        _cache[key]?.let {
            continuation.resumeWith(kotlin.Result.success(it))
            return@suspendCoroutine
        }

        // We want to automatically consolidate multiple requests for the same item.
        //
        // That is, if we receive multiple requests for the same item at approximately
        // the same time, then we only want to fetch the item from disk once.
        //
        // As the user is scrolling, the UI is likely to rapidly request items from the database.
        // These requests could easily be duplicated as the user scrolls up and down.
        // If we consolidate them, we make less trips to the disk & speed up the UI.

        pendingFetches[key]?.let { pendingContinuations ->
            pendingFetches[key] = pendingContinuations + continuation
            return@suspendCoroutine // database fetch already in progress
        }
        pendingFetches[key] = listOf(continuation)

        val completion = { result: Result ->

            _cache[key] = result
            pendingFetches.remove(key)?.let { pendingContinuations ->

                pendingContinuations.forEach { pendingContinuation ->
                    pendingContinuation.resumeWith(kotlin.Result.success(result))
                }
            }
        }

        launch {
            val payment = when (val paymentId = row.id) {
                is WalletPaymentId.IncomingPaymentId -> {
                    _paymentsManager.getIncomingPayment(paymentId.paymentHash)
                }
                is WalletPaymentId.OutgoingPaymentId -> {
                    _paymentsManager.getOutgoingPayment(paymentId.id)
                }
            }
            completion(Result(payment))
        }
    }
}
