package fr.acinq.phoenix.app

import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.io.PaymentNotSent
import fr.acinq.eclair.io.PaymentProgress
import fr.acinq.eclair.io.PaymentReceived
import fr.acinq.eclair.io.PaymentSent
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.utils.UUID
import fr.acinq.eclair.utils.currentTimestampMillis
import fr.acinq.eclair.utils.eclairLogger
import fr.acinq.phoenix.data.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.db.DB
import org.kodein.db.find
import org.kodein.db.on
import org.kodein.db.useModels
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class)
class AppHistoryManager(
    loggerFactory: LoggerFactory,
    private val appDb: DB,
    private val peer: Peer
) : CoroutineScope by MainScope() {

    private fun getList() = appDb.find<Transaction>().byIndex("timestamp").useModels(reverse = true) { it.toList() }

    // Broadcasts the entire transaction history (every single record in the database),
    // everytime the database changes.
    // This is less than ideal, and is slated to be replaced by the new database interface.
    private val transactions = ConflatedBroadcastChannel(getList())

    // Broadcasts the most recent incoming transaction since the app was launched.
    // If we haven't received any payments since app launch, the value will be null.
    // This is currently used for push notification handling on iOS.
    // On iOS, when the app is in the background, and a push notification is received,
    // the app is required to tell the OS when it has finished processing the notification.
    // That is, the app is expected to go off and do some stuff, clean up,
    // and then tell the OS "hey thanks, I'm done". At which point, the app will be
    // suspended again (network connections severed, CPU suspended, etc).
    // So the iOS app just needs some way of being notified that it has received
    // the pending payment.
    private val incomingTransaction = ConflatedBroadcastChannel<Transaction?>(null)
    
    private val logger = newLogger(loggerFactory)

    init {
        launch {
            peer.openListenerEventSubscription().consumeEach {
                when (it) {
                    is PaymentReceived -> {
                        appDb.put(
                            Transaction(
                                id = UUID.randomUUID().toString(),
                                amountMsat = it.received.amount.msat,
                                desc = when (val origin = it.incomingPayment.origin) {
                                    is IncomingPayment.Origin.Invoice -> origin.paymentRequest.description ?: ""
                                    is IncomingPayment.Origin.KeySend -> ""
                                    is IncomingPayment.Origin.SwapIn -> ""
                                },
                                status = Transaction.Status.Success,
                                timestamp = it.received.receivedAt
                            )
                        )
                    }
                    is PaymentProgress -> {
                        val totalAmount = it.request.amount + it.fees
                        appDb.put(
                            Transaction(
                                id = it.request.paymentId.toString(),
                                amountMsat = -totalAmount.toLong(),
                                desc = it.request.details.paymentRequest.description ?: "",
                                status = Transaction.Status.Pending,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    is PaymentSent -> {
                        val status = it.payment.status
                        require(status is OutgoingPayment.Status.Succeeded) { "Got PaymentReceived notification with an incomming payment $status" }
                        val totalAmount = it.payment.recipientAmount + it.payment.fees
                        appDb.put(
                            Transaction(
                                id = it.payment.id.toString(),
                                amountMsat = -totalAmount.toLong(),
                                desc = when (val details = it.payment.details) {
                                    is OutgoingPayment.Details.Normal -> details.paymentRequest.description ?: ""
                                    is OutgoingPayment.Details.KeySend -> ""
                                    is OutgoingPayment.Details.SwapOut -> ""
                                },
                                status = Transaction.Status.Success,
                                timestamp = status.completedAt
                            )
                        )
                    }
                    is PaymentNotSent -> {
                        appDb.put(
                            Transaction(
                                id = it.request.paymentId.toString(),
                                amountMsat = -it.request.amount.msat,
                                desc = it.reason.details(),
                                status = Transaction.Status.Failure,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    else -> {
                    }
                }
            }
        }

        fun updateChannel() = launch { transactions.send(getList()) }

        appDb.on<Transaction>().register {
            didPut { updateChannel() }
            didDelete { updateChannel() }
        }

        appDb.on<Transaction>().register {
            didPut {
                if (it.amountMsat > 0) { // is PaymentReceived
                    launch { incomingTransaction.send(it) }
                }
            }
        }
    }

    fun openTransactionsSubscription() = transactions.openSubscription()
    fun openIncomingTransactionSubscription() = incomingTransaction.openSubscription()
}
