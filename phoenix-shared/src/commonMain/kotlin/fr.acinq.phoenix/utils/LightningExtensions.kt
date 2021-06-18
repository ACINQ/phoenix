package fr.acinq.phoenix.utils

import fr.acinq.lightning.channel.ChannelState
import fr.acinq.lightning.channel.Closing
import fr.acinq.lightning.channel.Offline
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.PaymentRequest

/**
 * Standardized location for extending types from: fr.acinq.lightning
 */

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

/**
 * Many class types are not exported to iOS unless we explicitly
 * reference them within PhoenixShared.
 */

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun IncomingPayment.Origin.asInvoice(): IncomingPayment.Origin.Invoice? = when (this) {
    is IncomingPayment.Origin.Invoice -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun IncomingPayment.Origin.asKeySend(): IncomingPayment.Origin.KeySend? = when (this) {
    is IncomingPayment.Origin.KeySend -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun IncomingPayment.Origin.asSwapIn(): IncomingPayment.Origin.SwapIn? = when (this) {
    is IncomingPayment.Origin.SwapIn -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun IncomingPayment.ReceivedWith.asLightningPayment(): IncomingPayment.ReceivedWith.LightningPayment? = when (this) {
    is IncomingPayment.ReceivedWith.LightningPayment -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun IncomingPayment.ReceivedWith.asNewChannel(): IncomingPayment.ReceivedWith.NewChannel? = when (this) {
    is IncomingPayment.ReceivedWith.NewChannel -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Details.asNormal(): OutgoingPayment.Details.Normal? = when (this) {
    is OutgoingPayment.Details.Normal -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Details.asKeySend(): OutgoingPayment.Details.KeySend? = when (this) {
    is OutgoingPayment.Details.KeySend -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Details.asSwapOut(): OutgoingPayment.Details.SwapOut? = when (this) {
    is OutgoingPayment.Details.SwapOut -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Details.asChannelClosing(): OutgoingPayment.Details.ChannelClosing? = when (this) {
    is OutgoingPayment.Details.ChannelClosing -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asPending(): OutgoingPayment.Status.Pending? = when (this) {
    is OutgoingPayment.Status.Pending -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asFailed(): OutgoingPayment.Status.Completed.Failed? = when (this) {
    is OutgoingPayment.Status.Completed.Failed -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asSucceeded(): OutgoingPayment.Status.Completed.Succeeded? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asOffChain(): OutgoingPayment.Status.Completed.Succeeded.OffChain? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OffChain -> this
    else -> null
}

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun OutgoingPayment.Status.asOnChain(): OutgoingPayment.Status.Completed.Succeeded.OnChain? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OnChain -> this
    else -> null
}

// In Objective-C, the function name `description()` is already in use (part of NSObject).
// So we need to alias it.
fun PaymentRequest.desc(): String? = this.description

// Class type not exported to iOS unless we explicitly reference it in PhoenixShared.
fun ChannelState.asOffline(): Offline? = when (this) {
    is Offline -> this
    else -> null
}
fun ChannelState.asClosing(): Closing? = when (this) {
    is Closing -> this
    else -> null
}
