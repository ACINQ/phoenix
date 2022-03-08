package fr.acinq.phoenix.managers

import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Notes:
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

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class CurrencyManager(
    loggerFactory: LoggerFactory,
    private val configurationManager: AppConfigurationManager,
    private val appDb: SqliteAppDb,
    private val httpClient: HttpClient
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        configurationManager = business.appConfigurationManager,
        appDb = business.appDb,
        httpClient = business.httpClient
    )

    private val log = newLogger(loggerFactory)

    // List of fiat currencies where we directly fetch the FIAT/BTC exchange rate.
    // See notes at top of file for discussion.
    private val highLiquidityMarkets = setOf(FiatCurrency.USD, FiatCurrency.EUR)

    // We use a number of different API's to fetch all the data we need.
    interface API {
        val refreshDelay: Duration
        val fiatCurrencies: Set<FiatCurrency>
    }

    // The blockchain.info API is used to refresh the BitcoinPriceRates.
    // Since bitcoin prices are volatile, we refresh them often.
    private val blockchainInfoAPI = object: API {
        override val refreshDelay = Duration.minutes(20)
        override val fiatCurrencies = FiatCurrency.values.filter {
            highLiquidityMarkets.contains(it)
        }.toSet()
    }

    // The coindesk API is used to refersh the UsdPriceRates.
    // Since fiat prices are less volatile, we refresh them less often.
    // Also, the source only refreshes the values once per hour.
    private val coindeskAPI = object: API {
        override val refreshDelay = Duration.minutes(60)
        override val fiatCurrencies = FiatCurrency.values.filter {
            !highLiquidityMarkets.contains(it) && it != FiatCurrency.ARS_BM
        }.toSet()
    }

    // The bluelytics API is used to fetch the "blue market" price for the Argentine Peso.
    // ARS => government controlled exchange rate
    // ARS_BM => free market exchange rate
    private val bluelyticsAPI = object: API {
        override val refreshDelay = Duration.minutes(120)
        override val fiatCurrencies = setOf(FiatCurrency.ARS_BM)
    }

    // Public consumable flow that includes the most recent exchange rates
    val ratesFlow: Flow<List<ExchangeRate>> = appDb.listBitcoinRates()

    // Utility class useed to track refresh progress on a per-currency basis.
    data class RefreshInfo(
        val lastRefresh: Instant,
        val nextRefresh: Instant,
        val failCount: Int
    ) {
        constructor(): this(
            lastRefresh = Instant.fromEpochMilliseconds(0),
            nextRefresh = Instant.fromEpochMilliseconds(0),
            failCount = 0
        )
        fun fail(now: Instant): RefreshInfo {
            val newFailCount = failCount + 1
            val delay = when (newFailCount) {
                1 -> Duration.seconds(30)
                2 -> Duration.minutes(1)
                3 -> Duration.minutes(5)
                4 -> Duration.minutes(10)
                5 -> Duration.minutes(30)
                6 -> Duration.minutes(60)
                else -> Duration.minutes(120)
            }
            return RefreshInfo(
                lastRefresh = this.lastRefresh,
                nextRefresh = now + delay,
                failCount = newFailCount
            )
        }
    }
    private var refreshList = mutableMapOf<FiatCurrency, RefreshInfo>()
    private var refreshJob: Job? = null

    private val _refreshFlow = MutableStateFlow<Set<FiatCurrency>>(setOf())
    val refreshFlow: StateFlow<Set<FiatCurrency>> = _refreshFlow

    private var networkAccessEnabled = false

    // Used by AppConnectionsDaemon.
    // Invoked when an internet connection is available.
    internal fun enableNetworkAccess() {
        networkAccessEnabled = true
        start()
    }

    // Used by AppConnectionsDaemon.
    // Invoked when there is not an internet connection available.
    internal fun disableNetworkAccess() {
        networkAccessEnabled = false
        stop()
    }

    private fun start() {
        if (networkAccessEnabled && refreshJob == null) {
            refreshJob = startRefreshJob()
        }
    }

    private fun stop() = launch {
        refreshJob?.cancelAndJoin()
        refreshJob = null
    }

    fun refresh(targets: List<FiatCurrency>) = launch {
        stop().join()
        val deferred1 = async {
            refreshFromBlockchainInfo(targets = targets, forceRefresh = true)
        }
        val deferred2 = async {
            refreshFromCoinDesk(targets = targets, forceRefresh = true)
        }
        val deferred3 = async {
            refreshFromBluelytics(targets = targets, forceRefresh = true)
        }
        listOf(deferred1, deferred2, deferred3).awaitAll()
        start()
    }

    private fun startRefreshJob() = launch {
        launch {
            // This Job refreshes the BitcoinPriceRates from the blockchain.info API.
            val api = blockchainInfoAPI
            while (isActive) {
                delay(api).let { nextDelay ->
                    log.debug { "API(blockchain.info): Next BitcoinPriceRate refresh: $nextDelay" }
                    delay(nextDelay)
                }
                refreshFromBlockchainInfo(api.fiatCurrencies, forceRefresh = false)
            }
        }
        launch {
            // This Job refreshes the UsdPriceRates from the coindesk.com API.
            val api = coindeskAPI
            while (isActive) {
                delay(api).let { nextDelay ->
                    log.debug { "API(coindesk): Next UsdPriceRate refresh: $nextDelay" }
                    delay(nextDelay)
                }
                val preferred = configurationManager.preferredFiatCurrencies().value.toSet()
                val remaining = api.fiatCurrencies.filter { !preferred.contains(it) }

                refreshFromCoinDesk(preferred, forceRefresh = false)
                refreshFromCoinDesk(remaining, forceRefresh = false)
            }
        }
        launch {
            // This Job refreshes the UsdPriceRates from the bluelytics.com.ar API.
            val api = bluelyticsAPI
            while (isActive) {
                delay(api).let { nextDelay ->
                    log.debug { "API(bluelytics): Next UsdPriceRate refresh: $nextDelay" }
                    delay(nextDelay)
                }
                refreshFromBluelytics(api.fiatCurrencies, forceRefresh = false)
            }
        }
    }

    // Updates the `refreshList` with fresh RefreshInfo values.
    // Only the `attempted` currencies are updated.
    // The `refreshed` parameter marks those currencies that were successfully refreshed.
    //
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

    private suspend fun delay(api: API): Duration {

        val initialized = api.fiatCurrencies.all { refreshList.containsKey(it) }
        if (!initialized) {
            // Initialize the refreshList with the information from the database.
            val dbValues = ratesFlow.filterNotNull().first()
                .filter { api.fiatCurrencies.contains(it.fiatCurrency) }
            for (fiatCurrency in api.fiatCurrencies) {
                val lastRefresh = dbValues.firstOrNull { it.fiatCurrency == fiatCurrency  }?.let {
                    Instant.fromEpochMilliseconds(it.timestampMillis)
                } ?: run {
                    Instant.fromEpochMilliseconds(0)
                }
                refreshList[fiatCurrency] = RefreshInfo(
                    lastRefresh = lastRefresh,
                    nextRefresh = lastRefresh + api.refreshDelay,
                    failCount = 0
                )
            }
        }

        val nextRefresh = api.fiatCurrencies.mapNotNull { fiatCurrency ->
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

    // Given a list of targets, filters the set to only include:
    // - those in the given api
    // - those that actually need to be refreshed (unless forceRefresh is true)
    //
    private fun filterTargets(
        targets: Collection<FiatCurrency>,
        forceRefresh: Boolean,
        api: API,
        now: Instant = Clock.System.now()
    ): Set<FiatCurrency> {
        return targets.filter { fiatCurrency ->
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
    }

    private fun addRefreshTargets(targets: Set<FiatCurrency>) {
        _refreshFlow.update { currentSet ->
            currentSet.plus(targets)
        }
    }
    private fun removeRefreshTargets(targets: Set<FiatCurrency>) {
        _refreshFlow.update { currentSet ->
            currentSet.minus(targets)
        }
    }

    private suspend fun refreshFromBlockchainInfo(
        targets: Collection<FiatCurrency>,
        forceRefresh: Boolean
    ) {
        val api = blockchainInfoAPI
        val fiatCurrencies = filterTargets(targets, forceRefresh, api)

        if (fiatCurrencies.isEmpty()) {
            return
        } else {
            log.info { "fetching ${fiatCurrencies.size} exchange rate(s) from blockchain.info" }
            addRefreshTargets(fiatCurrencies)
        }

        val fetchedRates = try {
            httpClient.get<Map<String, BlockchainInfoPriceObject>>(
                urlString = "https://blockchain.info/ticker"
            ).mapNotNull {
                FiatCurrency.valueOfOrNull(it.key)?.let { fiatCurrency ->
                    if (api.fiatCurrencies.contains(fiatCurrency)) {
                        ExchangeRate.BitcoinPriceRate(
                            fiatCurrency = fiatCurrency,
                            price = it.value.last,
                            source = "blockchain.info",
                            timestampMillis = Clock.System.now().toEpochMilliseconds(),
                        )
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            log.error { "failed to refresh bitcoin price rates from blockchain.info: $e" }
            null
        }

        fetchedRates?.let {
            if (it.isNotEmpty()) {
                appDb.saveExchangeRates(it)
                log.info { "successfully refreshed bitcoin prices from blockchain.info" }
            } else {
                log.error { "failed to refresh bitcoin price rates from blockchain.info" }
            }
        }

        // Update all the corresponding values in `refreshList`
        updateRefreshList(
            api = api,
            attempted = api.fiatCurrencies,
            refreshed = fetchedRates?.map { it.fiatCurrency } ?: listOf()
        )
        removeRefreshTargets(fiatCurrencies)
    }

    class WrappedException(
        val inner: Exception?,
        val fiatCurrency: FiatCurrency,
        val reason: String
    ): Exception("$fiatCurrency: $reason: $inner")

    private suspend fun refreshFromCoinDesk(
        targets: Collection<FiatCurrency>,
        forceRefresh: Boolean
    ) {
        val api = coindeskAPI
        val fiatCurrencies = filterTargets(targets, forceRefresh, api)

        if (fiatCurrencies.isEmpty()) {
            return
        } else {
            log.info { "fetching ${fiatCurrencies.size} exchange rate(s) from coindesk.com" }
            addRefreshTargets(fiatCurrencies)
        }

        // Performance notes:
        //
        // - If we use zero concurrency, by fetching each URL one-at-a-time,
        //   and writing to the database after each one,
        //   then fetching every fiat currency takes around 46 seconds.
        //
        // - If we maximize concurrency by fetching all,
        //   then the whole process takes around 10 seconds.
        //
        // - Ktor has a bug, and doesn't properly use a shared URLSession:
        //   https://youtrack.jetbrains.com/issue/KTOR-3362
        //   If that bug is fixed, the process will take around 800 milliseconds.
        //
        val http = HttpClient { engine {
            threadsCount = 1
            pipelining = true
        }}
        val json = Json {
            ignoreUnknownKeys = true
        }

        fiatCurrencies.map { fiatCurrency ->
            val fiat = fiatCurrency.name // e.g.: "AUD"
            async {
                val response = try {
                    http.get<HttpResponse>(
                        urlString = "https://api.coindesk.com/v1/bpi/currentprice/$fiat.json"
                    )
                } catch (e: Exception) {
                    throw WrappedException(e, fiatCurrency,
                        "failed to fetch price from api.coindesk.com"
                    )
                }
                val result = try {
                    json.decodeFromString<CoinDeskResponse>(response.receive())
                } catch (e: Exception) {
                    throw WrappedException(e, fiatCurrency,
                        "failed to parse price from api.coindesk.com"
                    )
                }
                val usdRate = result.bpi["USD"]
                val fiatRate = result.bpi[fiat]
                if (usdRate == null || fiatRate == null) {
                    throw WrappedException(null, fiatCurrency,
                        "failed to extract USD or FIAT price from api.coindesk.com"
                    )
                }
                // log.debug { "$fiat = ${fiatRate.rate}" }
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

            val fetchedRates = results.mapNotNull { it.getOrNull() }
            val failedRates = results.mapNotNull { it.exceptionOrNull() }

            if (failedRates.isNotEmpty()) {
                log.error {
                    "Failed to refresh fiat exchange rates for ${failedRates.size} currencies: \n"+
                    " - 0: ${failedRates.first()}"
                }
            }

            if (fetchedRates.isNotEmpty()) {
                appDb.saveExchangeRates(fetchedRates)
                log.info {
                    "successfully refreshed ${fetchedRates.size} exchange rates from coindesk.com"
                }
            }

            // Update all the corresponding values in `refreshList`
            updateRefreshList(
                api = api,
                attempted = fiatCurrencies,
                refreshed = fetchedRates.map { it.fiatCurrency }
            )
            removeRefreshTargets(fiatCurrencies)
        }
    }

    private suspend fun refreshFromBluelytics(
        targets: Collection<FiatCurrency>,
        forceRefresh: Boolean
    ) {
        val api = bluelyticsAPI
        val fiatCurrencies = filterTargets(targets, forceRefresh, api)

        if (fiatCurrencies.isEmpty()) {
            return
        } else {
            log.info { "fetching exchange rate from bluelytics.com.ar" }
            addRefreshTargets(fiatCurrencies)
        }

        val result = try {
            val response = try {
                httpClient.get<HttpResponse>(
                    urlString = "https://api.bluelytics.com.ar/v2/latest"
                )
            } catch (e: Exception) {
                throw WrappedException(e, FiatCurrency.ARS_BM, "failed to get http response")
            }
            try {
                val json = Json {
                    ignoreUnknownKeys = true
                }
                json.decodeFromString<BluelyticsResponse>(response.receive())
            } catch (e: Exception) {
                throw WrappedException(e, FiatCurrency.ARS_BM, "failed to parse json response")
            }
        } catch (e: Exception) {
            log.error { "failed to refresh exchange rates from api.bluelytics.com.ar: $e" }
            null
        }

        val fetchedRates = mutableListOf<ExchangeRate>()
        if (result != null) {
            fetchedRates.add(ExchangeRate.UsdPriceRate(
                fiatCurrency = FiatCurrency.ARS_BM,
                price = result.blue.value_avg,
                source = "bluelytics.com.ar",
                timestampMillis = Clock.System.now().toEpochMilliseconds()
            ))
        }

        if (fetchedRates.isNotEmpty()) {
            appDb.saveExchangeRates(fetchedRates)
            log.info {
                "successfully refreshed exchange rate from bluelytics.com.ar"
            }
        }

        // Update all the corresponding values in `refreshList`
        updateRefreshList(
            api = api,
            attempted = fiatCurrencies,
            refreshed = fetchedRates.map { it.fiatCurrency }
        )
        removeRefreshTargets(fiatCurrencies)
    }
}
