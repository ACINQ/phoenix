package fr.acinq.phoenix.managers

import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
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

    val ratesFlow: Flow<List<BitcoinPriceRate>> = appDb.listBitcoinRates()
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
            refreshFromBitso()
            yield()
            delay(Duration.minutes(20))
        }
    }

    private suspend fun refreshFromBlockchainInfo() = try {
        log.info { "fetching bitcoin prices from blockchain.info" }
        httpClient.get<Map<String, BlockchainInfoPriceObject>>("https://blockchain.info/ticker")
            .mapNotNull {
                FiatCurrency.valueOfOrNull(it.key)?.let { fiatCurrency ->
                    BitcoinPriceRate(
                        fiatCurrency = fiatCurrency,
                        price = it.value.last,
                        source = "blockchain.info",
                        timestampMillis = Clock.System.now().toEpochMilliseconds(),
                    )
                } ?: run {
                    log.info { "Blockchain.info has new currency: ${it.key}" }
                    null
                }
            }
            .forEach { appDb.saveBitcoinRate(it) }
        log.info { "successfully refreshed bitcoin prices from blockchain.info" }
    } catch (e: Exception) {
        log.error(e) { "failed to refresh bitcoin price rates from blockchain.info ticker: $e" }
    }

    private suspend fun refreshFromBitso() = try {
        log.info { "fetching bitcoin prices from bitso.com" }
        httpClient.get<MxnApiResponse>("https://api.bitso.com/v3/ticker/?book=btc_mxn")
            .takeIf { it.success }
            ?.let {
                BitcoinPriceRate(
                    fiatCurrency = FiatCurrency.MXN,
                    price = it.payload.last,
                    source = "bitso.com",
                    timestampMillis = Clock.System.now().toEpochMilliseconds(),
                )
            }?.let { appDb.saveBitcoinRate(it) }
    } catch (e: Exception) {
        log.error(e) { "failed to refresh MXN bitcoin price rates from bitso.com ticker" }
    }
}
