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

package fr.acinq.phoenix.data

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.payment.PaymentRequest
import io.ktor.http.*

enum class BitcoinAddressType {
    Base58PubKeyHash,
    Base58ScriptHash,
    SegWitPubKeyHash,
    SegWitScriptHash
}

data class BitcoinAddressInfo(
    /** Actual Bitcoin address, may be different than input; e.g. when input is an URI like "bitcoin:xyz?param=123". */
    val address: String,
    val chain: NodeParams.Chain,
    val type: BitcoinAddressType,
    val hash: ByteVector,
    // Bip-21 parameters
    val label: String? = null,
    val message: String? = null,
    /** Amount requested in the URI. */
    val amount: Satoshi? = null,
    /** A Bitcoin URI may contain a Lightning payment request as an alternative way to make the payment. */
    val paymentRequest: PaymentRequest? = null,
    /** Other bip-21 parameters in the URI that we do not handle. */
    val ignoredParams: Parameters = Parameters.Empty,
) {
    fun write(): String {
        val params = Parameters.build {
            label?.let { append("label", it) }
            message?.let { append("message", it) }
            // amount field is converted to BTC
            amount?.sat?.toString()?.padStart(9, '0')?.let {
                val satPart = it.takeLast(8)
                val btcPart = it.substring(0, it.length - 8)
                append("amount", "${btcPart}.${satPart}")
            }
            paymentRequest?.let { append("lightning", it.write()) }
        }
        return "bitcoin:$address?${(params + ignoredParams).formUrlEncode()}"
    }
}

sealed class BitcoinAddressError {
    data class ChainMismatch(val myChain: NodeParams.Chain, val addrChain: NodeParams.Chain): BitcoinAddressError()
    data class UnknownBase58Prefix(val prefix: Byte): BitcoinAddressError()
    data class UnknownBech32Prefix(val hrp: String): BitcoinAddressError()
    data class UnknownBech32Version(val version: Byte): BitcoinAddressError()
    data class UnhandledRequiredParams(val parameters: List<Pair<String, String>>): BitcoinAddressError()
    object UnknownFormat: BitcoinAddressError()
}
