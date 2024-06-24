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

import fr.acinq.bitcoin.PrivateKey
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.OnChainOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.lightning.wire.OfferTypes

/** Standardized location for extending types from: fr.acinq.lightning. */
enum class WalletPaymentState { SuccessOnChain, SuccessOffChain, PendingOnChain, PendingOffChain, Failure }

fun WalletPayment.id(): String = when (this) {
    is OutgoingPayment -> this.id.toString()
    is IncomingPayment -> this.paymentHash.toHex()
}

fun WalletPayment.state(): WalletPaymentState = when (this) {
    is InboundLiquidityOutgoingPayment -> when (lockedAt) {
        null -> WalletPaymentState.PendingOnChain
        else -> WalletPaymentState.SuccessOnChain
    }
    is OnChainOutgoingPayment -> when (confirmedAt) {
        null -> WalletPaymentState.PendingOnChain
        else -> WalletPaymentState.SuccessOnChain
    }
    is LightningOutgoingPayment -> when (status) {
        is LightningOutgoingPayment.Status.Pending -> WalletPaymentState.PendingOffChain
        is LightningOutgoingPayment.Status.Completed.Succeeded.OffChain -> WalletPaymentState.SuccessOffChain
        is LightningOutgoingPayment.Status.Completed.Failed -> WalletPaymentState.Failure
    }
    is IncomingPayment -> when (val r = received) {
        null -> WalletPaymentState.PendingOffChain
        else -> when {
            r.receivedWith.isEmpty() -> WalletPaymentState.PendingOnChain
            r.receivedWith.any { it is IncomingPayment.ReceivedWith.OnChainIncomingPayment } -> when (completedAt) {
                null -> WalletPaymentState.PendingOnChain
                else -> WalletPaymentState.SuccessOnChain
            }
            else -> when (completedAt) {
                null -> WalletPaymentState.PendingOffChain
                else -> WalletPaymentState.SuccessOffChain
            }
        }
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

fun WalletPayment.incomingOfferMetadata(): OfferPaymentMetadata.V1? = ((this as? IncomingPayment)?.origin as? IncomingPayment.Origin.Offer)?.metadata as? OfferPaymentMetadata.V1
fun WalletPayment.outgoingInvoiceRequest(): OfferTypes.InvoiceRequest? = ((this as? LightningOutgoingPayment)?.details as? LightningOutgoingPayment.Details.Blinded)?.paymentRequest?.invoiceRequest
