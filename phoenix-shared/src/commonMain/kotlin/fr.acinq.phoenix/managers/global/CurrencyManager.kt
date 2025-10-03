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
import fr.acinq.lightning.logging.info
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.PreferredFiatCurrencies
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.managers.global.fiatapis.BlockchainInfoApi
import fr.acinq.phoenix.managers.global.fiatapis.BluelyticsAPI
import fr.acinq.phoenix.managers.global.fiatapis.CoinbaseAPI
import fr.acinq.phoenix.managers.global.fiatapis.ExchangeRateApi
import fr.acinq.phoenix.managers.global.fiatapis.YadioAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.collections.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the routines fetching the btc exchange rates. The frontend app must add fiat currencies they
 * wish to observe to the [monitoredCurrencies], and the manager will handle the rest.
 *
 * Architecture Notes:
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
class CurrencyManager(
    loggerFactory: LoggerFactory,
    val appDb: SqliteAppDb,
) {
    val log = loggerFactory.newLogger(this::class)
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val blockchainInfoAPI = BlockchainInfoApi(loggerFactory)
    private val yadioAPI = YadioAPI(loggerFactory)
    private val coinbaseAPI = CoinbaseAPI(loggerFactory)
    private val bluelyticsAPI = BluelyticsAPI(loggerFactory)

    /** Public consumable flow that includes the most recent exchange rates */
    val ratesFlow: StateFlow<List<ExchangeRate>> by lazy {
        appDb.listBitcoinRates().stateIn(
            scope = scope,
            started = SharingStarted.Companion.Eagerly,
            initialValue = listOf()
        )
    }

    private var refreshList = mutableMapOf<FiatCurrency, RefreshInfo>()
    private var autoRefreshJob: Job? = null

    private val _refreshFlow = MutableStateFlow<Set<FiatCurrency>>(setOf())
    val refreshFlow: StateFlow<Set<FiatCurrency>> = _refreshFlow

    private var networkAccessEnabled = false
    private var autoRefreshEnabled = true

    // monitored currencies are grouped by wallet id to easily add/remove currencies from the flow when starting/shutting down wallets
    private val _monitoredCurrencies by lazy { MutableStateFlow<Map<String, Set<FiatCurrency>>>(emptyMap()) }

    // always monitor USD, because it's the basis btc rate used by most currencies
    @OptIn(ExperimentalCoroutinesApi::class)
    val monitoredCurrencies = _monitoredCurrencies.mapLatest {
        (it.values.flatten().toSet() + FiatCurrency.USD).also { log.debug { "monitoring $it" } }
    }.stateIn(scope = scope, started = SharingStarted.Lazily, initialValue = setOf(FiatCurrency.USD))

    /** Wallet id is the hash160 of the wallet's node id */
    fun startMonitoringCurrencies(walletId: String, currencies: PreferredFiatCurrencies) {
        _monitoredCurrencies.value += walletId to currencies.all
    }

    fun stopMonitoringForWallet(walletId: String) {
        _monitoredCurrencies.value -= walletId
    }

    /** Called by AppConnectionsDaemon when internet is available. */
    internal fun enableNetworkAccess() {
        networkAccessEnabled = true
        maybeStartAutoRefresh()
    }

    /** Called by AppConnectionsDaemon when no connection is available. */
    internal fun disableNetworkAccess() {
        networkAccessEnabled = false
        stopAutoRefresh()
    }

    fun enableAutoRefresh() {
        autoRefreshEnabled = true
        maybeStartAutoRefresh()
    }

    fun disableAutoRefresh() {
        autoRefreshEnabled = false
        stopAutoRefresh()
    }

    private fun maybeStartAutoRefresh() {
        if (networkAccessEnabled && autoRefreshEnabled && autoRefreshJob == null) {
            autoRefreshJob = launchAutoRefreshJob()
        }
    }

    private fun stopAutoRefresh() = scope.launch {
        autoRefreshJob?.cancelAndJoin()
        autoRefreshJob = null
    }

    // only used by iOS
    fun refreshAll(targets: List<FiatCurrency>, force: Boolean = true) = scope.launch {
        stopAutoRefresh().join()
        val targetSet = targets.toSet() + FiatCurrency.USD

        val deferred1 = async {
            refresh(targetSet, blockchainInfoAPI, forceRefresh = force)
        }
        val deferred2 = async {
            refresh(targetSet, coinbaseAPI, forceRefresh = force)
        }
        val deferred3 = async {
            refresh(targetSet, bluelyticsAPI, forceRefresh = force)
        }
        val deferred4 = async {
            refresh(targetSet, yadioAPI, forceRefresh = force)
        }
        listOf(deferred1, deferred2, deferred3, deferred4).awaitAll()
        maybeStartAutoRefresh()
    }

    private fun launchAutoRefreshJob() = scope.launch {
        var blockchainInfoJob: Job? = null
        var coinbaseJob: Job? = null
        var bluelyticsJob: Job? = null
        var yadioJob: Job? = null

        monitoredCurrencies.collect { currencies ->
            blockchainInfoJob?.cancel()
            blockchainInfoJob = launchAutoRefreshJob(currencies, blockchainInfoAPI)

            coinbaseJob?.cancel()
            coinbaseJob = launchAutoRefreshJob(currencies, coinbaseAPI)

            bluelyticsJob?.cancel()
            bluelyticsJob = launchAutoRefreshJob(currencies, bluelyticsAPI)

            yadioJob?.cancel()
            yadioJob = launchAutoRefreshJob(currencies, yadioAPI)
        }
    }

    private fun launchAutoRefreshJob(allTargets: Set<FiatCurrency>, api: ExchangeRateApi) = scope.launch {
        val targets = allTargets.filter { api.fiatCurrencies.contains(it) }.toSet()
        if (targets.isEmpty()) {
            log.debug { "API(${api.name}): Nothing to refresh" }
            return@launch
        }

        while (isActive) {
            val nextDelay = calculateDelay(targets, api.refreshDelay)
            log.debug { "API(${api.name}): Next refresh: $nextDelay" }
            delay(nextDelay)
            refresh(targets, api, forceRefresh = false)
        }
    }

    /**
     * Returns a snapshot of the ExchangeRate for the primary FiatCurrency.
     * That is, an instance of OriginalFiat, where:
     * - type => current primary FiatCurrency (via AppConfigurationManager)
     * - price => BitcoinPriceRate.price for FiatCurrency type
     */
    fun calculateOriginalFiat(currency: FiatCurrency): ExchangeRate.BitcoinPriceRate? {
        val rates = ratesFlow.value
        val fiatRate = rates.firstOrNull { it.fiatCurrency == currency } ?: return null

        return when (fiatRate) {
            is ExchangeRate.BitcoinPriceRate -> {
                // We have a direct exchange rate.
                // BitcoinPriceRate.rate => The price of 1 BTC in this currency
                fiatRate
            }
            is ExchangeRate.UsdPriceRate -> {
                // We have an indirect exchange rate.
                // UsdPriceRate.price => The price of 1 US Dollar in this currency
                rates.filterIsInstance<ExchangeRate.BitcoinPriceRate>().firstOrNull {
                    it.fiatCurrency == FiatCurrency.USD
                }?.let { usdRate ->
                    ExchangeRate.BitcoinPriceRate(
                        fiatCurrency = currency,
                        price = usdRate.price * fiatRate.price,
                        source = "${fiatRate.source}/${usdRate.source}",
                        timestampMillis = fiatRate.timestampMillis.coerceAtMost(
                            usdRate.timestampMillis
                        )
                    )
                }
            }
        }
    }

    /**
     * Updates the `refreshList` with fresh RefreshInfo values.
     * Only the `attempted` currencies are updated.
     * The `refreshed` parameter marks those currencies that were successfully refreshed.
     */
    private fun updateRefreshList(
        api: ExchangeRateApi,
        attempted: Collection<FiatCurrency>,
        refreshed: Collection<FiatCurrency>
    ) {
        val refreshedSet = refreshed.toSet()
        val now = Clock.System.now()
        attempted.forEach { fiatCurrency ->
            if (refreshedSet.contains(fiatCurrency)) { // refresh succeeded
                refreshList[fiatCurrency] = RefreshInfo(
                    lastRefresh = now,
                    nextRefresh = now + api.refreshDelay,
                    failCount = 0
                )
            } else { // refresh failed
                val refreshInfo = refreshList[fiatCurrency] ?: RefreshInfo()
                refreshList[fiatCurrency] = refreshInfo.fail(now)
            }
        }
    }

    private suspend fun calculateDelay(
        targets: Set<FiatCurrency>,
        refreshDelay: Duration
    ): Duration {

        val initialized = targets.all { refreshList.containsKey(it) }
        if (!initialized) {
            // Initialize the refreshList with the information from the database.
            val dbValues = ratesFlow.filterNotNull().first()
                .filter { targets.contains(it.fiatCurrency) }
            for (fiatCurrency in targets) {
                val lastRefresh = dbValues.firstOrNull { it.fiatCurrency == fiatCurrency }?.let {
                    Instant.Companion.fromEpochMilliseconds(it.timestampMillis)
                } ?: run {
                    Instant.Companion.fromEpochMilliseconds(0)
                }
                refreshList[fiatCurrency] = RefreshInfo(
                    lastRefresh = lastRefresh,
                    nextRefresh = lastRefresh + refreshDelay,
                    failCount = 0
                )
            }
        }

        val nextRefresh = targets.mapNotNull { fiatCurrency ->
            refreshList[fiatCurrency]
        }.minByOrNull {
            it.nextRefresh
        }?.nextRefresh

        val now = Clock.System.now()
        return if (nextRefresh == null || nextRefresh <= now) {
            Duration.Companion.ZERO
        } else {
            nextRefresh - now
        }
    }

    /**
     * Adds given targets to the publicly visible `refreshFlow`.
     * The UI may use this flow to display a progress/spinner to indicate refresh activity.
     */
    private fun addRefreshTargets(targets: Set<FiatCurrency>) {
        _refreshFlow.update { currentSet ->
            currentSet.plus(targets)
        }
    }

    /**
     * Removes the given targets from the publicly visible `refreshFlow`.
     * The UI may use this flow to display a progress/spinner to indicate refresh activity.
     */
    private fun removeRefreshTargets(targets: Set<FiatCurrency>) {
        _refreshFlow.update { currentSet ->
            currentSet.minus(targets)
        }
    }

    /**
     * Standard routine to refresh a list of currencies for a given API.
     * The given `allTargets` parameter will automatically be filtered,
     * and only the necessary currencies will be updated.
     */
    private suspend fun refresh(
        allTargets: Set<FiatCurrency>,
        api: ExchangeRateApi,
        forceRefresh: Boolean
    ) {
        // Filter the `allTargets` set to only include:
        // - those in the given api
        // - those that actually need to be refreshed (unless forceRefresh is true)
        val now = Clock.System.now()
        val targets = allTargets.filter { fiatCurrency ->
            if (!api.fiatCurrencies.contains(fiatCurrency)) {
                false
            } else if (forceRefresh) {
                true
            } else {
                // Only include those that need to be refreshed
                refreshList[fiatCurrency]?.let {
                    val result: Boolean = it.nextRefresh <= now // < Android studio bug
                    result
                } ?: true
            }
        }.toSet()

        if (targets.isEmpty()) {
            return
        } else {
            log.debug { "fetching ${targets.size} exchange rate(s) from ${api.name}" }
            addRefreshTargets(targets)
        }

        val fetchedRates = api.fetch(targets)

        if (fetchedRates.isNotEmpty()) {
            appDb.saveExchangeRates(fetchedRates)
            log.debug { "successfully refreshed ${fetchedRates.size} exchange rate(s) from ${api.name}" }
        }

        val fetchedCurrencies = fetchedRates.map { it.fiatCurrency }.toSet()
        val failedCurrencies = targets.minus(fetchedCurrencies)
        if (failedCurrencies.isNotEmpty()) {
            log.info { "failed to refresh ${failedCurrencies.size} exchange rate(s) from ${api.name}: ${failedCurrencies.joinToString(",").take(30)}" }
        }

        // Update all the corresponding values in `refreshList`
        updateRefreshList(
            api = api,
            attempted = targets,
            refreshed = fetchedCurrencies
        )
        removeRefreshTargets(targets)
    }

    /** Utility class used to track refresh progress on a per-currency basis. */
    private data class RefreshInfo(
        val lastRefresh: Instant,
        val nextRefresh: Instant,
        val failCount: Int
    ) {
        constructor() : this(
            lastRefresh = Instant.Companion.fromEpochMilliseconds(0),
            nextRefresh = Instant.Companion.fromEpochMilliseconds(0),
            failCount = 0
        )

        fun fail(now: Instant): RefreshInfo {
            val newFailCount = failCount + 1
            val delay = when (newFailCount) {
                1 -> 30.seconds
                2 -> 1.minutes
                3 -> 5.minutes
                4 -> 10.minutes
                5 -> 30.minutes
                6 -> 60.minutes
                else -> 120.minutes
            }
            return RefreshInfo(
                lastRefresh = this.lastRefresh,
                nextRefresh = now + delay,
                failCount = newFailCount
            )
        }
    }
}