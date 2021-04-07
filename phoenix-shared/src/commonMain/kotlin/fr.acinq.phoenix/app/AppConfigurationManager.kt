package fr.acinq.phoenix.app

import fr.acinq.lightning.WalletParams
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.ServerAddress
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.utils.RETRY_DELAY
import fr.acinq.phoenix.utils.increaseDelay
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.math.max
import kotlin.time.*

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class AppConfigurationManager(
    private val appDb: SqliteAppDb,
    private val httpClient: HttpClient,
    private val electrumClient: ElectrumClient,
    private val chain: Chain,
    loggerFactory: LoggerFactory
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    init {
        initWalletParams()
        watchElectrumMessages()
    }

    private val currentWalletParamsVersion = ApiWalletParams.Version.V0
    private val _walletParams = MutableStateFlow<WalletParams?>(null)
    val walletParams: StateFlow<WalletParams?> = _walletParams

    private fun initWalletParams() = launch {
        val (instant, fallbackWalletParams) = appDb.getWalletParamsOrNull(currentWalletParamsVersion)

        val freshness = Clock.System.now() - instant
        logger.info { "local WalletParams loaded, not updated since=$freshness" }

        val timeout =
            if (freshness < 48.hours) 2.seconds
            else max(freshness.inDays.toInt(), 5) * 2.seconds // max=10s

        // TODO are we using TOR? -> increase timeout

        val walletParams = try {
            withTimeout(timeout) {
                fetchAndStoreWalletParams() ?: fallbackWalletParams
            }
        } catch (t: TimeoutCancellationException) {
            logger.warning { "Unable to fetch WalletParams, using fallback values=$fallbackWalletParams" }
            fallbackWalletParams
        }

        // _walletParams can be updated by [updateWalletParamsLoop] before we reach this block.
        // In that case, we don't update from here
        if (_walletParams.value == null) {
            logger.debug { "init WalletParams=$walletParams" }
            _walletParams.value = walletParams
        }

        logger.info { "walletParams=$walletParams" }
    }

    private var updateWalletParamsJob: Job? = null
    public fun startWalletParamsLoop() {
        updateWalletParamsJob = updateWalletParamsLoop()
    }

    public fun stopWalletParamsLoop() {
        launch { updateWalletParamsJob?.cancelAndJoin() }
    }

    @OptIn(ExperimentalTime::class)
    private fun updateWalletParamsLoop() = launch {
        var retryDelay = RETRY_DELAY

        while (isActive) {
            val walletParams = fetchAndStoreWalletParams()
            // _walletParams can be updated just once.
            if (_walletParams.value == null) {
                retryDelay = increaseDelay(retryDelay)
                logger.debug { "update WalletParams=$walletParams" }
                _walletParams.value = walletParams
            } else {
                retryDelay = 60.minutes
            }

            delay(retryDelay)
        }
    }

    private suspend fun fetchAndStoreWalletParams(): WalletParams? {
        return try {
            val rawData = httpClient.get<String>("https://acinq.co/phoenix/walletcontext.json")
            appDb.setWalletParams(currentWalletParamsVersion, rawData)
        } catch (t: Throwable) {
            logger.error(t) { "${t.message}" }
            null
        }
    }

    /** The flow containing the configuration to use for Electrum. If null, we do not know what conf to use. */
    private val _electrumConfig by lazy { MutableStateFlow<ElectrumConfig?>(null) }
    fun electrumConfig(): StateFlow<ElectrumConfig?> = _electrumConfig

    /** Use this method to set a server to connect to. If null, will connect to a random server. */
    fun updateElectrumConfig(server: ServerAddress?) {
        _electrumConfig.value = server?.let { ElectrumConfig.Custom(it) } ?: ElectrumConfig.Random(randomElectrumServer())
    }

    private fun randomElectrumServer() = when (chain) {
        Chain.Mainnet -> electrumMainnetConfigurations.random()
        Chain.Testnet -> electrumTestnetConfigurations.random()
        Chain.Regtest -> platformElectrumRegtestConf()
    }.asServerAddress(tls = TcpSocket.TLS.SAFE)

    /** The flow containing the electrum header responses messages. */
    private val _electrumMessages by lazy { MutableStateFlow<HeaderSubscriptionResponse?>(null) }
    fun electrumMessages(): StateFlow<HeaderSubscriptionResponse?> = _electrumMessages

    private fun watchElectrumMessages() = launch {
        electrumClient.openNotificationsSubscription().consumeAsFlow().filterIsInstance<HeaderSubscriptionResponse>()
            .collect {
                _electrumMessages.value = it
            }
    }

    // Tor configuration
    private val isTorEnabled = MutableStateFlow(false)
    fun subscribeToIsTorEnabled(): StateFlow<Boolean> = isTorEnabled
    fun updateTorUsage(enabled: Boolean): Unit {
        isTorEnabled.value = enabled
    }
}
