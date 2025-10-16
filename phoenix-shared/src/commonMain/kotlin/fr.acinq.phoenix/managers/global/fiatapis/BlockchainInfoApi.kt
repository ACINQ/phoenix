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

package fr.acinq.phoenix.managers.global.fiatapis

import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.BlockchainInfoResponse
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlin.time.Duration.Companion.minutes

/**
 * The blockchain.info API is used to refresh the BitcoinPriceRates
 * for currencies with "high-liquidity markets" (i.e. USD/EUR).
 * Since bitcoin prices are volatile, we refresh them often.
 */
class BlockchainInfoApi(loggerFactory: LoggerFactory) : ExchangeRateApi {

    val log = loggerFactory.newLogger(this::class)

    override val name = "blockchain.info"
    override val refreshDelay = 20.minutes
    override val fiatCurrencies = ExchangeRateApi.highLiquidityMarkets

    override suspend fun fetch(targets: Set<FiatCurrency>): List<ExchangeRate> {

        val httpResponse: HttpResponse? = try {
            ExchangeRateApi.httpClient.get(urlString = "https://blockchain.info/ticker")
        } catch (e: Exception) {
            log.error { "failed to get exchange rates from blockchain.info: ${e.message}" }
            null
        }
        val parsedResponse: BlockchainInfoResponse? = httpResponse?.let {
            try {
                ExchangeRateApi.json.decodeFromString<BlockchainInfoResponse>(it.bodyAsText())
            } catch (e: Exception) {
                log.error { "failed to read exchange rates response from blockchain.info: ${e.message}" }
                null
            }
        }

        val timestampMillis = currentTimestampMillis()
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
}