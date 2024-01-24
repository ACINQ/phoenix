package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.utils.loggerExtensions.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Architecture Notes:
 *
 * At the time of implementation, it was determined that the bitcoin markets for both USD & EUR
 * were sufficiently deep & liquid enough to provide reliable rates.
 *
 * That is to say, if you fetch the BTC-USD rate, and the BTC-EUR rate,
 * you would then be able to reliably approximate the USD-EUR rate.
 * I.e. calculated rate would reliably approximate official USD-EUR mid-market rate.
 *
 * However, the same is not true for every fiat currency.
 * For example, fetching the BTC-COP rate produces an unreliable approximate for USD-COP.
 *
 * It is expected that this will improve over time as the markets mature.
 * However, for the time being, we rely on the more liquid USD-FIAT exchange rates.
 * Thus, if we fetch both BTC-USD & USD-COP, we can easily convert between any of the 3 currencies.
 */

/**
 * Manages fetching and updating exchange rates.
 */
class CurrencyManager(
    loggerFactory: Logger,
    private val configurationManager: AppConfigurationManager,
    private val appDb: SqliteAppDb,
    private val httpClient: HttpClient
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.newLoggerFactory,
        configurationManager = business.appConfigurationManager,
        appDb = business.appDb,
        httpClient = business.httpClient
    )

    private val log = loggerFactory.appendingTag("CurrencyManager")

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * List of fiat currencies where we directly fetch the FIAT/BTC exchange rate.
     * See "architecture notes" at top of file for discussion.
     */
    private val highLiquidityMarkets = setOf(FiatCurrency.USD, FiatCurrency.EUR)

    private val specialMarkets = setOf(
        FiatCurrency.ARS_BM, // Argentine Peso (blue market)
        FiatCurrency.CUP_FM, // Cuban Peso (free market)
        FiatCurrency.LBP_BM  // Lebanese Pound (black market)
    )

    private val missingFromCoinbase = setOf(
        FiatCurrency.CUP, // Cuban Peso
        FiatCurrency.ERN, // Eritrean Nakfa (exists in response, but refers to ERN altcoin)
        FiatCurrency.IRR, // Iranian Rial
        FiatCurrency.KPW, // North Korean Won
        FiatCurrency.SDG, // Sudanese Pound
        FiatCurrency.SOS, // Somali Shilling
        FiatCurrency.SYP  // Syrian Pound
    )

    /**
     * We use a number of different APIs to fetch all the data we need.
     * This interface defines the shared format for each API.
     */
    private interface API {
        /**
         * Primarily used for debugging
         */
        val name: String

        /**
         * How often to perform an automatic refresh.
         * Some APIs impose limits, and others simply don't refresh (server-side) as often.
         */
        val refreshDelay: Duration

        /**
         * List of fiat currencies updated by the API.
         * A currency should only be represented in a single API.
         */
        val fiatCurrencies: Set<FiatCurrency>
    }

    /**
     * The blockchain.info API is used to refresh the BitcoinPriceRates
     * for currencies with "high-liquidity markets" (i.e. USD/EUR).
     * Since bitcoin prices are volatile, we refresh them often.
     */
    private val blockchainInfoAPI = object : API {
        override val name = "blockchain.info"
        override val refreshDelay = 20.minutes
        override val fiatCurrencies = FiatCurrency.values.filter {
            highLiquidityMarkets.contains(it)
        }.toSet()
    }

    /**
     * The coinbase API is used to refresh select UsdPriceRates.
     * Since fiat prices are less volatile, we refresh them less often.
     */
    private val coinbaseAPI = object : API {
        override val name = "coinbase"
        override val refreshDelay = 60.minutes
        override val fiatCurrencies = FiatCurrency.values.filter {
            !highLiquidityMarkets.contains(it) &&
            !specialMarkets.contains(it) &&
            !missingFromCoinbase.contains(it)
        }.toSet()
    }

    /**
     * The coindesk API is used to refresh select UsdPriceRates that are not available from Coinbase.
     * Since fiat prices are less volatile, we refresh them less often.
     * Also, the source only refreshes the values once per hour.
     */
    private val coindeskAPI = object : API {
        override val name = "coindesk"
        override val refreshDelay = 60.minutes
        override val fiatCurrencies = FiatCurrency.values.filter {
            missingFromCoinbase.contains(it)
        }.toSet()
    }

    /**
     * The bluelytics API is used to fetch the "blue market" price for the Argentine Peso.
     * - ARS => government controlled exchange rate
     * - ARS_BM => free market exchange rate
     */
    private val bluelyticsAPI = object : API {
        override val name = "bluelytics"
        override val refreshDelay = 120.minutes
        override val fiatCurrencies = setOf(FiatCurrency.ARS_BM)
    }

    /**
     * The yadio API is used to fetch various "free market" prices.
     * For example:
     * - CUP => government controlled exchange rate
     * - CUP_FM => free market exchange rate
     */
    private val yadioAPI = object : API {
        override val name = "yadio"
        override val refreshDelay = 120.minutes
        override val fiatCurrencies = setOf(FiatCurrency.CUP_FM, FiatCurrency.LBP_BM)
    }

    /** Public consumable flow that includes the most recent exchange rates */
    val ratesFlow: StateFlow<List<ExchangeRate>> by lazy {
        appDb.listBitcoinRates().stateIn(
            scope = this,
            started = SharingStarted.Eagerly,
            initialValue = listOf()
        )
    }

    /**
     * Returns a snapshot of the ExchangeRate for the primary FiatCurrency.
     * That is, an instance of OriginalFiat, where:
     * - type => current primary FiatCurrency (via AppConfigurationManager)
     * - price => BitcoinPriceRate.price for FiatCurrency type
     */
    fun calculateOriginalFiat(): ExchangeRate.BitcoinPriceRate? {

        val fiatCurrency = configurationManager.preferredFiatCurrencies.value?.primary ?: return null

        val rates = ratesFlow.value

        val fiatRate = rates.firstOrNull { it.fiatCurrency == fiatCurrency } ?: return null

        return when (fiatRate) {
            is ExchangeRate.BitcoinPriceRate -> {
                // We have a direct exchange rate.
                // BitcoinPriceRate.rate => The price of 1 BTC in this currency
                fiatRate
            }
            is ExchangeRate.UsdPriceRate -> {
                // We have an indirect exchange rate.
                // UsdPriceRate.price => The price of 1 US Dollar in this currency
                rates.filterIsInstance<ExchangeRate.BitcoinPriceRate>().firstOrNull {
                    it.fiatCurrency == FiatCurrency.USD
                }?.let { usdRate ->
                    ExchangeRate.BitcoinPriceRate(
                        fiatCurrency = fiatCurrency,
                        price = usdRate.price * fiatRate.price,
                        source = "${fiatRate.source}/${usdRate.source}",
                        timestampMillis = fiatRate.timestampMillis.coerceAtMost(
                            usdRate.timestampMillis
                        )
                    )
                }
            }
        }
    }

    /** Utility class used to track refresh progress on a per-currency basis. */
    private data class RefreshInfo(
        val lastRefresh: Instant,
        val nextRefresh: Instant,
        val failCount: Int
    ) {
        constructor() : this(
            lastRefresh = Instant.fromEpochMilliseconds(0),
            nextRefresh = Instant.fromEpochMilliseconds(0),
            failCount = 0
        )

        fun fail(now: Instant): RefreshInfo {
            val newFailCount = failCount + 1
            val delay = when (newFailCount) {
                1 -> 30.seconds
                2 -> 1.minutes
                3 -> 5.minutes
                4 -> 10.minutes
                5 -> 30.minutes
                6 -> 60.minutes
                else -> 120.minutes
            }
            return RefreshInfo(
                lastRefresh = this.lastRefresh,
                nextRefresh = now + delay,
                failCount = newFailCount
            )
        }
    }

    private var refreshList = mutableMapOf<FiatCurrency, RefreshInfo>()
    private var autoRefreshJob: Job? = null

    private val _refreshFlow = MutableStateFlow<Set<FiatCurrency>>(setOf())
    val refreshFlow: StateFlow<Set<FiatCurrency>> = _refreshFlow

    private var networkAccessEnabled = false
    private var autoRefreshEnabled = true

    /**
     * Used by AppConnectionsDaemon.
     * Invoked when an internet connection is available.
     */
    internal fun enableNetworkAccess() {
        networkAccessEnabled = true
        maybeStartAutoRefresh()
    }

    /**
     * Used by AppConnectionsDaemon.
     * Invoked when there is not an internet connection available.
     */
    internal fun disableNetworkAccess() {
        networkAccessEnabled = false
        stopAutoRefresh()
    }

    fun enableAutoRefresh() {
        autoRefreshEnabled = true
        maybeStartAutoRefresh()
    }

    fun disableAutoRefresh() {
        autoRefreshEnabled = false
        stopAutoRefresh()
    }

    private fun maybeStartAutoRefresh() {
        if (networkAccessEnabled && autoRefreshEnabled && autoRefreshJob == null) {
            autoRefreshJob = launchAutoRefreshJob()
        }
    }

    private fun stopAutoRefresh() = launch {
        autoRefreshJob?.cancelAndJoin()
        autoRefreshJob = null
    }

    fun refreshAll(targets: List<FiatCurrency>, force: Boolean = true) = launch {
        stopAutoRefresh().join()
        val targetSet = prepTargets(targets)

        val deferred1 = async {
            refresh(targetSet, blockchainInfoAPI, forceRefresh = force)
        }
        val deferred2 = async {
            refresh(targetSet, coinbaseAPI, forceRefresh = force)
        }
        val deferred3 = async {
            refresh(targetSet, coindeskAPI, forceRefresh = force)
        }
        val deferred4 = async {
            refresh(targetSet, bluelyticsAPI, forceRefresh = force)
        }
        val deferred5 = async {
            refresh(targetSet, yadioAPI, forceRefresh = force)
        }
        listOf(deferred1, deferred2, deferred3, deferred4, deferred5).awaitAll()
        maybeStartAutoRefresh()
    }

    private fun launchAutoRefreshJob() = launch {
        var blockchainInfoJob: Job? = null
        var coinbaseJob: Job? = null
        var coindeskJob: Job? = null
        var bluelyticsJob: Job? = null
        var yadioJob: Job? = null

        // There's no reason to refresh ALL the currencies.
        // This is just unnecessary network and disk IO.
        // The `preferredFiatCurrencies` tells us which currencies are being used in the app.
        // So we can just refresh those.

        configurationManager.preferredFiatCurrencies.filterNotNull().collect { preferredFiatCurrencies ->

            val targetSet = prepTargets(preferredFiatCurrencies.all)

            blockchainInfoJob?.cancel()
            blockchainInfoJob = launchAutoRefreshJob(targetSet, blockchainInfoAPI)

            coinbaseJob?.cancel()
            coinbaseJob = launchAutoRefreshJob(targetSet, coinbaseAPI)

            coindeskJob?.cancel()
            coindeskJob = launchAutoRefreshJob(targetSet, coindeskAPI)

            bluelyticsJob?.cancel()
            bluelyticsJob = launchAutoRefreshJob(targetSet, bluelyticsAPI)

            yadioJob?.cancel()
            yadioJob = launchAutoRefreshJob(targetSet, yadioAPI)
        }
    }

    private fun launchAutoRefreshJob(allTargets: Set<FiatCurrency>, api: API) = launch {

        val targets = allTargets.filter { api.fiatCurrencies.contains(it) }.toSet()
        if (targets.isEmpty()) {
            log.debug { "API(${api.name}): Nothing to refresh" }
            return@launch
        }

        while (isActive) {
            val nextDelay = calculateDelay(targets, api.refreshDelay)
            log.debug { "API(${api.name}): Next refresh: $nextDelay" }
            delay(nextDelay)
            refresh(targets, api, forceRefresh = false)
        }
    }

    /**
     * Updates the `refreshList` with fresh RefreshInfo values.
     * Only the `attempted` currencies are updated.
     * The `refreshed` parameter marks those currencies that were successfully refreshed.
     */
    private fun updateRefreshList(
        api: API,
        attempted: Collection<FiatCurrency>,
        refreshed: Collection<FiatCurrency>
    ) {
        val refreshedSet = refreshed.toSet()
        val now = Clock.System.now()
        attempted.forEach { fiatCurrency ->
            if (refreshedSet.contains(fiatCurrency)) { // refresh succeeded
                refreshList[fiatCurrency] = RefreshInfo(
                    lastRefresh = now,
                    nextRefresh = now + api.refreshDelay,
                    failCount = 0
                )
            } else { // refresh failed
                val refreshInfo = refreshList[fiatCurrency] ?: RefreshInfo()
                refreshList[fiatCurrency] = refreshInfo.fail(now)
            }
        }
    }

    private fun prepTargets(targets: Collection<FiatCurrency>): Set<FiatCurrency> {

        // All non-high-liquidity currencies require USD to perform proper conversions.
        // E.g. COP => USD => BTC
        // So if the given list includes any non-high-liquidity currencies,
        // then we need to make sure we append USD to the list.
        val requiresUsd = targets.any { !highLiquidityMarkets.contains(it) }
        return if (requiresUsd) {
            targets.plus(FiatCurrency.USD).toSet()
        } else {
            targets.toSet()
        }
    }

    private suspend fun calculateDelay(
        targets: Set<FiatCurrency>,
        refreshDelay: Duration
    ): Duration {

        val initialized = targets.all { refreshList.containsKey(it) }
        if (!initialized) {
            // Initialize the refreshList with the information from the database.
            val dbValues = ratesFlow.filterNotNull().first()
                .filter { targets.contains(it.fiatCurrency) }
            for (fiatCurrency in targets) {
                val lastRefresh = dbValues.firstOrNull { it.fiatCurrency == fiatCurrency }?.let {
                    Instant.fromEpochMilliseconds(it.timestampMillis)
                } ?: run {
                    Instant.fromEpochMilliseconds(0)
                }
                refreshList[fiatCurrency] = RefreshInfo(
                    lastRefresh = lastRefresh,
                    nextRefresh = lastRefresh + refreshDelay,
                    failCount = 0
                )
            }
        }

        val nextRefresh = targets.mapNotNull { fiatCurrency ->
            refreshList[fiatCurrency]
        }.minByOrNull {
            it.nextRefresh
        }?.nextRefresh

        val now = Clock.System.now()
        return if (nextRefresh == null || nextRefresh <= now) {
            Duration.ZERO
        } else {
            nextRefresh - now
        }
    }

    /**
     * Adds given targets to the publicly visible `refreshFlow`.
     * The UI may use this flow to display a progress/spinner to indicate refresh activity.
     */
    private fun addRefreshTargets(targets: Set<FiatCurrency>) {
        _refreshFlow.update { currentSet ->
            currentSet.plus(targets)
        }
    }

    /**
     * Removes the given targets from the publicly visible `refreshFlow`.
     * The UI may use this flow to display a progress/spinner to indicate refresh activity.
     */
    private fun removeRefreshTargets(targets: Set<FiatCurrency>) {
        _refreshFlow.update { currentSet ->
            currentSet.minus(targets)
        }
    }

    class WrappedException(
        val inner: Exception?,
        val fiatCurrency: FiatCurrency,
        val reason: String
    ) : Exception("$fiatCurrency: $reason: $inner")

    /**
     * Standard routine to refresh a list of currencies for a given API.
     * The given `allTargets` parameter will automatically be filtered,
     * and only the necessary currencies will be updated.
     */
    private suspend fun refresh(
        allTargets: Set<FiatCurrency>,
        api: API,
        forceRefresh: Boolean
    ) {
        // Filter the `allTargets` set to only include:
        // - those in the given api
        // - those that actually need to be refreshed (unless forceRefresh is true)
        val now = Clock.System.now()
        val targets = allTargets.filter { fiatCurrency ->
            if (!api.fiatCurrencies.contains(fiatCurrency)) {
                false
            } else if (forceRefresh) {
                true
            } else {
                // Only include those that need to be refreshed
                refreshList[fiatCurrency]?.let {
                    val result: Boolean = it.nextRefresh <= now // < Android studio bug
                    result
                } ?: true
            }
        }.toSet()

        if (targets.isEmpty()) {
            return
        } else {
            log.debug { "fetching ${targets.size} exchange rate(s) from ${api.name}" }
            addRefreshTargets(targets)
        }

        val fetchedRates = when (api) {
            blockchainInfoAPI -> fetchFromBlockchainInfo(targets)
            coinbaseAPI -> fetchFromCoinbase(targets)
            coindeskAPI -> fetchFromCoinDesk(targets)
            bluelyticsAPI -> fetchFromBluelytics(targets)
            yadioAPI -> fetchFromYadio(targets)
            else -> listOf()
        }
        if (fetchedRates.isNotEmpty()) {
            appDb.saveExchangeRates(fetchedRates)
            log.debug { "successfully refreshed ${fetchedRates.size} exchange rate(s) from ${api.name}" }
        }

        val fetchedCurrencies = fetchedRates.map { it.fiatCurrency }.toSet()
        val failedCurrencies = targets.minus(fetchedCurrencies)
        if (failedCurrencies.isNotEmpty()) {
            log.info { "failed to refresh ${failedCurrencies.size} exchange rate(s) from ${api.name}: ${failedCurrencies.joinToString(",").take(30)}" }
        }

        // Update all the corresponding values in `refreshList`
        updateRefreshList(
            api = api,
            attempted = targets,
            refreshed = fetchedCurrencies
        )
        removeRefreshTargets(targets)
    }

    private suspend fun fetchFromBlockchainInfo(
        targets: Set<FiatCurrency>
    ): List<ExchangeRate> {

        val httpResponse: HttpResponse? = try {
            httpClient.get(urlString = "https://blockchain.info/ticker")
        } catch (e: Exception) {
            log.error { "failed to get exchange rates from blockchain.info: ${e.message}" }
            null
        }
        val parsedResponse: BlockchainInfoResponse? = httpResponse?.let {
            try {
                json.decodeFromString<BlockchainInfoResponse>(it.bodyAsText())
            } catch (e: Exception) {
                log.error { "failed to read exchange rates response from blockchain.info: ${e.message}" }
                null
            }
        }

        val timestampMillis = Clock.System.now().toEpochMilliseconds()
        val fetchedRates: List<ExchangeRate> = parsedResponse?.let {
            targets.mapNotNull { fiatCurrency ->
                parsedResponse[fiatCurrency.name]?.let { priceObject ->
                    ExchangeRate.BitcoinPriceRate(
                        fiatCurrency = fiatCurrency,
                        price = priceObject.last,
                        source = "blockchain.info",
                        timestampMillis = timestampMillis,
                    )
                }
            }
        } ?: listOf()

        return fetchedRates
    }

    private suspend fun fetchFromCoinbase(
        targets: Set<FiatCurrency>
    ): List<ExchangeRate> {

        val httpResponse: HttpResponse? = try {
            httpClient.get(urlString = "https://api.coinbase.com/v2/exchange-rates?currency=USD")
        } catch (e: Exception) {
            log.error { "failed to get exchange rates from api.coinbase.com: $e" }
            null
        }
        val parsedResponse: CoinbaseResponse? = httpResponse?.let {
            try {
                json.decodeFromString<CoinbaseResponse>(it.bodyAsText())
            } catch (e: Exception) {
                log.error { "failed to get exchange rates response from api.coinbase.com: $e" }
                null
            }
        }

        val timestampMillis = Clock.System.now().toEpochMilliseconds()
        val fetchedRates: List<ExchangeRate> = parsedResponse?.let {
            targets.mapNotNull { fiatCurrency ->
                parsedResponse.data.rates[fiatCurrency.name]?.let { valueAsString ->
                    valueAsString.toDoubleOrNull()?.let { valueAsDouble ->
                        ExchangeRate.UsdPriceRate(
                            fiatCurrency = fiatCurrency,
                            price = valueAsDouble,
                            source = "coinbase.com",
                            timestampMillis = timestampMillis
                        )
                    }
                }
            }
        } ?: listOf()

        return fetchedRates
    }

    private suspend fun fetchFromCoinDesk(
        targets: Set<FiatCurrency>
    ): List<ExchangeRate> {

        // Performance notes:
        //
        // The CoinDesk API forces us to fetch each rate individually. E.g.:
        // https://api.coindesk.com/v1/bpi/currentprice/COP.json
        //
        // This was a big problem when we were using CoinDesk for ~146 fiat currencies.
        // It's much less of a problem now that we're only using it for 7.
        //
        // In the past, we explored this problem in depth, since it was a bigger issue.
        // Here's the historical notes:
        //
        // > - If we use zero concurrency, by fetching each URL one-at-a-time,
        // >   and writing to the database after each one,
        // >   then the whole process takes around 46 seconds.
        // >
        // > - If we maximize concurrency by fetching all of them,
        // >   and then writing to the database once afterwards,
        // >   then the whole process takes around 10 seconds.
        // >
        // > This should be much faster, because:
        // > - the HttpClient should use 1 or 2 connections in total
        // > - it should keep those connections open
        // > - it should send multiple requests over the same connection
        // >
        // > It probably does this correctly on Android.
        // > But on iOS there was a BUG, and it opened a different connection for each request.
        // > So that's 146 TCP handshakes, and 146 TLS handshakes...
        // > This bug was reported:
        // > - https://youtrack.jetbrains.com/issue/KTOR-3362
        // >
        // > If that bug is fixed, the process will take around 800 milliseconds on iOS.
        //
        val http = HttpClient {
            engine {
                threadsCount = 1
                pipelining = true
            }
        }

        targets.map { fiatCurrency ->
            val fiat = fiatCurrency.name // e.g.: "AUD"
            async {
                val httpResponse: HttpResponse = try {
                    http.get("https://api.coindesk.com/v1/bpi/currentprice/$fiat.json")
                } catch (e: Exception) {
                    throw WrappedException(e, fiatCurrency, "failed to get exchange rates from api.coindesk.com")
                }
                val parsedResponse: CoinDeskResponse = try {
                    json.decodeFromString(httpResponse.body())
                } catch (e: Exception) {
                    throw WrappedException(e, fiatCurrency, "failed to read exchange rates response from api.coindesk.com")
                }
                val usdRate = parsedResponse.bpi["USD"]
                val fiatRate = parsedResponse.bpi[fiat]
                if (usdRate == null || fiatRate == null) {
                    throw WrappedException(null, fiatCurrency, "failed to extract USD or FIAT price from api.coindesk.com")
                }

                // Example (fiat = "ILS"):
                // usdRate.rate = 62,980.0572
                // fiatRate.rate = 202,056.4047
                // 202,056.4047 / 62,980.0572 = 3.2082
                // This means that:
                // - 1 USD = 3.2082 ILS
                // - 1 ILS = 62,980.0572 * 3.2082 BTC = 202,056.4047 BTC
                val price = fiatRate.rate / usdRate.rate
                ExchangeRate.UsdPriceRate(
                    fiatCurrency = fiatCurrency,
                    price = price,
                    source = "coindesk.com",
                    timestampMillis = Clock.System.now().toEpochMilliseconds()
                )
            }
        }.map { request -> // Deferred<ExchangeRate.UsdPriceRate>
            try {
                Result.success(request.await())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }.let { results -> // List<Result<ExchangeRate.UsdPriceRate>>

        //  val fetchedRates = results.mapNotNull { it.getOrNull() }
        //  val failedRates = results.mapNotNull { it.exceptionOrNull() }

            return results.mapNotNull { it.getOrNull() }
        }
    }

    private suspend fun fetchFromBluelytics(
        targets: Set<FiatCurrency>
    ): List<ExchangeRate> {

        val httpResponse: HttpResponse? = try {
            httpClient.get(urlString = "https://api.bluelytics.com.ar/v2/latest")
        } catch (e: Exception) {
            log.error { "failed to get exchange rates from api.bluelytics.com.ar: $e" }
            null
        }
        val parsedResponse: BluelyticsResponse? = httpResponse?.let {
            try {
                json.decodeFromString<BluelyticsResponse>(httpResponse.bodyAsText())
            } catch (e: Exception) {
                log.error { "failed to get exchange rates response from api.bluelytics.com.ar: $e" }
                null
            }
        }

        val timestampMillis = Clock.System.now().toEpochMilliseconds()
        val fetchedRates: List<ExchangeRate> = parsedResponse?.let {
            targets.filter { it == FiatCurrency.ARS_BM }.map {
                ExchangeRate.UsdPriceRate(
                    fiatCurrency = FiatCurrency.ARS_BM,
                    price = parsedResponse.blue.value_avg,
                    source = "bluelytics.com.ar",
                    timestampMillis = timestampMillis
                )
            }
        } ?: listOf()

        return fetchedRates
    }

    private suspend fun fetchFromYadio(
        targets: Set<FiatCurrency>
    ): List<ExchangeRate> {

        val httpResponse: HttpResponse? = try {
            httpClient.get(urlString = "https://api.yadio.io/exrates/USD")
        } catch (e: Exception) {
            log.error { "failed to get exchange rates from api.yadio.io: $e" }
            null
        }
        val parsedResponse: YadioResponse? = httpResponse?.let {
            try {
                json.decodeFromString<YadioResponse>(httpResponse.bodyAsText())
            } catch (e: Exception) {
                log.error { "failed to get exchange rates response from api.yadio.io: $e" }
                null
            }
        }

        val timestampMillis = Clock.System.now().toEpochMilliseconds()
        val fetchedRates: List<ExchangeRate> = parsedResponse?.let {
            targets.mapNotNull { fiatCurrency ->
                val name = fiatCurrency.name.take(3)
                parsedResponse.usdRates[name]?.let { valueAsDouble ->
                    ExchangeRate.UsdPriceRate(
                        fiatCurrency = fiatCurrency,
                        price = valueAsDouble,
                        source = "yadio.io",
                        timestampMillis = timestampMillis
                    )
                }
            }
        } ?: listOf()

        return fetchedRates
    }

    /**
     * For debugging purposes.
     *
     * It was discovered that Coinbase's currency rate for "ERN" wasn't accurate.
     * It turns out that "ERN" didn't mean "Eritrean Nakfa", but instead refers to an altcoin.
     * How did this happen ?
     * It might be because the USA is sanctioning the country.
     * After all, the other missing currencies from coinbase are sanctioned countries.
     * However, it might also be an accident.
     * And if it was an accident, this would imply that anytime an altcoin name happens
     * to conflict with an existing currency name, the altcoin might take precedence.
     * I think this is unlikely.
     * But this function is a "quick-and-dirty" way to scan the list for suspicious exchange rates.
     */
    private suspend fun compareCoinbaseVsCoindesk() {

        val fiatCurrencies = FiatCurrency.values.filter {
            it != FiatCurrency.USD &&
            it != FiatCurrency.EUR &&
            it != FiatCurrency.ARS_BM
        }.toSet()

        val coinbaseRates = fetchFromCoinbase(fiatCurrencies)
        val coindeskRates = fetchFromCoinDesk(fiatCurrencies)

        val coinbaseMap = coinbaseRates.associateBy { it.fiatCurrency }
        val coindeskMap = coindeskRates.associateBy { it.fiatCurrency }

        for (fiatCurrency in fiatCurrencies) {
            val coinbaseRate = coinbaseMap[fiatCurrency]
            val coindeskRate = coindeskMap[fiatCurrency]

            val coinbaseValue = when (coinbaseRate) {
                is ExchangeRate.UsdPriceRate -> coinbaseRate.price.toString()
                else -> "null"
            }
            val coindeskValue = when (coindeskRate) {
                is ExchangeRate.UsdPriceRate -> coindeskRate.price.toString()
                else -> "null"
            }

            log.debug { "${fiatCurrency.name}: coinbase($coinbaseValue), coindesk($coindeskValue)" }
        }
    }
}
