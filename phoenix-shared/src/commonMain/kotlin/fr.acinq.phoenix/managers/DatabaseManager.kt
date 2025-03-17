package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.db.Databases
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.SqliteChannelsDb
import fr.acinq.phoenix.db.SqlitePaymentsDb
import fr.acinq.phoenix.db.createChannelsDbDriver
import fr.acinq.phoenix.db.createPaymentsDbDriver
import fr.acinq.phoenix.db.createSqliteChannelsDb
import fr.acinq.phoenix.db.createSqlitePaymentsDb
import fr.acinq.phoenix.db.makeCloudKitDb
import fr.acinq.phoenix.db.payments.CloudKitInterface
import fr.acinq.phoenix.utils.MetadataQueue
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DatabaseManager(
    loggerFactory: LoggerFactory,
    private val ctx: PlatformContext,
    private val chain: Chain,
    private val appDb: SqliteAppDb,
    private val nodeParamsManager: NodeParamsManager,
    private val contactsManager: ContactsManager,
    private val currencyManager: CurrencyManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        ctx = business.ctx,
        appDb = business.appDb,
        chain = business.chain,
        nodeParamsManager = business.nodeParamsManager,
        contactsManager = business.contactsManager,
        currencyManager = business.currencyManager
    )

    private val log = loggerFactory.newLogger(this::class)

    private val _databases = MutableStateFlow<PhoenixDatabases?>(null)
    val databases: StateFlow<PhoenixDatabases?> = _databases.asStateFlow()

    val metadataQueue = MetadataQueue(currencyManager)

    init {
        launch {
            nodeParamsManager.nodeParams.collect { nodeParams ->
                if (nodeParams == null) return@collect
                log.debug { "nodeParams available: building databases..." }

                val nodeIdHash = nodeParams.nodeId.hash160().byteVector().toHex()
                val channelsDbDriver = createChannelsDbDriver(ctx, chain, nodeIdHash)
                val channelsDb = createSqliteChannelsDb(channelsDbDriver)
                val paymentsDbDriver = createPaymentsDbDriver(ctx, chain, nodeIdHash) { log.e { "payments-db migration error: $it" } }
                val paymentsDb = createSqlitePaymentsDb(paymentsDbDriver, metadataQueue, contactsManager, loggerFactory)
                val cloudKitDb = makeCloudKitDb(appDb, paymentsDb)
                log.debug { "databases object created" }
                _databases.value = PhoenixDatabases(
                    channels = channelsDb,
                    payments = paymentsDb,
                    cloudKit = cloudKitDb,
                )
            }
        }
    }

    fun close() {
        val db = databases.value
        if (db != null) {
            db.channels.close()
            db.payments.close()
        }
    }

    suspend fun paymentsDb(): SqlitePaymentsDb {
        val db = databases.filterNotNull().first()
        return db.payments
    }

    suspend fun cloudKitDb(): CloudKitInterface? {
        val db = databases.filterNotNull().first()
        return db.cloudKit
    }
}

data class PhoenixDatabases(
    override val channels: SqliteChannelsDb,
    override val payments: SqlitePaymentsDb,
    val cloudKit: CloudKitInterface?,
): Databases
