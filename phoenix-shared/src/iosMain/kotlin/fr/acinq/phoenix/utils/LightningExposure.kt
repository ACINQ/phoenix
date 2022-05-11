package fr.acinq.phoenix.utils

import fr.acinq.lightning.channel.*
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.io.NativeSocketException
import fr.acinq.lightning.io.TcpSocket
import fr.acinq.lightning.utils.Connection

/**
 * Class types from lightning-kmp & bitcoin-kmp are not exported to iOS unless we explicitly
 * reference them within PhoenixShared, either as a public parameter or return type.
 *
 * This problem is restricted to iOS, and doesn't not affect Android.
 */

fun IncomingPayment.Origin.asInvoice(): IncomingPayment.Origin.Invoice? = when (this) {
    is IncomingPayment.Origin.Invoice -> this
    else -> null
}

fun IncomingPayment.Origin.asKeySend(): IncomingPayment.Origin.KeySend? = when (this) {
    is IncomingPayment.Origin.KeySend -> this
    else -> null
}

fun IncomingPayment.Origin.asSwapIn(): IncomingPayment.Origin.SwapIn? = when (this) {
    is IncomingPayment.Origin.SwapIn -> this
    else -> null
}

fun IncomingPayment.ReceivedWith.asLightningPayment(): IncomingPayment.ReceivedWith.LightningPayment? = when (this) {
    is IncomingPayment.ReceivedWith.LightningPayment -> this
    else -> null
}

fun IncomingPayment.ReceivedWith.asNewChannel(): IncomingPayment.ReceivedWith.NewChannel? = when (this) {
    is IncomingPayment.ReceivedWith.NewChannel -> this
    else -> null
}

fun OutgoingPayment.Details.asNormal(): OutgoingPayment.Details.Normal? = when (this) {
    is OutgoingPayment.Details.Normal -> this
    else -> null
}

fun OutgoingPayment.Details.asKeySend(): OutgoingPayment.Details.KeySend? = when (this) {
    is OutgoingPayment.Details.KeySend -> this
    else -> null
}

fun OutgoingPayment.Details.asSwapOut(): OutgoingPayment.Details.SwapOut? = when (this) {
    is OutgoingPayment.Details.SwapOut -> this
    else -> null
}

fun OutgoingPayment.Details.asChannelClosing(): OutgoingPayment.Details.ChannelClosing? = when (this) {
    is OutgoingPayment.Details.ChannelClosing -> this
    else -> null
}

fun OutgoingPayment.Status.asPending(): OutgoingPayment.Status.Pending? = when (this) {
    is OutgoingPayment.Status.Pending -> this
    else -> null
}

fun OutgoingPayment.Status.asFailed(): OutgoingPayment.Status.Completed.Failed? = when (this) {
    is OutgoingPayment.Status.Completed.Failed -> this
    else -> null
}

fun OutgoingPayment.Status.asSucceeded(): OutgoingPayment.Status.Completed.Succeeded? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded -> this
    else -> null
}

fun OutgoingPayment.Status.asOffChain(): OutgoingPayment.Status.Completed.Succeeded.OffChain? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OffChain -> this
    else -> null
}

fun OutgoingPayment.Status.asOnChain(): OutgoingPayment.Status.Completed.Succeeded.OnChain? = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OnChain -> this
    else -> null
}

fun ChannelState.asOffline(): Offline? = when (this) {
    is Offline -> this
    else -> null
}
fun ChannelState.asClosing(): Closing? = when (this) {
    is Closing -> this
    else -> null
}
fun ChannelState.asClosed(): Closed? = when (this) {
    is Closed -> this
    else -> null
}
fun ChannelState.asAborted(): Aborted? = when (this) {
    is Aborted -> this
    else -> null
}

fun TcpSocket.TLS.asDisabled(): TcpSocket.TLS.DISABLED? = when (this) {
    is TcpSocket.TLS.DISABLED -> this
    else -> null
}

fun TcpSocket.TLS.asTrustedCertificates(): TcpSocket.TLS.TRUSTED_CERTIFICATES? = when (this) {
    is TcpSocket.TLS.TRUSTED_CERTIFICATES -> this
    else -> null
}

fun TcpSocket.TLS.asPinnedPublicKey(): TcpSocket.TLS.PINNED_PUBLIC_KEY? = when (this) {
    is TcpSocket.TLS.PINNED_PUBLIC_KEY -> this
    else -> null
}

fun TcpSocket.TLS.asUnsafeCertificates(): TcpSocket.TLS.UNSAFE_CERTIFICATES? = when (this) {
    is TcpSocket.TLS.UNSAFE_CERTIFICATES -> this
    else -> null
}

fun Connection.asClosed(): Connection.CLOSED? = when (this) {
    is Connection.CLOSED -> this
    else -> null
}

fun Connection.asEstablishing(): Connection.ESTABLISHING? = when (this) {
    is Connection.ESTABLISHING -> this
    else -> null
}

fun Connection.isEstablished(): Connection.ESTABLISHED? = when (this) {
    is Connection.ESTABLISHED -> this
    else -> null
}

fun NativeSocketException.asPOSIX(): NativeSocketException.POSIX? = when (this) {
    is NativeSocketException.POSIX -> this
    else -> null
}

fun NativeSocketException.asDNS(): NativeSocketException.DNS? = when (this) {
    is NativeSocketException.DNS -> this
    else -> null
}

fun NativeSocketException.asTLS(): NativeSocketException.TLS? = when (this) {
    is NativeSocketException.TLS -> this
    else -> null
}
