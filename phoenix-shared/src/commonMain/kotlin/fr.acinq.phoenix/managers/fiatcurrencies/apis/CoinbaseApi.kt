/*
 * Copyright 2025 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.managers.fiatcurrencies.apis

import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.error
import fr.acinq.phoenix.data.CoinbaseResponse
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The coinbase API is used to refresh select UsdPriceRates.
 * Since fiat prices are less volatile, we refresh them less often.
 */
class CoinbaseAPI(loggerFactory: LoggerFactory) : ExchangeRateApi {

    val log = loggerFactory.newLogger(this::class)

    override val name = "coinbase"
    override val refreshDelay = 60.minutes
    override val fiatCurrencies = FiatCurrency.Companion.values.filter {
        // bascially, everything except USD, EURO, and special markets
        !ExchangeRateApi.highLiquidityMarkets.contains(it) && !ExchangeRateApi.specialMarkets.contains(it) && !ExchangeRateApi.missingFromCoinbase.contains(it)
    }.toSet()

    override suspend fun fetch(targets: Set<FiatCurrency>): List<ExchangeRate> {
        val httpResponse: HttpResponse? = try {
            ExchangeRateApi.httpClient.get(urlString = "https://api.coinbase.com/v2/exchange-rates?currency=USD")
        } catch (e: Exception) {
            log.error { "failed to get exchange rates from api.coinbase.com: $e" }
            null
        }
        val parsedResponse: CoinbaseResponse? = httpResponse?.let {
            try {
                ExchangeRateApi.json.decodeFromString<CoinbaseResponse>(it.bodyAsText())
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
}