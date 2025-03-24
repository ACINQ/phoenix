package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.PaymentEvents
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.db.Bolt11IncomingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.logging.info
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqlitePaymentsDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class PaymentsManager(
    private val loggerFactory: LoggerFactory,
    private val configurationManager: AppConfigurationManager,
    private val contactsManager: ContactsManager,
    private val databaseManager: DatabaseManager,
    private val electrumClient: ElectrumClient,
    private val nodeParamsManager: NodeParamsManager,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        configurationManager = business.appConfigurationManager,
        contactsManager = business.contactsManager,
        databaseManager = business.databaseManager,
        electrumClient = business.electrumClient,
        nodeParamsManager = business.nodeParamsManager,
    )

    private val log = loggerFactory.newLogger(this::class)

    /** Contains the most recently completed payment (only bolt11 incoming, or bolt11/bolt12 outgoing). */
    private val _lastCompletedPayment = MutableStateFlow<WalletPayment?>(null)
    val lastCompletedPayment: StateFlow<WalletPayment?> = _lastCompletedPayment

    fun makePageFetcher(): PaymentsPageFetcher {
        return PaymentsPageFetcher(loggerFactory, databaseManager, contactsManager)
    }

    init {

        launch { monitorLastCompletedPayment() }

        launch { monitorUnconfirmedTransactions() }
    }

    private suspend fun monitorLastCompletedPayment() {
        val nodeParams = nodeParamsManager.nodeParams.filterNotNull().first()
        nodeParams.nodeEvents.filterIsInstance<PaymentEvents>().collect {
            when (it) {
                is PaymentEvents.PaymentReceived -> if (it.payment is Bolt11IncomingPayment) _lastCompletedPayment.value = it.payment
                is PaymentEvents.PaymentSent -> if (it.payment is LightningOutgoingPayment) _lastCompletedPayment.value = it.payment
            }
        }
    }

    /** Watches transactions that are unconfirmed, checks their confirmation status at each block, and updates relevant payments. */
    private suspend fun monitorUnconfirmedTransactions() {
        val paymentsDb = paymentsDb()
        // We need to recheck anytime either:
        // - the list of unconfirmed txs changes
        // - a new block is mined
        combine(
            paymentsDb.listUnconfirmedTransactions(),
            configurationManager.electrumMessages
        ) { unconfirmedTxs, header ->
            unconfirmedTxs to header?.blockHeight
        }.collect { (unconfirmedTxs, blockHeight) ->
            if (blockHeight != null) {
                log.debug { "checking confirmation status of ${unconfirmedTxs.size} txs at block=$blockHeight" }
                unconfirmedTxs.forEach { txId ->
                    val conf = electrumClient.getConfirmations(txId)
                    log.info { "found confirmations=$conf for tx=$txId" }
                    if (conf != null && conf > 0) {
                        paymentsDb.setConfirmed(txId)
                    }
                }
            }
        }
    }

    private suspend fun paymentsDb(): SqlitePaymentsDb {
        return databaseManager.paymentsDb()
    }

    suspend fun updateMetadata(id: UUID, userDescription: String?) {
        paymentsDb().updateUserInfo(id = id, userDescription = userDescription, userNotes = null)
    }

    /**
     * Returns payment(s) related to a transaction id. Useful to link a commitment change in a channel to the
     * payment(s) that triggered that change.
     */
    suspend fun listPaymentsForTxId(txId: TxId): List<WalletPayment> {
        return paymentsDb().listPaymentsForTxId(txId)
    }

    /** Returns the first incoming payment related to a transaction id. Useful to find the incoming payment that triggered a liquidity purchase. */
    suspend fun getIncomingPaymentForTxId(txId: TxId): WalletPayment? {
        return listPaymentsForTxId(txId).filterIsInstance<IncomingPayment>().firstOrNull()
    }

    suspend fun getPayment(
        id: UUID
    ): WalletPaymentInfo? {
        return paymentsDb().getPayment(id)?.let {
            val payment = it.first
            val contact = contactsManager.contactForPayment(payment)
            WalletPaymentInfo(
                payment = payment,
                metadata = it.second ?: WalletPaymentMetadata(),
                contact = contact
            )
        }
    }
}
