package fr.acinq.phoenix.managers

import fr.acinq.lightning.db.ChannelsDb
import fr.acinq.lightning.db.Databases
import fr.acinq.lightning.db.PaymentsDb
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.db.SqliteChannelsDb
import fr.acinq.phoenix.db.SqlitePaymentsDb
import fr.acinq.phoenix.db.createChannelsDbDriver
import fr.acinq.phoenix.db.createPaymentsDbDriver
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import org.kodein.memory.text.toHexString

class DatabaseManager(
    loggerFactory: LoggerFactory,
    private val ctx: PlatformContext,
    private val chain: Chain,
    private val nodeParamsManager: NodeParamsManager,
    private val currencyManager: CurrencyManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        ctx = business.ctx,
        chain = business.chain,
        nodeParamsManager = business.nodeParamsManager,
        currencyManager = business.currencyManager
    )

    private val log = newLogger(loggerFactory)

    private val _databases = MutableStateFlow<Databases?>(null)
    val databases: StateFlow<Databases?> = _databases

    init {
        launch {
            nodeParamsManager.nodeParams.collect { nodeParams ->
                if (nodeParams == null) return@collect
                log.info { "nodeParams available: building databases..." }

                val nodeIdHash = nodeParams.nodeId.hash160().toHexString()
                val channelsDb = SqliteChannelsDb(
                    driver = createChannelsDbDriver(ctx, chain, nodeIdHash)
                )
                val paymentsDb = SqlitePaymentsDb(
                    driver = createPaymentsDbDriver(ctx, chain, nodeIdHash),
                    currencyManager = currencyManager
                )
                log.debug { "databases object created" }
                _databases.value = object : Databases {
                    override val channels: ChannelsDb get() = channelsDb
                    override val payments: PaymentsDb get() = paymentsDb
                }
            }
        }
    }

    fun close() {
        val db = databases.value
        if (db != null) {
            (db.channels as SqliteChannelsDb).close()
            (db.payments as SqlitePaymentsDb).close()
        }
    }

    suspend fun paymentsDb(): SqlitePaymentsDb {
        val db = databases.filterNotNull().first()
        return db.payments as SqlitePaymentsDb
    }
}