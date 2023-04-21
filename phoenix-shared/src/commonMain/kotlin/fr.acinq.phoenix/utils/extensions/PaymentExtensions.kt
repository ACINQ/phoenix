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

import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.db.*
import fr.acinq.lightning.payment.PaymentRequest
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
    is OnChainOutgoingPayment -> when (confirmedAt) {
        null -> WalletPaymentState.Pending
        else -> WalletPaymentState.Success
    }
    is LightningOutgoingPayment -> when (status) {
        is LightningOutgoingPayment.Status.Pending -> WalletPaymentState.Pending
        is LightningOutgoingPayment.Status.Completed.Failed -> WalletPaymentState.Failure
        is LightningOutgoingPayment.Status.Completed.Succeeded.OffChain -> WalletPaymentState.Success
    }
    is IncomingPayment -> when (completedAt) {
        null -> WalletPaymentState.Pending
        else -> WalletPaymentState.Success
    }
}

fun WalletPayment.paymentHashString(): String = when (this) {
    is OnChainOutgoingPayment -> throw NotImplementedError("no payment hash for on-chain outgoing")
    is LightningOutgoingPayment -> paymentHash.toString()
    is IncomingPayment -> paymentHash.toString()
}

fun WalletPayment.errorMessage(): String? = when (this) {
    is OnChainOutgoingPayment -> null
    is LightningOutgoingPayment -> when (val s = status) {
        is LightningOutgoingPayment.Status.Completed.Failed -> s.reason.toString()
        else -> null
    }
    is IncomingPayment -> null
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

fun PaymentRequest.chain(): NodeParams.Chain? = when (this.prefix) {
    "lnbc" -> NodeParams.Chain.Mainnet
    "lntb" -> NodeParams.Chain.Testnet
    "lnbcrt" -> NodeParams.Chain.Regtest
    else -> null
}
