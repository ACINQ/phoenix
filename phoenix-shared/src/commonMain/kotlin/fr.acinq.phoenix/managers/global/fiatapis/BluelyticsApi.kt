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
import fr.acinq.phoenix.data.BluelyticsResponse
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlin.time.Duration.Companion.minutes

/**
 * The bluelytics API is used to fetch the "blue market" price for the Argentine Peso.
 * - ARS => government controlled exchange rate
 * - ARS_BM => free market exchange rate
 */
class BluelyticsAPI(loggerFactory: LoggerFactory) : ExchangeRateApi {

    val log = loggerFactory.newLogger(this::class)

    override val name = "bluelytics"
    override val refreshDelay = 120.minutes
    override val fiatCurrencies = setOf(FiatCurrency.ARS_BM)

    override suspend fun fetch(targets: Set<FiatCurrency>): List<ExchangeRate> {
        val httpResponse: HttpResponse? = try {
            ExchangeRateApi.httpClient.get(urlString = "https://api.bluelytics.com.ar/v2/latest")
        } catch (e: Exception) {
            log.error { "failed to get exchange rates from api.bluelytics.com.ar: $e" }
            null
        }
        val parsedResponse: BluelyticsResponse? = httpResponse?.let {
            try {
                ExchangeRateApi.json.decodeFromString<BluelyticsResponse>(httpResponse.bodyAsText())
            } catch (e: Exception) {
                log.error { "failed to get exchange rates response from api.bluelytics.com.ar: $e" }
                null
            }
        }

        val timestampMillis = currentTimestampMillis()
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
}