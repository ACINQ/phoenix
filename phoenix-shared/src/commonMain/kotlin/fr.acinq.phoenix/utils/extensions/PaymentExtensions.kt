/*
 * Copyright 2022 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils.extensions

import fr.acinq.lightning.db.*
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.phoenix.data.Chain
import org.kodein.memory.util.freeze

/**
 * Standardized location for extending types from: fr.acinq.lightning
 */

enum class WalletPaymentState { Success, Pending, Failure }

fun WalletPayment.id(): String = when (this) {
    is OutgoingPayment -> this.id.toString()
    is IncomingPayment -> this.paymentHash.toHex()
}

fun WalletPayment.state(): WalletPaymentState = when (this) {
    is SpliceOutgoingPayment -> when (confirmedAt) {
        null -> WalletPaymentState.Pending
        else -> WalletPaymentState.Success
    }
    is LightningOutgoingPayment -> when (status) {
        is LightningOutgoingPayment.Status.Pending -> WalletPaymentState.Pending
        is LightningOutgoingPayment.Status.Completed.Failed -> WalletPaymentState.Failure
        is LightningOutgoingPayment.Status.Completed.Succeeded.OnChain -> WalletPaymentState.Success
        is LightningOutgoingPayment.Status.Completed.Succeeded.OffChain -> WalletPaymentState.Success
    }
    is IncomingPayment -> when (val received = received) {
        null -> WalletPaymentState.Pending
        else -> {
            if (received.receivedWith.all {
                when (it) {
                    is IncomingPayment.ReceivedWith.NewChannel -> it.confirmed
                    else -> true
                }
            }) WalletPaymentState.Success else WalletPaymentState.Pending
        }
    }
}

fun WalletPayment.paymentHashString(): String = when (this) {
    is SpliceOutgoingPayment -> throw NotImplementedError("no payment hash for splice-out")
    is LightningOutgoingPayment -> paymentHash.toString()
    is IncomingPayment -> paymentHash.toString()
}

fun WalletPayment.errorMessage(): String? = when (this) {
    is SpliceOutgoingPayment -> null
    is LightningOutgoingPayment -> when (val s = status) {
        is LightningOutgoingPayment.Status.Completed.Failed -> s.reason.toString()
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
