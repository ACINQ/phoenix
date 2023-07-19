package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.byteVector32
import fr.acinq.lightning.blockchain.electrum.ElectrumClient
import fr.acinq.lightning.blockchain.electrum.getConfirmations
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.io.PaymentNotSent
import fr.acinq.lightning.io.PaymentProgress
import fr.acinq.lightning.io.PaymentSent
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.db.SqlitePaymentsDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


class PaymentsManager(
    private val loggerFactory: LoggerFactory,
    private val configurationManager: AppConfigurationManager,
    private val peerManager: PeerManager,
    private val databaseManager: DatabaseManager,
    private val electrumClient: ElectrumClient,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        configurationManager = business.appConfigurationManager,
        peerManager = business.peerManager,
        databaseManager = business.databaseManager,
        electrumClient = business.electrumClient
    )

    private val log = newLogger(loggerFactory)

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
     * The in-flight flow tracks payments that are being sent. This is used by iOS in case the app goes into the
     * background and the iOS app needs to start a long-lived task so that the payment can still go through.
     */
    private val _inFlightOutgoingPayments = MutableStateFlow<Set<UUID>>(setOf())
    val inFlightOutgoingPayments: StateFlow<Set<UUID>> = _inFlightOutgoingPayments

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

        launch { monitorInflightOutgoingPayments() }
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
                    val completedAt = paymentInfo?.payment?.completedAt
                    if (completedAt != null && completedAt > appLaunchTimestamp) {
                        _lastCompletedPayment.value = paymentInfo.payment
                    }
                    break
                }
            }
        }
    }

    /** Monitors in-flight outgoing payments from peer events and forward them to the [_inFlightOutgoingPayments] flow. */
    private suspend fun monitorInflightOutgoingPayments() {
        peerManager.getPeer().eventsFlow.collect { event ->
            when (event) {
                is PaymentProgress -> {
                    addToInFlightOutgoingPayments(event.request.paymentId)
                }
                is PaymentSent -> {
                    removeFromInFlightOutgoingPayments(event.request.paymentId)
                }
                is PaymentNotSent -> {
                    removeFromInFlightOutgoingPayments(event.request.paymentId)
                }
                else -> Unit
            }
        }
    }

    /** Watches transactions that are unconfirmed, check their confirmation status, and update relevant payments. */
    private suspend fun monitorUnconfirmedTransactions() {
        val paymentsDb = paymentsDb()
        // We need to recheck anytime either:
        // - the list of unconfirmed txs changes
        // - a new block is mined
        combine(
            paymentsDb.listUnconfirmedTransactions(),
            configurationManager.electrumMessages
        ) { unconfirmedTxs, header ->
            Pair(unconfirmedTxs, header)
        }.collect { (unconfirmedTxs, _) ->
            val unconfirmedTxIds = unconfirmedTxs.map { it.byteVector32() }
            log.info { "monitoring unconfirmed txs=$unconfirmedTxIds" }
            unconfirmedTxIds.forEach { txId ->
                electrumClient.getConfirmations(txId)?.let { conf ->
                    if (conf > 0) {
                        log.info { "transaction $txId has $conf confirmations, updating database" }
                        paymentsDb.setConfirmed(txId)
                    }
                }
            }
        }
    }

    /** Adds to StateFlow<Set<UUID>> */
    private fun addToInFlightOutgoingPayments(id: UUID) {
        val oldSet = _inFlightOutgoingPayments.value
        val newSet = oldSet.plus(id)
        _inFlightOutgoingPayments.value = newSet
    }

    /** Removes from StateFlow<Set<UUID>> */
    private fun removeFromInFlightOutgoingPayments(id: UUID) {
        val oldSet = _inFlightOutgoingPayments.value
        val newSet = oldSet.minus(id)
        _inFlightOutgoingPayments.value = newSet
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
        txId: ByteVector32
    ): List<WalletPayment> {
        return paymentsDb().listPaymentsForTxId(txId)
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
        }?.let {
            WalletPaymentInfo(
                payment = it.first,
                metadata = it.second ?: WalletPaymentMetadata(),
                fetchOptions = options
            )
        }
    }
}
