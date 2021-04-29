package fr.acinq.phoenix.app

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.io.*
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.getValue
import fr.acinq.lightning.utils.setValue
import fr.acinq.lightning.utils.toMilliSatoshi
import fr.acinq.phoenix.db.SqlitePaymentsDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsManager(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val nodeParamsManager: NodeParamsManager,
) : CoroutineScope by MainScope() {
    private val log = newLogger(loggerFactory)

    /** A flow containing a list of payments, directly taken from the database and automatically refreshed when the database changes. */
    internal val payments = MutableStateFlow<List<WalletPayment>>(emptyList())

    /** Flow of map of (bitcoinAddress -> amount) swap-ins. */
    private val _incomingSwaps = MutableStateFlow<Map<String, MilliSatoshi>>(HashMap())
    val incomingSwaps: StateFlow<Map<String, MilliSatoshi>> = _incomingSwaps
    private var _incomingSwapsMap by _incomingSwaps

    /**
     * Broadcasts the most recently completed payment since the app was launched.
     * This includes incoming & outgoing payments (both successful & failed).
     *
     * If we haven't completed any payments since app launch, the value will be null.
     */
    private val _lastCompletedPayment = MutableStateFlow<WalletPayment?>(null)
    val lastCompletedPayment: StateFlow<WalletPayment?> = _lastCompletedPayment

    init {
        launch {
            val db = nodeParamsManager.databases.filterNotNull().first()
            (db.payments as SqlitePaymentsDb).listPaymentsFlow(150, 0, emptySet()).collect {
                payments.value = it
            }
        }

        launch {
            peerManager.getPeer().openListenerEventSubscription().consumeEach { event ->
                when (event) {
                    is PaymentSent -> {
                        _lastCompletedPayment.value = event.payment
                    }
                    is PaymentNotSent -> {
                        db()?.getOutgoingPayment(event.request.paymentId)?.let {
                            _lastCompletedPayment.value = it
                        }
                    }
                    is PaymentReceived -> {
                        _lastCompletedPayment.value = event.incomingPayment
                    }
                    is SwapInPendingEvent -> {
                        _incomingSwapsMap += (event.swapInPending.bitcoinAddress to event.swapInPending.amount.toMilliSatoshi())
                    }
                    is SwapInConfirmedEvent -> {
                        _incomingSwapsMap -= event.swapInConfirmed.bitcoinAddress
                    }
                    else -> Unit
                }
            }
        }
    }

    fun db() = nodeParamsManager.databases.value?.payments

    suspend fun getOutgoingPayment(id: UUID): OutgoingPayment? = db()?.getOutgoingPayment(id)
}

fun WalletPayment.desc(): String? = when (this) {
    is OutgoingPayment -> when (val d = this.details) {
        is OutgoingPayment.Details.Normal -> d.paymentRequest.description
        is OutgoingPayment.Details.KeySend -> "donation"
        is OutgoingPayment.Details.SwapOut -> d.address
        is OutgoingPayment.Details.ChannelClosing -> "channel closing"
    }
    is IncomingPayment -> when (val o = this.origin) {
        is IncomingPayment.Origin.Invoice -> o.paymentRequest.description
        is IncomingPayment.Origin.KeySend -> "donation"
        is IncomingPayment.Origin.SwapIn -> o.address
    }
}.takeIf { !it.isNullOrBlank() }

enum class WalletPaymentState { Success, Pending, Failure }

fun WalletPayment.amountMsat(): Long = when (this) {
    is OutgoingPayment -> -recipientAmount.msat - fees.msat
    is IncomingPayment -> received?.amount?.msat ?: 0
}

fun WalletPayment.id(): String = when (this) {
    is OutgoingPayment -> this.id.toString()
    is IncomingPayment -> this.paymentHash.toHex()
}

fun WalletPayment.state(): WalletPaymentState = when (this) {
    is OutgoingPayment -> when (status) {
        is OutgoingPayment.Status.Pending -> WalletPaymentState.Pending
        is OutgoingPayment.Status.Completed.Failed -> WalletPaymentState.Failure
        is OutgoingPayment.Status.Completed.Succeeded.OnChain -> WalletPaymentState.Success
        is OutgoingPayment.Status.Completed.Succeeded.OffChain -> WalletPaymentState.Success
    }
    is IncomingPayment -> when (received) {
        null -> WalletPaymentState.Pending
        else -> WalletPaymentState.Success
    }
}

fun WalletPayment.paymentHashString(): String = when (this) {
    is OutgoingPayment -> paymentHash.toString()
    is IncomingPayment -> paymentHash.toString()
}

fun WalletPayment.timestamp(): Long = WalletPayment.completedAt(this)

fun WalletPayment.errorMessage(): String? = when (this) {
    is OutgoingPayment -> when (val s = status) {
        is OutgoingPayment.Status.Completed.Failed -> s.reason.toString()
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

// Class type IncomingPayment.ReceivedWith.LightningPayment is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun IncomingPayment.ReceivedWith.asLightningPayment(): IncomingPayment.ReceivedWith.LightningPayment? = when (this) {
    is IncomingPayment.ReceivedWith.LightningPayment -> this
    else -> null
}

// Class type IncomingPayment.ReceivedWith.NewChannel is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun IncomingPayment.ReceivedWith.asNewChannel(): IncomingPayment.ReceivedWith.NewChannel? = when (this) {
    is IncomingPayment.ReceivedWith.NewChannel -> this
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

// Class type OutgoingPayment.Details.ChannelClosing is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Details.asChannelClosing(): OutgoingPayment.Details.ChannelClosing? = when (this) {
    is OutgoingPayment.Details.ChannelClosing -> this
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
fun OutgoingPayment.Status.asFailed(): OutgoingPayment.Status.Completed.Failed? = when (this) {
    is OutgoingPayment.Status.Completed.Failed -> this
    else -> null
}

// Class type OutgoingPayment.Status.Succeeded is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asSucceeded(): OutgoingPayment.Status.Completed.Succeeded? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded -> this
    else -> null
}

// Class type OutgoingPayment.Status.Succeeded.OffChain is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asOffChain(): OutgoingPayment.Status.Completed.Succeeded.OffChain? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OffChain -> this
    else -> null
}

// Class type OutgoingPayment.Status.Succeeded.OnChain is not exported to iOS unless
// we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asOnChain(): OutgoingPayment.Status.Completed.Succeeded.OnChain? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OnChain -> this
    else -> null
}

// In Objective-C, the function name `description()` is already in use (part of NSObject).
// So we need to alias it.
fun PaymentRequest.desc(): String? = this.description
