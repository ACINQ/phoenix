package fr.acinq.phoenix.managers

import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    private val appDb: SqliteAppDb,
    private val httpClient: HttpClient
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        appDb = business.appDb,
        httpClient = business.httpClient
    )

    private val log = newLogger(loggerFactory)

    private val highLiquidityMarkets = setOf(FiatCurrency.USD, FiatCurrency.EUR)

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
            !highLiquidityMarkets.contains(it)
        }.toSet()
    }

    val ratesFlow: Flow<List<ExchangeRate>> = appDb.listBitcoinRates()
    private var refreshJob: Job? = null

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

    fun start() {
        refreshJob = startRefreshJob()
    }

    fun stop() = launch {
        refreshJob?.cancelAndJoin()
    }

    private fun startRefreshJob() = launch {
        launch {
            // This Job refreshes the BitcoinPriceRates from the blockchain.info API.
            val api = blockchainInfoAPI
            while (isActive) {
                delay(api).let { nextDelay ->
                    log.debug { "Next BitcoinPriceRate refresh: $nextDelay" }
                    delay(nextDelay)
                }
                refreshFromBlockchainInfo()
            }
        }
        launch {
            // This Job refreshes the UsdPriceRates from the coindesk.com API.
            val api = coindeskAPI
            while (isActive) {
                delay(api).let { nextDelay ->
                    log.debug { "Next UsdPriceRate refresh: $nextDelay" }
                    delay(nextDelay)
                }
                refreshFromCoinDesk()
            }
        }
    }

    private fun outdatedCurrencies(api: API): List<FiatCurrency> {
        val now = Clock.System.now()
        val results = mutableListOf<FiatCurrency>()
        for (fiatCurrency in api.fiatCurrencies) {
            refreshList[fiatCurrency]?.let {
                if (it.nextRefresh <= now) {
                    results.add(fiatCurrency)
                }
            } ?: run {
                results.add(fiatCurrency)
            }
        }
        return results
    }

    // Updates the `refreshList` with fresh RefreshInfo values.
    // Only the currencies listed in api.fiatCurrencies are updated.
    // The `refreshed` parameter marks those currencies that were successfully refreshed.
    //
    private fun updateRefreshList(api: API, refreshed: Set<FiatCurrency>) {
        val now = Clock.System.now()
        api.fiatCurrencies.forEach { fiatCurrency ->
            if (refreshed.contains(fiatCurrency)) { // refresh succeeded
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
                .filterIsInstance<ExchangeRate.BitcoinPriceRate>()
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

    private suspend fun refreshFromBlockchainInfo() {
        log.info { "fetching bitcoin prices from blockchain.info" }
        val api = blockchainInfoAPI

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
            refreshed = fetchedRates?.map { it.fiatCurrency }?.toSet() ?: setOf()
        )
    }

    class WrappedException(
        val inner: Exception?,
        val fiatCurrency: FiatCurrency,
        val reason: String
    ): Exception("$fiatCurrency: $reason: $inner")

    private suspend fun refreshFromCoinDesk() {
        log.info { "fetching exchange rates from coindesk.com" }
        val api = coindeskAPI

        // Performance notes:
        //
        // - If we use zero concurrency, by fetching each URL one-at-a-time,
        //   and writing to the database after each one,
        //   then the entire process takes around 46 seconds !
        //   That's an average wait time of 23 seconds
        //   before a given fiat currency is ready.
        //
        // - If we maximize concurrency by fetching all,
        //   the end result is that the process takes around 10 seconds.
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

        // We're not going to attempt to fetch ALL the fiatCurrencies.
        // Just those that are outdated.
        val fiatCurrencies = outdatedCurrencies(api)
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
                refreshed = fetchedRates.map { it.fiatCurrency }.toSet()
            )
        }
    }
}
