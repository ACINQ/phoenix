package fr.acinq.phoenix.app

import fr.acinq.eclair.WalletParams
import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.utils.RETRY_DELAY
import fr.acinq.phoenix.utils.increaseDelay
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.kodein.db.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.math.max
import kotlin.time.*

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class AppConfigurationManager(
    private val noSqlDb: DB, // TODO to be replaced by [appDb]
    private val appDb: SqliteAppDb,
    private val httpClient: HttpClient,
    private val electrumClient: ElectrumClient,
    private val chain: Chain,
    loggerFactory: LoggerFactory
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    init {
        // Wallet Params
        initWalletParams()
        // Electrum Triggers
        noSqlDb.on<ElectrumServer>().register {
            didPut {
                launch { electrumServerUpdates.send(it) }
            }
        }
        launch {
            electrumClient.openNotificationsSubscription().consumeAsFlow()
                .filterIsInstance<HeaderSubscriptionResponse>().collect { notification ->
                    if (getElectrumServer().blockHeight == notification.height &&
                        getElectrumServer().tipTimestamp == notification.header.time
                    ) return@collect

                    putElectrumServer(
                        getElectrumServer().copy(
                            blockHeight = notification.height,
                            tipTimestamp = notification.header.time
                        )
                    )
                }
        }
    }

    //endregion WalletParams fetch / store / initialization
    private val currentWalletParamsVersion = ApiWalletParams.Version.V0
    private val _walletParams = MutableStateFlow<WalletParams?>(null)
    val walletParams: StateFlow<WalletParams?> = _walletParams

    public fun initWalletParams() = launch {
        val (instant, fallbackWalletParams) = appDb.getWalletParamsOrNull(currentWalletParamsVersion)

        val freshness = Clock.System.now() - instant
        logger.info { "local WalletParams loaded, not updated since=$freshness" }

        val timeout =
            if (freshness < 48.hours) 2.seconds
            else max(freshness.inDays.toInt(), 5) * 2.seconds // max=10s

        // TODO are we using TOR? -> increase timeout

        val walletParams =
            try {
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
    }

    private var updateParametersJob: Job? = null
    public fun startWalletParamsLoop() {
        updateParametersJob = updateWalletParamsLoop()
    }
    public fun stopWalletParamsLoop() {
        launch { updateParametersJob?.cancelAndJoin() }
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

    private suspend fun fetchAndStoreWalletParams() : WalletParams? {
        return try {
            val rawData = httpClient.get<String>("https://acinq.co/phoenix/walletcontext.json")
            appDb.setWalletParams(currentWalletParamsVersion, rawData)
        } catch (t: Throwable) {
            logger.error(t) { "${t.message}" }
            null
        }
    }
    //endregion

    //region Electrum configuration
    private val electrumServerUpdates = ConflatedBroadcastChannel<ElectrumServer>()
    fun openElectrumServerUpdateSubscription(): ReceiveChannel<ElectrumServer> =
        electrumServerUpdates.openSubscription()

    private val electrumServerKey = noSqlDb.key<ElectrumServer>(0)
    private fun createElectrumConfiguration(): ElectrumServer {
        if (noSqlDb[electrumServerKey] == null) {
            logger.info { "Create ElectrumX configuration" }
            setRandomElectrumServer()
        }
        return noSqlDb[electrumServerKey] ?: error("ElectrumServer must be initialized.")
    }

    fun getElectrumServer(): ElectrumServer = noSqlDb[electrumServerKey] ?: createElectrumConfiguration()

    private fun putElectrumServer(electrumServer: ElectrumServer) {
        logger.info { "Update electrum configuration [$electrumServer]" }
        noSqlDb.put(electrumServerKey, electrumServer)
    }

    fun putElectrumServerAddress(host: String, port: Int, customized: Boolean = false) {
        putElectrumServer(getElectrumServer().copy(host = host, port = port, customized = customized))
    }

    fun setRandomElectrumServer() {
        putElectrumServer(
            when (chain) {
                Chain.MAINNET -> electrumMainnetConfigurations.random()
                Chain.TESTNET -> electrumTestnetConfigurations.random()
                Chain.REGTEST -> platformElectrumRegtestConf()
            }.asElectrumServer()
        )
    }
    //endregion
}
