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

import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.error
import fr.acinq.phoenix.data.WalletContext
import fr.acinq.phoenix.data.WalletNotice
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.utils.extensions.phoenixName
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Manages HTTP calls to the LSP endpoint that fetch contextual information about the wallet or
 * the service. For example, check what is the latest available version of Phoenix, or if the
 * has an important notice that the user should be aware of.
 */
class WalletContextManager(
    loggerFactory: LoggerFactory
) {
    val log = loggerFactory.newLogger(this::class)
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val jsonFormat = Json { ignoreUnknownKeys = true }
    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) { json(jsonFormat) }
        }
    }

    private var walletNoticePollingJob: Job? = null
    private var walletContextPollingJob: Job? = null

    /** Wallet notices are short messages provided by the LSP and displayed in the Home screen. */
    private val _walletNotice = MutableStateFlow<WalletNotice?>(null)
    val walletNotice = _walletNotice.asStateFlow()

    /**
     * The [WalletContext] contains some information about the context in which the wallet operates. A lot of the
     * information in the context json was used by the legacy android app, and is now obsolete and ignored.
     */
    private val _walletContext = MutableStateFlow<WalletContext?>(null)
    val walletContext = _walletContext.asStateFlow()

    fun startJobs() {
        startWalletContextJob()
        startWalletNoticeJob()
    }

    fun stopJobs() {
        walletContextPollingJob?.cancel()
        walletNoticePollingJob?.cancel()
    }

    /** Starts a coroutine that continuously polls the wallet-context endpoint. The coroutine is tracked in [walletContextPollingJob]. */
    private fun startWalletContextJob() {
        walletContextPollingJob = scope.launch {
            var pause = 30.seconds
            while (isActive) {
                pause = (pause * 2).coerceAtMost(10.minutes)
                fetchWalletContext()?.let {
                    _walletContext.value = it
                    pause = 180.minutes
                }
                delay(pause)
            }
        }
    }

    /** Fetches and parses the wallet context from the wallet context remote endpoint. Returns null if resource is unavailable or unreadable. */
    private suspend fun fetchWalletContext(): WalletContext? {
        return try {
            httpClient.get("https://acinq.co/phoenix/walletcontext.json")
        } catch (_: Exception) {
            try {
                httpClient.get("https://s3.eu-west-1.amazonaws.com/acinq.co/phoenix/walletcontext.json")
            } catch (e: Exception) {
                log.error { "failed to fetch wallet context: ${e.message?.take(200)}" }
                null
            }
        }?.let { response ->
            if (response.status.isSuccess()) {
                jsonFormat.decodeFromString<JsonObject>(response.bodyAsText(Charsets.UTF_8))
            } else {
                log.error { "wallet-context returned status=${response.status}" }
                null
            }
        }?.let { json ->
            log.debug { "fetched wallet-context=$json" }
            try {
                val base = json[NodeParamsManager.chain.phoenixName]!!
                val isMempoolFull = base.jsonObject["mempool"]?.jsonObject?.get("v1")?.jsonObject?.get("high_usage")?.jsonPrimitive?.booleanOrNull
                val androidLatestVersion = base.jsonObject["version"]?.jsonPrimitive?.intOrNull
                val androidLatestCriticalVersion = base.jsonObject["latest_critical_version"]?.jsonPrimitive?.intOrNull
                WalletContext(
                    isMempoolFull = isMempoolFull ?: false,
                    androidLatestVersion = androidLatestVersion ?: 0,
                    androidLatestCriticalVersion = androidLatestCriticalVersion ?: 0,
                )
            } catch (e: Exception) {
                log.error { "could not parse wallet-context response: ${e.message}" }
                null
            }
        }
    }

    /** Starts a coroutine that continuously polls the wallet-notice endpoint. The coroutine is tracked in [walletNoticePollingJob]. */
    private fun startWalletNoticeJob() {
        walletNoticePollingJob = scope.launch {
            var pause = 30.seconds
            while (isActive) {
                pause = (pause * 2).coerceAtMost(10.minutes)
                fetchWalletNotice()?.let {
                    _walletNotice.value = it
                    pause = 180.minutes
                }
                delay(pause)
            }
        }
    }

    /** Fetches and parses the wallet context from the wallet context remote endpoint. Returns null if resource is unavailable or unreadable. */
    private suspend fun fetchWalletNotice(): WalletNotice? {
        return try {
            httpClient.get("https://acinq.co/phoenix/walletnotice.json")
        } catch (_: Exception) {
            try {
                httpClient.get("https://s3.eu-west-1.amazonaws.com/acinq.co/phoenix/walletnotice.json")
            } catch (_: Exception) {
                null
            }
        }?.let { response ->
            try {
                if (response.status.isSuccess()) {
                    val json = jsonFormat.decodeFromString<JsonObject>(response.bodyAsText())
                    log.debug { "fetched wallet-notice=$json" }
                    val notice = json["notice"]!!
                    val message = notice.jsonObject["message"]!!.jsonPrimitive.content
                    val index = notice.jsonObject["index"]!!.jsonPrimitive.int
                    WalletNotice(message = message, index = index)
                } else {
                    null
                }
            } catch (e: Exception) {
                log.debug { "failed to read wallet-notice response: ${e.message}" }
                null
            }
        }
    }
}