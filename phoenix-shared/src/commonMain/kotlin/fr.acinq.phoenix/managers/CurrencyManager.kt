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
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

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
        while (isActive) {
            refreshFromBlockchainInfo()
            refreshFromCoinDesk()
            yield()
            delay(Duration.minutes(20))
        }
    }

    private suspend fun refreshFromBlockchainInfo() = try {
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
    } catch (e: Exception) {
        log.error(e) { "failed to refresh bitcoin price rates from blockchain.info ticker: $e" }
    }

    private suspend fun refreshFromCoinDesk() {
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
        FiatCurrency.values.filter {
            it != FiatCurrency.USD && it != FiatCurrency.EUR
        }.map { fiatCurrency ->
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
                    Json {
                        ignoreUnknownKeys = true
                    }.decodeFromString<CoinDeskResponse>(response.receive())
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
                log.debug { "$fiat = ${fiatRate.rate}" }
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
                    timestampMillis = result.time.updatedISO.toEpochMilliseconds()
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
            } catch (e: Exception) {
                log.error(e) { "failed to refresh fiat exchange rates: $e" }
            }
        }
    }
}
