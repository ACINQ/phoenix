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

import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration


/**
 * We use a number of different APIs to fetch all the data we need.
 * This interface defines the shared format for each API.
 */
interface ExchangeRateApi {
    /** Primarily used for debugging */
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

    suspend fun fetch(targets: Set<FiatCurrency>): List<ExchangeRate>

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        val httpClient by lazy {
            HttpClient {
                install(ContentNegotiation) {
                    json(json)
                }
            }
        }

        /**
         * List of fiat currencies where we directly fetch the FIAT/BTC exchange rate.
         * See "architecture notes" at top of file for discussion.
         */
        val highLiquidityMarkets = setOf(FiatCurrency.USD, FiatCurrency.EUR)

        val specialMarkets = setOf(
            FiatCurrency.ARS_BM, // Argentine Peso (blue market)
            FiatCurrency.CUP_FM, // Cuban Peso (free market)
            FiatCurrency.LBP_BM  // Lebanese Pound (black market)
        )

        val missingFromCoinbase = setOf(
            FiatCurrency.CUP, // Cuban Peso
            FiatCurrency.ERN, // Eritrean Nakfa (exists in response, but refers to ERN altcoin)
            FiatCurrency.IRR, // Iranian Rial
            FiatCurrency.KPW, // North Korean Won
            FiatCurrency.SDG, // Sudanese Pound
            FiatCurrency.SOS, // Somali Shilling
            FiatCurrency.SYP  // Syrian Pound
        )
    }
}