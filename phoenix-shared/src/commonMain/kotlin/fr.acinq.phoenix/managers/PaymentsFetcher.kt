package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.data.WalletPaymentFetchOptions
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.db.WalletPaymentOrderRow
import fr.acinq.phoenix.utils.Cache
import fr.acinq.phoenix.utils.loggerExtensions.*
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
 * - metadataModifiedAt: Date
 *
 * Thus, since the key changes whenever the payment is updated, the fetcher (and built-in cache)
 * always provide an up-to-date WalletPayment instance in response to your query.
 */
class PaymentsFetcher(
    val loggerFactory: Logger,
    private var paymentsManager: PaymentsManager,
    cacheSizeLimit: Int
): CoroutineScope by MainScope() {

    // We're using an explicit Result type because:
    //   If the client is attempting to fetch a non-existent row from the database,
    //   we want to cache the fact that the row doesn't exist.
    //   And this is easier to do if we have a non-null Result item in the cache.
    //
    private data class Result(
        val info: WalletPaymentInfo?
    )

    private val log = loggerFactory.appendingTag("PaymentsFetcher")

    // Using a strict cache to ensure eviction based on actual usage
    private var cache = Cache<String, Result>(sizeLimit = cacheSizeLimit)

    // Used to consolidate database lookups for the same item
    private var pendingFetches = mutableMapOf<String, List<Continuation<WalletPaymentInfo?>>>()

    private fun cacheKey(
        row: WalletPaymentOrderRow,
        options: WalletPaymentFetchOptions
    ): String {
        // The row.identifier looks like this:
        // - "incoming|<id>|<createdAt>|<completedAt || null>"
        // - "outgoing|<id>|<createdAt>|<completedAt || null>"
        //
        // So the full key will look like:
        // - "<options>|incoming|<id>|<createdAt>|<completedAt || null>"
        // - "<options>|outgoing|<id>|<createdAt>|<completedAt || null>"
        //
        return "${options.flags}|${row.identifier}"
    }

    private fun staleCacheKeyPrefix(
        row: WalletPaymentOrderRow,
        options: WalletPaymentFetchOptions
    ): String {
        return "${options.flags}|${row.staleIdentifierPrefix}"
    }

    /**
     * Returns the payment if it exists in the cache.
     * A database fetch is not performed.
     */
    fun getCachedPayment(
        row: WalletPaymentOrderRow,
        options: WalletPaymentFetchOptions
    ): WalletPaymentInfo? {

        val key = cacheKey(row, options)
        return cache[key]?.info
    }

    /**
     * Searches the cache for a stale version of the payment.
     * Sometimes a stale version of the object is better than nothing.
     */
    fun getCachedStalePayment(
        row: WalletPaymentOrderRow,
        options: WalletPaymentFetchOptions
    ): WalletPaymentInfo? {

        val prefix = staleCacheKeyPrefix(row, options)
        val mostRecentStaleEntry = cache.filteredKeys { key ->
            key.startsWith(prefix)
        }.associateWith { key ->
            val suffix = key.substring(prefix.length)
            suffix.toLongOrNull() ?: 0
        }.maxByOrNull {
            it.value
        }

        val mostRecentStaleValue = mostRecentStaleEntry?.let {
            cache[it.key]
        }

        return mostRecentStaleValue?.info
    }

    /**
     * Fetches the payment, either via the cache or via a database fetch.
     *
     * If multiple queries for the same row arrive simultaneously,
     * they will be automatically consolidated into a single database fetch.
     */
    suspend fun getPayment(
        row: WalletPaymentOrderRow,
        options: WalletPaymentFetchOptions
    ): WalletPaymentInfo? = suspendCoroutine { continuation ->

        val key = cacheKey(row, options)
        log.debug { "fetching payment for key=$key" }
        cache[key]?.let {
            log.debug { "payment found in cache for key=$key" }
            continuation.resumeWith(kotlin.Result.success(it.info))
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
            log.debug { "fetching request found pending for key=$key" }
            pendingFetches[key] = pendingContinuations + continuation
            return@suspendCoroutine // database fetch already in progress
        }
        pendingFetches[key] = listOf(continuation)

        val completion = { result: Result ->
            cache[key] = result
            pendingFetches.remove(key)?.let { pendingContinuations ->
                pendingContinuations.forEach { pendingContinuation ->
                    pendingContinuation.resumeWith(kotlin.Result.success(result.info))
                }
            }
        }

        launch {
            paymentsManager.getPayment(row.id, options)?.let {
                completion(Result(it))
            } ?: run {
                completion(Result(null))
            }
        }
    }
}
