package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.TxId
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.SpliceCpfpOutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqlitePaymentsDb
import fr.acinq.lightning.logging.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch


class PaymentsManager(
    private val loggerFactory: LoggerFactory,
    private val configurationManager: AppConfigurationManager,
    private val contactsManager: ContactsManager,
    private val databaseManager: DatabaseManager,
    private val electrumClient: ElectrumClient,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        configurationManager = business.appConfigurationManager,
        contactsManager = business.contactsManager,
        databaseManager = business.databaseManager,
        electrumClient = business.electrumClient
    )

    private val log = loggerFactory.newLogger(this::class)

    /**
     * A flow containing the total number of payments in the database,
     * and automatically refreshed when the database changes.
     */
    private val _paymentsCount = MutableStateFlow<Long>(0)
    val paymentsCount: StateFlow<Long> = _paymentsCount

    /**
     * Broadcasts the most recently completed payment since the app was launched.
     * This includes incoming & outgoing payments (both successful & failed).
     *
     * If we haven't completed any payments since app launch, the value will be null.
     */
    private val _lastCompletedPayment = MutableStateFlow<WalletPayment?>(null)
    val lastCompletedPayment: StateFlow<WalletPayment?> = _lastCompletedPayment

    /**
     * Provides a default PaymentsFetcher for use by the app.
     * (You can also create your own instances if needed.)
     */
    val fetcher: PaymentsFetcher by lazy {
        PaymentsFetcher(loggerFactory = loggerFactory, paymentsManager = this, cacheSizeLimit = 250)
    }

    fun makePageFetcher(): PaymentsPageFetcher {
        return PaymentsPageFetcher(loggerFactory, databaseManager)
    }

    init {
        launch { monitorPaymentsCountInDb() }

        launch { monitorLastCompletedPayment(currentTimestampMillis()) }

        launch { monitorUnconfirmedTransactions() }
    }

    private suspend fun monitorPaymentsCountInDb() {
        paymentsDb().listPaymentsCountFlow().collect { _paymentsCount.value = it }
    }

    /** Monitors the payments database and push any new payments in the [_lastCompletedPayment] flow. */
    private suspend fun monitorLastCompletedPayment(appLaunchTimestamp: Long) {
        paymentsDb().listPaymentsOrderFlow(count = 25, skip = 0).collectIndexed { index, list ->
            // NB: lastCompletedPayment should NOT fire under any of the following conditions:
            // - relaunching app with completed payments in database
            // - restoring old wallet and downloading transaction history
            if (index > 0) {
                for (row in list) {
                    val paymentInfo = fetcher.getPayment(row, WalletPaymentFetchOptions.None)
                    val payment = paymentInfo?.payment
                    if (payment is InboundLiquidityOutgoingPayment || payment is SpliceCpfpOutgoingPayment
                        || (payment is LightningOutgoingPayment && payment.details is LightningOutgoingPayment.Details.Blinded)
                    ) {
                        // ignore cpfp/inbound/blinded
                    } else {
                        val completedAt = paymentInfo?.payment?.completedAt
                        if (completedAt != null && completedAt > appLaunchTimestamp) {
                            _lastCompletedPayment.value = paymentInfo.payment
                        }
                    }
                    break
                }
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
            unconfirmedTxs.map { TxId(it) } to header?.blockHeight
        }.collect { (unconfirmedTxs, blockHeight) ->
            if (blockHeight != null) {
                log.debug { "checking confirmation status of ${unconfirmedTxs.size} txs at block=$blockHeight" }
                unconfirmedTxs.forEach { txId ->
                    electrumClient.getConfirmations(txId)?.let { conf ->
                        if (conf > 0) {
                            log.debug { "transaction $txId has $conf confirmations, updating database" }
                            paymentsDb.setConfirmed(txId)
                        }
                    }
                }
            }
        }
    }

    private suspend fun paymentsDb(): SqlitePaymentsDb {
        return databaseManager.paymentsDb()
    }

    suspend fun updateMetadata(id: WalletPaymentId, userDescription: String?) {
        paymentsDb().updateMetadata(
            id = id,
            userDescription = userDescription,
            userNotes = null
        )
    }

    /**
     * Returns the payment(s) that are related to a transaction id. Useful to link a commitment change in a channel to the
     * payment(s) that triggered that change.
     */
    suspend fun listPaymentsForTxId(
        txId: TxId
    ): List<WalletPayment> {
        return paymentsDb().listPaymentsForTxId(txId)
    }

    /**
     * Returns the inbound liquidity purchase relevant to a given transaction id.
     *
     * It is used to track the fees that may have been incurred by an incoming payment because of low liquidity. To do
     * that, we use the transaction id attached to the incoming payment, and find any purchase that matches.
     *
     * This allows us to display the fees of a liquidity purchase inside an incoming payment details screen.
     */
    suspend fun getLiquidityPurchaseForTxId(
        txId: TxId
    ): InboundLiquidityOutgoingPayment? {
        return paymentsDb().getInboundLiquidityPurchase(txId)
    }

    /**
     * Returns the inbound liquidity purchase relevant to a given transaction id.
     *
     * It is used to track the fees that may have been incurred by an incoming payment because of low liquidity. To do
     * that, we use the transaction id attached to the incoming payment, and find any purchase that matches.
     *
     * This allows us to display the fees of a liquidity purchase inside an incoming payment details screen.
     */
    suspend fun listIncomingPaymentsForTxId(
        txId: TxId
    ): List<WalletPaymentId.IncomingPaymentId> {
        return paymentsDb().getWalletPaymentIdForTxId(txId).filterIsInstance<WalletPaymentId.IncomingPaymentId>()
    }

    suspend fun getPayment(
        id: WalletPaymentId,
        options: WalletPaymentFetchOptions
    ): WalletPaymentInfo? {
        return when (id) {
            is WalletPaymentId.IncomingPaymentId -> paymentsDb().getIncomingPayment(id.paymentHash, options)
            is WalletPaymentId.LightningOutgoingPaymentId -> paymentsDb().getLightningOutgoingPayment(id.id, options)
            is WalletPaymentId.SpliceOutgoingPaymentId -> paymentsDb().getSpliceOutgoingPayment(id.id, options)
            is WalletPaymentId.ChannelCloseOutgoingPaymentId -> paymentsDb().getChannelCloseOutgoingPayment(id.id, options)
            is WalletPaymentId.SpliceCpfpOutgoingPaymentId -> paymentsDb().getSpliceCpfpOutgoingPayment(id.id, options)
            is WalletPaymentId.InboundLiquidityOutgoingPaymentId -> paymentsDb().getInboundLiquidityOutgoingPayment(id.id, options)
        }?.let {
            val payment = it.first
            val contact = if (options.contains(WalletPaymentFetchOptions.Contact)) {
                contactsManager.contactForPayment(payment)
            } else { null }
            WalletPaymentInfo(
                payment = payment,
                metadata = it.second ?: WalletPaymentMetadata(),
                contact = contact,
                fetchOptions = options
            )
        }
    }
}
