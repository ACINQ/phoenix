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

import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.InboundLiquidityOutgoingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.OnChainOutgoingPayment
import fr.acinq.lightning.db.SpliceInIncomingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.LiquidityAds
import fr.acinq.lightning.wire.OfferTypes

enum class WalletPaymentState { SuccessOnChain, SuccessOffChain, PendingOnChain, PendingOffChain, Failure }

@Suppress("DEPRECATION")
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
        is LightningOutgoingPayment.Status.Succeeded -> WalletPaymentState.SuccessOffChain
        is LightningOutgoingPayment.Status.Failed -> WalletPaymentState.Failure
    }
    is LightningIncomingPayment, is LegacyPayToOpenIncomingPayment -> when (completedAt) {
        null -> WalletPaymentState.PendingOffChain
        else -> WalletPaymentState.SuccessOffChain
    }
    is SpliceInIncomingPayment, is NewChannelIncomingPayment, is LegacySwapInIncomingPayment -> when (completedAt) {
        null -> WalletPaymentState.PendingOnChain
        else -> WalletPaymentState.SuccessOnChain
    }
}

fun WalletPayment.errorMessage(): String? = when (this) {
    is OnChainOutgoingPayment -> null
    is LightningOutgoingPayment -> when (val s = status) {
        is LightningOutgoingPayment.Status.Failed -> s.reason.toString()
        else -> null
    }
    is IncomingPayment -> null
}

fun WalletPayment.incomingOfferMetadata(): OfferPaymentMetadata.V1? = (this as? Bolt12IncomingPayment)?.metadata as? OfferPaymentMetadata.V1
fun WalletPayment.outgoingInvoiceRequest(): OfferTypes.InvoiceRequest? = ((this as? LightningOutgoingPayment)?.details as? LightningOutgoingPayment.Details.Blinded)?.paymentRequest?.invoiceRequest

/** Returns a list of the ids of the payments that triggered this liquidity purchase. May be empty, for example if this is a manual purchase. */
fun InboundLiquidityOutgoingPayment.relatedPaymentIds() : List<UUID> = when (val details = purchase.paymentDetails) {
    is LiquidityAds.PaymentDetails.FromFutureHtlc -> details.paymentHashes
    is LiquidityAds.PaymentDetails.FromChannelBalanceForFutureHtlc -> details.paymentHashes
    else -> emptyList()
}.map { it.deriveUUID() }

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
