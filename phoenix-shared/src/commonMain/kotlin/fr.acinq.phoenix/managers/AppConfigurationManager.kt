package fr.acinq.phoenix.managers

import co.touchlab.kermit.Logger
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.lightning.blockchain.fee.FeeratePerByte
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.utils.loggerExtensions.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlin.math.*
import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AppConfigurationManager(
    private val appDb: SqliteAppDb,
    private val httpClient: HttpClient,
    private val electrumWatcher: ElectrumWatcher,
    private val chain: NodeParams.Chain,
    loggerFactory: Logger
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.newLoggerFactory,
        chain = business.chain,
        appDb = business.appDb,
        httpClient = business.httpClient,
        electrumWatcher = business.electrumWatcher,
    )

    private val logger = loggerFactory.appendingTag("AppConfigurationManager")

    init {
        watchElectrumMessages()
    }

    // Called from AppConnectionsDaemon
    internal fun enableNetworkAccess() {
        startWalletContextJob()
        monitorMempoolFeerate()
    }

    // Called from AppConnectionsDaemon
    internal fun disableNetworkAccess() {
        stopJobs()
    }

    /** Cancels [walletContextPollingJob] and [mempoolFeerateJob]. */
    private fun stopJobs() {
        launch {
            mempoolFeerateJob?.cancelAndJoin()
            walletContextPollingJob?.cancelAndJoin()
        }
    }

    private val _walletContext = MutableStateFlow<WalletContext?>(null)
    val walletContext = _walletContext.asStateFlow()

    /** Track the job that polls the wallet-context endpoint, so that we can cancel/restart it when needed. */
    private var walletContextPollingJob: Job? = null

    /** Starts a coroutine that continuously polls the wallet-context endpoint. The coroutine is tracked in [walletContextPollingJob]. */
    private fun startWalletContextJob() {
        launch {
            walletContextPollingJob = launch {
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
    }

    /** Fetches and parses the wallet context from the wallet context remote endpoint. Returns null if resource is unavailable or unreadable. */
    private suspend fun fetchWalletContext(): WalletContext? {
        return try {
            httpClient.get("https://acinq.co/phoenix/walletcontext.json")
        } catch (e1: Exception) {
            try {
                httpClient.get("https://s3.eu-west-1.amazonaws.com/acinq.co/phoenix/walletcontext.json")
            } catch (e2: Exception) {
                logger.error { "failed to fetch wallet context: ${e2.message?.take(200)}" }
                null
            }
        }?.let { response ->
            if (response.status.isSuccess()) {
                Json.decodeFromString<JsonObject>(response.bodyAsText(Charsets.UTF_8))
            } else {
                logger.error { "wallet-context returned status=${response.status}" }
                null
            }
        }?.let { json ->
            logger.debug { "fetched wallet-context=$json" }
            try {
                val base = json[chain.name.lowercase()]!!
                val isMempoolFull = base.jsonObject["mempool"]?.jsonObject?.get("v1")?.jsonObject?.get("high_usage")?.jsonPrimitive?.booleanOrNull
                val androidLatestVersion = base.jsonObject["version"]?.jsonPrimitive?.intOrNull
                val androidLatestCriticalVersion = base.jsonObject["latest_critical_version"]?.jsonPrimitive?.intOrNull
                WalletContext(
                    isMempoolFull = isMempoolFull ?: false,
                    androidLatestVersion = androidLatestVersion ?: 0,
                    androidLatestCriticalVersion = androidLatestCriticalVersion ?: 0,
                )
            } catch (e: Exception) {
                logger.error { "could not parse wallet-context response: ${e.message}" }
                null
            }
        }
    }

    /**
     * Used by the [PeerManager] to know what parameters to use when starting
     * up the [Peer] connection. If null, the [PeerManager] will wait before
     * instantiating the [Peer].
     */
    private val _startupParams by lazy { MutableStateFlow<StartupParams?>(null) }
    val startupParams: StateFlow<StartupParams?> by lazy { _startupParams }
    internal fun setStartupParams(params: StartupParams) {
        if (_startupParams.value == null) _startupParams.value = params
        if (_isTorEnabled.value == null) _isTorEnabled.value = params.isTorEnabled
    }

    /**
     * Used by the [AppConnectionsDaemon] to know which server to connect to.
     * If null, the daemon will wait for a config to be set.
     */
    private val _electrumConfig by lazy { MutableStateFlow<ElectrumConfig?>(null) }
    val electrumConfig: StateFlow<ElectrumConfig?> by lazy { _electrumConfig }

    /**
     * Use this method to set a server to connect to.
     * If null, will connect to a random server from the hard-coded list.
     */
    fun updateElectrumConfig(server: ServerAddress?) {
        _electrumConfig.value = server?.let {
            if (it.host.endsWith(".onion")) {
                ElectrumConfig.Custom(it.copy(tls = TcpSocket.TLS.DISABLED))
            } else {
                ElectrumConfig.Custom(it)
            }
        } ?: ElectrumConfig.Random
    }

    fun randomElectrumServer() = when (chain) {
        NodeParams.Chain.Mainnet -> mainnetElectrumServers.random()
        NodeParams.Chain.Testnet -> testnetElectrumServers.random()
        NodeParams.Chain.Regtest -> platformElectrumRegtestConf()
    }

    /** The flow containing the electrum header responses messages. */
    private val _electrumMessages by lazy { MutableStateFlow<HeaderSubscriptionResponse?>(null) }
    val electrumMessages: StateFlow<HeaderSubscriptionResponse?> = _electrumMessages

    private fun watchElectrumMessages() = launch {
        electrumWatcher.client.notifications.filterIsInstance<HeaderSubscriptionResponse>().collect {
            _electrumMessages.value = it
        }
    }

    private var mempoolFeerateJob: Job? = null
    private val _mempoolFeerate by lazy { MutableStateFlow<MempoolFeerate?>(null) }
    val mempoolFeerate by lazy { _mempoolFeerate.asStateFlow() }

    /**  Polls an HTTP endpoint every X seconds to get an estimation of the mempool feerate. */
    private fun monitorMempoolFeerate() {
        mempoolFeerateJob = launch {
            while (isActive) {
                try {
                    logger.debug { "fetching mempool.space feerate" }
                    // FIXME: use our own endpoint
                    // always use Mainnet, even on Testnet
                    val response = httpClient.get("https://mempool.space/api/v1/fees/recommended")
                    if (response.status.isSuccess()) {
                        val json = Json.decodeFromString<JsonObject>(response.bodyAsText(Charsets.UTF_8))
                        logger.debug { "mempool.space feerate endpoint returned json=$json" }
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
                    logger.error { "could not fetch/read data from mempool.space feerate endpoint: ${e.message}" }
                } finally {
                    delay(10 * 60 * 1_000) // pause for 10 min
                }
            }
        }
    }

    // Tor configuration
    private val _isTorEnabled = MutableStateFlow<Boolean?>(null)
    val isTorEnabled get(): StateFlow<Boolean?> = _isTorEnabled.asStateFlow()
    fun updateTorUsage(enabled: Boolean): Unit {
        _isTorEnabled.value = enabled
    }

    // Fiat preferences
    data class PreferredFiatCurrencies(
        val primary: FiatCurrency,
        val others: Set<FiatCurrency>
    ) {
        constructor(primary: FiatCurrency, others: List<FiatCurrency>) :
                this(primary = primary, others = others.toSet())

        val all: Set<FiatCurrency>
            get() {
                return if (others.contains(primary)) {
                    others
                } else {
                    others.toMutableSet().apply { add(primary) }
                }
            }
    }

    private val _preferredFiatCurrencies by lazy { MutableStateFlow<PreferredFiatCurrencies?>(null) }
    val preferredFiatCurrencies: StateFlow<PreferredFiatCurrencies?> by lazy { _preferredFiatCurrencies }

    fun updatePreferredFiatCurrencies(current: PreferredFiatCurrencies) {
        _preferredFiatCurrencies.value = current
    }
}
