package fr.acinq.phoenix.utils

import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.data.Chain
import org.kodein.memory.util.freeze

/**
 * Standardized location for extending types from: fr.acinq.lightning
 */

enum class WalletPaymentState { Success, Pending, Failure }

val WalletPayment.createdAt: Long get() = when (this) {
    is OutgoingPayment -> this.createdAt
    is IncomingPayment -> this.createdAt
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

fun WalletPayment.errorMessage(): String? = when (this) {
    is OutgoingPayment -> when (val s = status) {
        is OutgoingPayment.Status.Completed.Failed -> s.reason.toString()
        else -> null
    }
    is IncomingPayment -> null
}

/**
 * This function exists because the `freeze()` function isn't exposed to iOS.
 */
fun WalletPayment.copyAndFreeze(): WalletPayment {
    return this.freeze()
}

/**
 * In Objective-C, the function name `description()` is already in use (part of NSObject).
 * So we need to alias it.
 */
fun PaymentRequest.desc(): String? = this.description

/**
 * Since unix epoch
 */
fun PaymentRequest.expiryTimestampSeconds(): Long? = this.expirySeconds?.let {
    this.timestampSeconds + it
}

fun PaymentRequest.chain(): Chain? = when (this.prefix) {
    "lnbc" -> Chain.Mainnet
    "lntb" -> Chain.Testnet
    "lnbcrt" -> Chain.Regtest
    else -> null
}
