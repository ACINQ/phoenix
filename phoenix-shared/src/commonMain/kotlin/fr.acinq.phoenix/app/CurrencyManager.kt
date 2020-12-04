package fr.acinq.phoenix.app

import fr.acinq.phoenix.ctrl.EventBus
import fr.acinq.phoenix.data.BitcoinPriceRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.MxnApiResponse
import fr.acinq.phoenix.data.PriceRate
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import org.kodein.db.DB
import org.kodein.db.execBatch
import org.kodein.db.find
import org.kodein.db.get
import org.kodein.db.key
import org.kodein.db.useModels
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

sealed class CurrencyEvent
data class FiatExchangeRatesUpdated(val rates: List<BitcoinPriceRate>) : CurrencyEvent()

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class CurrencyManager(
    loggerFactory: LoggerFactory,
    private val appDB: DB,
    private val httpClient: HttpClient
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    val events = EventBus<CurrencyEvent>()

    init {
        launchUpdateRates() // Do we need to manage cancellation ?
    }

    fun getBitcoinRates(): List<BitcoinPriceRate> {
        return appDB.find<BitcoinPriceRate>().all().useModels { it.toList() }
    }

    // Returns the exchange rate, if it exists in the database.
    // Otherwise, returns a rate with a negative value.
    fun getBitcoinRate(fiatCurrency: FiatCurrency): BitcoinPriceRate {

        val key = appDB.key<BitcoinPriceRate>(fiatCurrency.name)
        return appDB[key] ?: BitcoinPriceRate(fiatCurrency, -1.0)
    }

    private fun launchUpdateRates() = launch {
        while (isActive) {
            val priceRates = buildMap<String, Double> {
                try {
                    val rates = httpClient.get<Map<String, PriceRate>>("https://blockchain.info/ticker")
                    rates.forEach {
                        put(it.key, it.value.last)
                    }

                    val response = httpClient.get<MxnApiResponse>("https://api.bitso.com/v3/ticker/?book=btc_mxn")
                    if (response.success) put(FiatCurrency.MXN.name, response.payload.last)
                } catch (t: Throwable) {
                    logger.error { "An issue occurred while retrieving exchange rates from API." }
                    logger.error(t)
                }
            }

            val exchangeRates =
                FiatCurrency.values()
                    .filter { it.name in priceRates.keys }
                    .mapNotNull { fiatCurrency ->
                        priceRates[fiatCurrency.name]?.let { priceRate ->
                            BitcoinPriceRate(fiatCurrency, priceRate)
                        }
                    }

            appDB.execBatch {
                logger.verbose { "Saving price rates: $exchangeRates" }
                exchangeRates.forEach {
                    appDB.put(it)
                }
            }

            events.send(FiatExchangeRatesUpdated(exchangeRates))
            yield()
            delay(5.minutes)
        }
    }
}