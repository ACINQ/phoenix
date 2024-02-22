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

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.Feature
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.lightning.payment.PaymentRequest

fun Bolt11Invoice.isAmountlessTrampoline() = this.amount == null && this.features.hasFeature(Feature.TrampolinePayment)

/**
 * In Objective-C, the function name `description()` is already in use (part of NSObject).
 * So we need to alias it.
 */
fun Bolt11Invoice.desc(): String? = this.description

val PaymentRequest.chain: Chain
    get() = when (this) {
        is Bolt11Invoice -> {
            when (prefix) {
                "lnbc" -> Chain.Mainnet
                "lntb" -> Chain.Testnet
                "lnbcrt" -> Chain.Regtest
                else -> throw IllegalArgumentException("unhandled invoice prefix=$prefix")
            }
        }
        is Bolt12Invoice -> TODO()
    }

val PaymentRequest.nodeId: PublicKey
    get() = when (this) {
        is Bolt11Invoice -> this.nodeId
        is Bolt12Invoice -> this.nodeId
    }

val PaymentRequest.desc: String?
    get() = when (this) {
        is Bolt11Invoice -> this.description
        is Bolt12Invoice -> this.description
    }