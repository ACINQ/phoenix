package fr.acinq.phoenix.app

import fr.acinq.eklair.io.*
import fr.acinq.eklair.utils.UUID
import fr.acinq.eklair.utils.currentTimestampMillis
import fr.acinq.phoenix.data.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.db.*
import org.kodein.db.orm.kotlinx.KotlinxSerializer
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance


@OptIn(ExperimentalCoroutinesApi::class)
class AppHistoryManager(override val di: DI) : DIAware, CoroutineScope by MainScope() {

    private val dbFactory: DBFactory<DB> by instance()
    private val peer: Peer by instance()

    private val db = dbFactory.open("transactions", KotlinxSerializer())

    private fun getList() = db.find<Transaction>().byIndex("timestamp").useModels(reverse = true) { it.toList() }

    private val transactions = ConflatedBroadcastChannel(getList())

    init {
        launch {
            peer.openListenerEventSubscription().consumeEach {
                when (it) {
                    is PaymentReceived -> {
                        db.put(
                            Transaction(
                                UUID.randomUUID().toString(),
                                it.receivePayment.amount.toLong(),
                                it.receivePayment.description,
                                Transaction.Status.Success,
                                currentTimestampMillis()
                            )
                        )
                    }
                    is SendingPayment -> {
                        db.put(
                            Transaction(
                                it.id.toString(),
                                -it.paymentRequest.amount!!.toLong(), // TODO: Why is amount nullable ?!?!?
                                it.paymentRequest.description ?: "",
                                Transaction.Status.Pending,
                                currentTimestampMillis()
                            )
                        )
                    }
                    is PaymentSent -> {
                        db.put(
                            Transaction(
                                it.id.toString(),
                                -it.paymentRequest.amount!!.toLong(), // TODO: Why is amount nullable ?!?!?
                                it.paymentRequest.description ?: "",
                                Transaction.Status.Success,
                                currentTimestampMillis()
                            )
                        )
                    }
                    else -> {}
                }
            }
        }

        fun updateChannel() = launch { transactions.send(getList()) }

        db.on<Transaction>().register {
            didPut { updateChannel() }
            didDelete { updateChannel() }
        }
    }

    fun openTransactionsSubscriptions() = transactions.openSubscription()
}
