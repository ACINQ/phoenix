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

    private val transactions = ConflatedBroadcastChannel(getList())

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
    }

    fun openTransactionsSubscriptions() = transactions.openSubscription()
}
