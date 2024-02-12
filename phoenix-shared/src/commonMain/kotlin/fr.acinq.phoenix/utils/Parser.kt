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

package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.*
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.Try
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.data.*
import io.ktor.http.*
import io.ktor.util.*

object Parser {

    /** Order matters, as the prefixes are matched with startsWith. Longest prefixes should be at the beginning to avoid trimming only a part of the prefix. */
    val lightningPrefixes = listOf(
        "phoenix:lightning://",
        "phoenix:lightning:",
        "lightning://",
        "lightning:",
    )

    val bitcoinPrefixes = listOf(
        "phoenix:bitcoin://",
        "phoenix:bitcoin:",
        "bitcoin://",
        "bitcoin:"
    )

    val lnurlPrefixes = listOf(
        "phoenix:lnurl://",
        "phoenix:lnurl:",
        "lnurl://",
        "lnurl:",
    )

    fun removeExcessInput(input: String) = input.lines().firstOrNull { it.isNotBlank() }?.replace("\\u00A0", "")?.trim() ?: ""

    /**
     * Remove the prefix from the input, if any. Trimming is done in a case-insensitive manner because often QR codes will
     * use upper-case for the prefix, such as LIGHTNING:LNURL1...
     */
    fun trimMatchingPrefix(
        input: String,
        prefixes: List<String>
    ): String {
        val matchingPrefix = prefixes.firstOrNull { input.startsWith(it, ignoreCase = true) }
        return if (matchingPrefix != null) {
            input.drop(matchingPrefix.length)
        } else {
            input
        }
    }

    /** Reads a payment request after stripping prefixes. Return null if input is invalid. */
    fun readPaymentRequest(
        input: String
    ): PaymentRequest? = when (val res = PaymentRequest.read(trimMatchingPrefix(removeExcessInput(input), lightningPrefixes))) {
        is Try.Success -> res.get()
        is Try.Failure -> null
    }

    /**
     * Parses an input and returns a [BitcoinUri] if it is valid, or a typed error otherwise.
     *
     * @param chain the chain this parser expects the address to be valid on.
     * @param input can range from a basic bitcoin address to a sophisticated Bitcoin URI with a prefix and parameters.
     */
    fun readBitcoinAddress(
        chain: NodeParams.Chain,
        input: String
    ): Either<BitcoinUriError, BitcoinUri> {
        val cleanInput = removeExcessInput(input)
        val url = try {
            Url(cleanInput)
        } catch (e: Exception) {
            return Either.Left(BitcoinUriError.InvalidUri)
        }

        // -- get address
        // The input might look like: bitcoin:tb1qla78tll0eua3l5f4nvfq3tx58u35yc3m44flfu?time=1618931109&exp=604800
        // We want to parse the parameters and the address. However the Url api lacks a simple property to extract an address.
        val address = trimMatchingPrefix(cleanInput, bitcoinPrefixes).substringBefore("?")

        // -- read parameters
        val requiredParams = url.parameters.entries().filter { it.key.startsWith("req-") }.map { it.key to it.value.joinToString(";") }
        if (requiredParams.isNotEmpty()) {
            return Either.Left(BitcoinUriError.UnhandledRequiredParams(requiredParams))
        }

        val amountSplit = url.parameters["amount"]?.trim()?.split(".", ignoreCase = true, limit = 2)
        val btcPart = amountSplit?.first()
        val satPart = amountSplit?.last()?.take(8)?.padEnd(8, '0')
        val amount = when {
            btcPart != null && satPart != null -> btcPart + satPart
            btcPart != null && satPart == null -> btcPart + "00000000"
            btcPart == null && satPart != null -> satPart
            else -> null
        }?.toLongOrNull()?.takeIf { it > 0L && it <= 21e14 }?.sat

        val label = url.parameters["label"]
        val message = url.parameters["message"]
        val lightning = url.parameters["lightning"]?.let {
            when (val res = PaymentRequest.read(it)) {
                is Try.Success -> res.get()
                is Try.Failure -> null
            }
        }
        val otherParams = ParametersBuilder().apply {
            appendAll(url.parameters.filter { entry, _ ->
                !listOf("amount", "label", "message", "lightning").contains(entry)
            })
        }.build()

        return when (val res = Bitcoin.addressToPublicKeyScript(chain.chainHash, address)) {
            is Either.Left -> Either.Left(BitcoinUriError.InvalidScript(res.left))
            is Either.Right -> Either.Right(BitcoinUri(chain, address, res.right.let { Script.write(it) }.byteVector(), label, message, amount, lightning, otherParams))
        }
    }

    /** Transforms a bitcoin address into a public key script if valid, otherwise returns null. */
    fun addressToPublicKeyScriptOrNull(chain: NodeParams.Chain, address: String): ByteVector? {
        return readBitcoinAddress(chain, address).right?.script
    }
}