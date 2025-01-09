package fr.acinq.phoenix.managers

import app.cash.sqldelight.EnumColumnAdapter
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.db.Databases
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.db.SqliteChannelsDb
import fr.acinq.phoenix.db.SqlitePaymentsDb
import fr.acinq.phoenix.db.createChannelsDbDriver
import fr.acinq.phoenix.db.createPaymentsDbDriver
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.lightning.logging.debug
import fr.acinq.phoenix.db.ByteVector32Adapter
import fr.acinq.phoenix.db.IncomingPaymentAdapter
import fr.acinq.phoenix.db.OutgoingPaymentAdapter
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.TxIdAdapter
import fr.acinq.phoenix.db.UUIDAdapter
import fr.acinq.phoenix.db.createSqlitePaymentsDb
import fr.acinq.phoenix.db.makeCloudKitDb
import fr.acinq.phoenix.db.payments.CloudKitInterface
import fracinqphoenixdb.Cloudkit_payments_metadata
import fracinqphoenixdb.Cloudkit_payments_queue
import fracinqphoenixdb.Link_lightning_outgoing_payment_parts
import fracinqphoenixdb.On_chain_txs
import fracinqphoenixdb.Payments_incoming
import fracinqphoenixdb.Payments_metadata
import fracinqphoenixdb.Payments_outgoing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DatabaseManager(
    loggerFactory: LoggerFactory,
    private val ctx: PlatformContext,
    private val chain: Chain,
    private val appDb: SqliteAppDb,
    private val nodeParamsManager: NodeParamsManager,
    private val currencyManager: CurrencyManager
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        ctx = business.ctx,
        appDb = business.appDb,
        chain = business.chain,
        nodeParamsManager = business.nodeParamsManager,
        currencyManager = business.currencyManager
    )

    private val log = loggerFactory.newLogger(this::class)

    private val _databases = MutableStateFlow<PhoenixDatabases?>(null)
    val databases: StateFlow<PhoenixDatabases?> = _databases.asStateFlow()

    init {
        launch {
            nodeParamsManager.nodeParams.collect { nodeParams ->
                if (nodeParams == null) return@collect
                log.debug { "nodeParams available: building databases..." }

                val nodeIdHash = nodeParams.nodeId.hash160().byteVector().toHex()
                val channelsDb = SqliteChannelsDb(
                    driver = createChannelsDbDriver(ctx, chain, nodeIdHash)
                )
                val paymentsDbDriver = createPaymentsDbDriver(ctx, chain, nodeIdHash)
                val paymentsDb = createSqlitePaymentsDb(paymentsDbDriver, currencyManager)
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
