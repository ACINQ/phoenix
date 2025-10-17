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

@file:Suppress("DEPRECATION")

package fr.acinq.phoenix.utils.extensions

import fr.acinq.lightning.db.AutomaticLiquidityPurchasePayment
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.LegacyPayToOpenIncomingPayment
import fr.acinq.lightning.db.LegacySwapInIncomingPayment
import fr.acinq.lightning.db.LightningIncomingPayment
import fr.acinq.lightning.db.LightningOutgoingPayment
import fr.acinq.lightning.db.ManualLiquidityPurchasePayment
import fr.acinq.lightning.db.NewChannelIncomingPayment
import fr.acinq.lightning.db.OnChainOutgoingPayment
import fr.acinq.lightning.db.SpliceInIncomingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.lightning.wire.OfferTypes

enum class WalletPaymentState { SuccessOnChain, SuccessOffChain, PendingOnChain, PendingOffChain, Failure }

fun WalletPayment.state(): WalletPaymentState = when (this) {
    is ManualLiquidityPurchasePayment -> when (lockedAt) {
        null -> WalletPaymentState.PendingOnChain
        else -> WalletPaymentState.SuccessOnChain
    }
    is AutomaticLiquidityPurchasePayment -> when (lockedAt) {
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

fun WalletPayment.incomingOfferMetadata(): OfferPaymentMetadata? = (this as? Bolt12IncomingPayment)?.metadata
fun WalletPayment.outgoingInvoiceRequest(): OfferTypes.InvoiceRequest? = ((this as? LightningOutgoingPayment)?.details as? LightningOutgoingPayment.Details.Blinded)?.paymentRequest?.invoiceRequest
