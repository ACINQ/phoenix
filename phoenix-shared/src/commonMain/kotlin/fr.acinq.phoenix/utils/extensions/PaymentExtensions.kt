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
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.OnChainOutgoingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.lightning.utils.getValue
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.utils.sum
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.WalletPaymentId

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

/**
 * Incoming payments may be received (in part or entirely) as a fee credit. This happens when an on-chain operation
 * would be necessary to complete the payment, but the amount received is too low to pay for this operation just yet.
 * The payment is then accepted, but the amount is accrued to a fee credit.
 *
 * This fee credit in the wallet is not part of the wallet's balance. It and can only be spent to pay future mining
 * or service fees. It serves as a buffer that allows the user to keep accepting incoming payments seamlessly.
 *
 * Most of the time, this value is null (i.e., the amount received goes to the balance).
 */
val IncomingPayment.amountFeeCredit : MilliSatoshi?
    get() = this.received?.receivedWith?.filterIsInstance<IncomingPayment.ReceivedWith.AddedToFeeCredit>()?.map { it.amountReceived }?.sum()

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

/** Returns a list of the ids of the payments that triggered this liquidity purchase. May be empty, for example if this is a manual purchase. */
fun InboundLiquidityOutgoingPayment.relatedPaymentIds() : List<WalletPaymentId> = when (val details = purchase.paymentDetails) {
    is LiquidityAds.PaymentDetails.FromFutureHtlc -> details.paymentHashes
    is LiquidityAds.PaymentDetails.FromChannelBalanceForFutureHtlc -> details.paymentHashes
    else -> emptyList()
}.map { WalletPaymentId.IncomingPaymentId(it) }

/**
 * Returns true if this liquidity was initiated manually by the user, false otherwise.
 *
 * FIXME: Dangerous!!
 * In general, FromChannelBalance only happens for manual purchases OR automated swap-ins with additional liquidity.
 * However, swap-ins do not **yet** request additional liquidity, so **for now** we can make a safe approximation.
 * Eventually, once swap-ins are upgraded to request liquidity, this will have to be fixed.
 */
fun InboundLiquidityOutgoingPayment.isManualPurchase(): Boolean =
    purchase.paymentDetails is LiquidityAds.PaymentDetails.FromChannelBalance &&
    purchase.amount > 1.sat

/**
 * Returns true if the liquidity fee was paid by an htlc in a future incoming payment. When that's the case, we should
 * not display the fees in the liquidity details screen to avoid confusion. Instead we should link to the payment whose
 * HTLCs paid the fees.
 */
fun InboundLiquidityOutgoingPayment.isPaidInTheFuture(): Boolean =
    feePaidFromChannelBalance.total == 0.sat

fun InboundLiquidityOutgoingPayment.isChannelCreationFromSwapIn(): Boolean =
    purchase.paymentDetails is LiquidityAds.PaymentDetails.FromChannelBalance &&
    purchase.amount == 1.sat
