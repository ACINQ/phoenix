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

package fr.acinq.phoenix.managers.global

import fr.acinq.bitcoin.Chain
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.data.MempoolFeerate
import fr.acinq.phoenix.managers.NodeParamsManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Fetches a feerate estimation from an external provider (mempool.space).
 * If no data can be fetched from the service, the wallet should default to the peer-provided funding feerate.
 */
class FeerateManager(
    loggerFactory: LoggerFactory,
) {
    val log = loggerFactory.newLogger(this::class)
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val jsonFormat = Json { ignoreUnknownKeys = true }
    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) { json(jsonFormat) }
        }
    }

    private var mempoolFeerateJob: Job? = null
    private val _mempoolFeerate by lazy { MutableStateFlow<MempoolFeerate?>(null) }
    val mempoolFeerate by lazy { _mempoolFeerate.asStateFlow() }

    fun stopMonitoringFeerate() {
        mempoolFeerateJob?.cancel()
    }

    /**  Polls an HTTP endpoint every X seconds to get an estimation of the mempool feerate. */
    fun startMonitoringFeerate() {
        mempoolFeerateJob = scope.launch {
            while (isActive) {
                try {
                    log.debug { "fetching mempool.space feerate" }
                    // FIXME: use our own endpoint
                    val response = httpClient.get(
                        // TODO: after switching to testnet4, consider using the Mainnet endpoint even on Testnet
                        if (NodeParamsManager.chain is Chain.Mainnet) {
                            "https://mempool.space/api/v1/fees/recommended"
                        } else {
                            "https://mempool.space/testnet/api/v1/fees/recommended"
                        }
                    )
                    if (response.status.isSuccess()) {
                        val json = jsonFormat.decodeFromString<JsonObject>(response.bodyAsText(Charsets.UTF_8))
                        log.debug { "mempool.space feerate endpoint returned json=$json" }
                        val feerate = MempoolFeerate(
                            fastest = FeeratePerByte(json["fastestFee"]!!.jsonPrimitive.long.sat),
                            halfHour = FeeratePerByte(json["halfHourFee"]!!.jsonPrimitive.long.sat),
                            hour = FeeratePerByte(json["hourFee"]!!.jsonPrimitive.long.sat),
                            economy = FeeratePerByte(json["economyFee"]!!.jsonPrimitive.long.sat),
                            minimum = FeeratePerByte(json["minimumFee"]!!.jsonPrimitive.long.sat),
                            timestamp = currentTimestampMillis(),
                        )
                        _mempoolFeerate.value = feerate
                    }
                } catch (e: Exception) {
                    log.error { "could not fetch/read data from mempool.space feerate endpoint: ${e.message}" }
                } finally {
                    delay(10 * 60 * 1_000) // pause for 10 min
                }
            }
        }
    }
}