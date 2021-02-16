package fr.acinq.phoenix.app

import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.db.PaymentsDb
import fr.acinq.eclair.db.WalletPayment
import fr.acinq.eclair.io.PaymentNotSent
import fr.acinq.eclair.io.PaymentProgress
import fr.acinq.eclair.io.PaymentReceived
import fr.acinq.eclair.io.PaymentSent
import fr.acinq.eclair.payment.PaymentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsManager(
    loggerFactory: LoggerFactory,
    private val paymentsDb: PaymentsDb,
    private val peerManager: PeerManager
) : CoroutineScope by MainScope() {
    private val logger = newLogger(loggerFactory)

    /** Get a list of wallet payments from database, with hard coded parameters. */
    private suspend fun listPayments(): List<WalletPayment> = paymentsDb.listPayments(150, 0)

    /** Broadcasts an observable relevant list of payments. */
    private val payments = MutableStateFlow<Pair<List<WalletPayment>, WalletPayment?>>(Pair(emptyList(), null))

    /**
     * Broadcasts the most recent incoming payment since the app was launched.
     *
     * If we haven't received any payments since app launch, the value will be null.
     * Value is refreshed when the peer emits a [PaymentReceived] event.
     *
     * This is currently used for push notification handling on iOS.
     * On iOS, when the app is in the background, and a push notification is received,
     * the app is required to tell the OS when it has finished processing the notification.
     * This channel is used for that purpose: when a payment is received, the app can be suspended again.
     *
     * As a side effect, this allows the app to show a notification when a payment has been received.
     */
    private val lastIncomingPayment = MutableStateFlow<WalletPayment?>(null)

    init {
        launch {
            payments.value = listPayments() to null
            peerManager.getPeer().openListenerEventSubscription().consumeEach { event ->
                when (event) {
                    is PaymentReceived, is PaymentSent, is PaymentNotSent, is PaymentProgress -> {
                        logger.debug { "refreshing payment history with event=$event" }
                        if (event is PaymentReceived) {
                            lastIncomingPayment.value = event.incomingPayment
                        }
                        val list = listPayments()
                        payments.value = list to list.firstOrNull()?.takeIf { WalletPayment.completedAt(it) > 0 }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun subscribeToPayments(): StateFlow<Pair<List<WalletPayment>, WalletPayment?>> = payments
    fun subscribeToLastIncomingPayment(): StateFlow<WalletPayment?> = lastIncomingPayment
}

fun WalletPayment.desc(): String? = when (this) {
    is OutgoingPayment -> when (val d = this.details) {
        is OutgoingPayment.Details.Normal -> d.paymentRequest.description
        is OutgoingPayment.Details.KeySend -> "donation"
        is OutgoingPayment.Details.SwapOut -> d.address
    }
    is IncomingPayment -> when (val o = this.origin) {
        is IncomingPayment.Origin.Invoice -> o.paymentRequest.description
        is IncomingPayment.Origin.KeySend -> "donation"
        is IncomingPayment.Origin.SwapIn -> o.address
    }
}.takeIf { !it.isNullOrBlank() }

enum class WalletPaymentState { Success, Pending, Failure }

fun WalletPayment.amountMsat(): Long = when (this) {
    is OutgoingPayment -> -recipientAmount.msat
    is IncomingPayment -> received?.amount?.msat ?: 0
}

fun WalletPayment.id(): String = when (this) {
    is OutgoingPayment -> this.id.toString()
    is IncomingPayment -> this.paymentHash.toHex()
}

fun WalletPayment.state(): WalletPaymentState = when (this) {
    is OutgoingPayment -> when (status) {
        is OutgoingPayment.Status.Pending -> WalletPaymentState.Pending
        is OutgoingPayment.Status.Succeeded -> WalletPaymentState.Success
        is OutgoingPayment.Status.Failed -> WalletPaymentState.Failure
    }
    is IncomingPayment -> when (received) {
        null -> WalletPaymentState.Pending
        else -> WalletPaymentState.Success
    }
}

fun WalletPayment.timestamp(): Long = WalletPayment.completedAt(this)

fun WalletPayment.errorMessage(): String? = when (this) {
    is OutgoingPayment -> when (val s = status) {
        is OutgoingPayment.Status.Failed -> s.reason.toString()
        else -> null
    }
    is IncomingPayment -> null
}

// Class type IncomingPayment.Origin.Invoice is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun IncomingPayment.Origin.asInvoice(): IncomingPayment.Origin.Invoice? = when (this) {
    is IncomingPayment.Origin.Invoice -> this
    else -> null
}

// Class type IncomingPayment.Origin.KeySend is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun IncomingPayment.Origin.asKeySend(): IncomingPayment.Origin.KeySend? = when (this) {
    is IncomingPayment.Origin.KeySend -> this
    else -> null
}

// Class type IncomingPayment.Origin.SwapIn is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun IncomingPayment.Origin.asSwapIn(): IncomingPayment.Origin.SwapIn? = when (this) {
    is IncomingPayment.Origin.SwapIn -> this
    else -> null
}

// Class type OutgoingPayment.Details.Normal is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Details.asNormal(): OutgoingPayment.Details.Normal? = when (this) {
    is OutgoingPayment.Details.Normal -> this
    else -> null
}

// Class type OutgoingPayment.Details.KeySend is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Details.asKeySend(): OutgoingPayment.Details.KeySend? = when (this) {
    is OutgoingPayment.Details.KeySend -> this
    else -> null
}

// Class type OutgoingPayment.Details.SwapOut is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Details.asSwapOut(): OutgoingPayment.Details.SwapOut? = when (this) {
    is OutgoingPayment.Details.SwapOut -> this
    else -> null
}

// Class type OutgoingPayment.Status.Pending is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asPending(): OutgoingPayment.Status.Pending? = when (this) {
    is OutgoingPayment.Status.Pending -> this
    else -> null
}

// Class type OutgoingPayment.Status.Failed is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asFailed(): OutgoingPayment.Status.Failed? = when (this) {
    is OutgoingPayment.Status.Failed -> this
    else -> null
}

// Class type OutgoingPayment.Status.Succeeded is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asSucceeded(): OutgoingPayment.Status.Succeeded? = when (this) {
    is OutgoingPayment.Status.Succeeded -> this
    else -> null
}

// In Objective-C, the function name `description()` is already in use (part of NSObject).
// So we need to alias it.
fun PaymentRequest.desc(): String? = this.description
