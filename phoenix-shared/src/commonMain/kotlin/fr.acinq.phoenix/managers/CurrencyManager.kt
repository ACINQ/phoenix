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

    val ratesFlow: Flow<List<ExchangeRate>> = appDb.listBitcoinRates()
    private var priceRatesPollingJob: Job? = null

    fun start() {
        priceRatesPollingJob = startRatesPollingJob()
    }

    fun stop() {
        launch { priceRatesPollingJob?.cancelAndJoin() }
    }

    private fun startRatesPollingJob() = launch {
        launch {
            // This Job refreshes the BitcoinPriceRates from the blockchain.info API.
            // Since these rates are volatile, we refresh them often.
            val standardDelay = Duration.minutes(20)
            val initialDelay = ratesFlow.filterNotNull().first()
                .filterIsInstance<ExchangeRate.BitcoinPriceRate>()
                .maxByOrNull { it.timestampMillis }
                ?.let { initialDelay(it.timestampMillis, standardDelay) }
            if (initialDelay != null && initialDelay > Duration.ZERO) {
                // We have fresh data from last app launch
                log.info { "BitcoinPriceRate's are fresh - initialDelay: $initialDelay" }
                delay(initialDelay)
            }
            while (isActive) {
                val success = refreshFromBlockchainInfo()
                if (success) {
                    delay(standardDelay)
                } else {
                    delay(standardDelay / 4)
                }
            }
        }
        launch {
            // This Job refreshes the UsdPriceRates from the coindesk.com API.
            // Since these rates are updated at most once per hour (according to coindesk),
            // it doesn't make sense to update them more often than that.
            val standardDelay = Duration.minutes(60)
            val initialDelay = ratesFlow.filterNotNull().first()
                .filterIsInstance<ExchangeRate.UsdPriceRate>()
                .maxByOrNull { it.timestampMillis }
                ?.let { initialDelay(it.timestampMillis, standardDelay) }
            if (initialDelay != null && initialDelay > Duration.ZERO) {
                // We have fresh data from last app launch
                log.info { "UsdPriceRate's are fresh - initialDelay: $initialDelay" }
                delay(initialDelay)
            }
            while (isActive) {
                val success = refreshFromCoinDesk()
                if (success) {
                    delay(standardDelay)
                } else {
                    delay(standardDelay / 4)
                }
            }
        }
    }

    /**
     * Utility function:
     * After launching the app, we may not need to immediately refresh the exchange rates.
     * For example, if the user quits & immediately relaunches Phoenix,
     * then the rates are still fresh.
     */
    private fun initialDelay(timestampMillis: Long, standardDelay: Duration): Duration {
        // The given `timestampMillis` parameter comes from the
        // latest associated ExchangeRate in the database.
        val latest = Instant.fromEpochMilliseconds(timestampMillis)
        // The typical delay would be after the given standardDelay.
        val afterDelay = latest + standardDelay
        val now = Clock.System.now()
        return if (afterDelay <= now) {
            // No delay - refresh exchange rates immediately
            Duration.ZERO
        } else {
            // Existing rates are fresh - add delay (with max for safety)
            val delay = afterDelay - now
            if (delay < standardDelay) delay else standardDelay
        }
    }

    private suspend fun refreshFromBlockchainInfo(): Boolean = try {
        log.info { "fetching bitcoin prices from blockchain.info" }
        httpClient.get<Map<String, BlockchainInfoPriceObject>>("https://blockchain.info/ticker")
            .mapNotNull {
                FiatCurrency.valueOfOrNull(it.key)?.let { fiatCurrency ->
                    if (fiatCurrency == FiatCurrency.USD || fiatCurrency == FiatCurrency.EUR) {
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
            .let { fetchedRates ->
                appDb.saveExchangeRates(fetchedRates)
            }
        log.info { "successfully refreshed bitcoin prices from blockchain.info" }
        true
    } catch (e: Exception) {
        log.error(e) { "failed to refresh bitcoin price rates from blockchain.info ticker: $e" }
        false
    }

    private suspend fun refreshFromCoinDesk(): Boolean {
        log.info { "fetching exchange rates from coindesk.com" }
        // Performance notes:
        // If we use zero concurrency, by fetching each URL one-at-a-time,
        // and writing to the database after each one,
        // then the entire process takes around 46 seconds !
        // That's an average wait time of 23 seconds before a given fiat currency is ready.
        // We're attempting to maximize concurrency by fetching all,
        // and hopefully using http pipelining to speed up the process.
        // The end result is that the process takes around 10 seconds.
        val http = HttpClient { engine {
            threadsCount = 1
            pipelining = true
        }}
        val json = Json {
            ignoreUnknownKeys = true
        }
        val fiatCurrencies = FiatCurrency.values.filter {
            it != FiatCurrency.USD && it != FiatCurrency.EUR
        }
        return fiatCurrencies.map { fiatCurrency ->
            val fiat = fiatCurrency.name // e.g.: "AUD"
            async {
                val response = try {
                    http.get<HttpResponse>(
                        urlString = "https://api.coindesk.com/v1/bpi/currentprice/$fiat.json"
                    )
                } catch (e: Exception) {
                    log.error { "failed to fetch $fiat price from api.coindesk.com: $e" }
                    return@async null
                }
                val result = try {
                    json.decodeFromString<CoinDeskResponse>(response.receive())
                } catch (e: Exception) {
                    log.error { "failed to parse $fiat price from api.coindesk.com: $e" }
                    return@async null
                }
                val usdRate = result.bpi["USD"]
                val fiatRate = result.bpi[fiat]
                if (usdRate == null || fiatRate == null) {
                    log.error { "failed to extract USD or $fiat price from api.coindesk.com" }
                    return@async null
                }
                // log.debug { "$fiat = ${fiatRate.rate}" }
                // Example (fiat = "ILS"):
                // usdRate.rate = 62,980.0572
                // fiatRate.rate = 202,056.4047
                // 202,056.4047 / 62,980.0572 = 3.2082
                // This means that:
                // - 1 USD = 3.2082 ILS
                // - 1 ILS = 62,980.0572 * 2.2082 BTC = 202,056.4047 BTC
                val price = fiatRate.rate / usdRate.rate
                ExchangeRate.UsdPriceRate(
                    fiatCurrency = fiatCurrency,
                    price = price,
                    source = "coindesk.com",
                    timestampMillis = Clock.System.now().toEpochMilliseconds()
                )
            }
        }.mapNotNull { request ->
            try {
                request.await()
            } catch (e: Exception) {
                null
            }
        }.let { fetchedRates ->
            try {
                appDb.saveExchangeRates(fetchedRates)
                log.info { "successfully refreshed exchange rates from coindesk.com" }
                // We consider the fetch to be successful if we fetched at least 2/3 of the list
                fetchedRates.size >= (fiatCurrencies.size * 2 / 3)
            } catch (e: Exception) {
                log.error(e) { "failed to refresh fiat exchange rates: $e" }
                false
            }
        }
    }
}
