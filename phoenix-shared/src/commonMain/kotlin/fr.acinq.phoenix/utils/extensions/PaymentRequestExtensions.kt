/*
 * Copyright 2023 ACINQ SAS
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

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.lightning.payment.OfferPaymentMetadata
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.payment.UnverifiedContactAddress
import fr.acinq.lightning.wire.OfferTypes


/**
 * In Objective-C, the function name `description()` is already in use (part of NSObject).
 * So we need to alias it.
 */
fun Bolt11Invoice.desc(): String? = this.description

val PaymentRequest.desc: String?
    get() = when (this) {
        is Bolt11Invoice -> this.description
        is Bolt12Invoice -> this.description
    }

val OfferPaymentMetadata.description: String?
    get() = when (this) {
        is OfferPaymentMetadata.V1 -> null
        is OfferPaymentMetadata.V2 -> this.description
        is OfferPaymentMetadata.V3 -> this.description
    }

val OfferPaymentMetadata.payerKey: PublicKey?
    get() = when (this) {
        is OfferPaymentMetadata.V1 -> this.payerKey
        is OfferPaymentMetadata.V2 -> this.payerKey
        is OfferPaymentMetadata.V3 -> this.payerKey
    }

val OfferPaymentMetadata.payerNote: String?
    get() = when (this) {
        is OfferPaymentMetadata.V1 -> this.payerNote
        is OfferPaymentMetadata.V2 -> this.payerNote
        is OfferPaymentMetadata.V3 -> this.payerNote
    }

val OfferPaymentMetadata.contactSecret: ByteVector32?
    get() = when (this) {
        is OfferPaymentMetadata.V1 -> null
        is OfferPaymentMetadata.V2 -> null
        is OfferPaymentMetadata.V3 -> this.contactSecret
    }

val OfferPaymentMetadata.payerOffer: OfferTypes.Offer?
    get() = when (this) {
        is OfferPaymentMetadata.V1 -> null
        is OfferPaymentMetadata.V2 -> null
        is OfferPaymentMetadata.V3 -> this.payerOffer
    }

val OfferPaymentMetadata.payerAddress: UnverifiedContactAddress?
    get() = when (this) {
        is OfferPaymentMetadata.V1 -> null
        is OfferPaymentMetadata.V2 -> null
        is OfferPaymentMetadata.V3 -> this.payerAddress
    }
