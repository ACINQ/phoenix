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
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.data.lnurl.Lnurl
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
    fun readBolt11Invoice(input: String): Bolt11Invoice? {
        return when (val res = Bolt11Invoice.read(trimMatchingPrefix(removeExcessInput(input), lightningPrefixes))) {
            is Try.Success -> res.get()
            is Try.Failure -> null
        }
    }

    fun readOffer(input: String): OfferTypes.Offer? {
        val cleanInput = trimMatchingPrefix(removeExcessInput(input), lightningPrefixes)
        return when (val res = OfferTypes.Offer.decode(cleanInput)) {
            is Try.Success -> res.get()
            is Try.Failure -> {
                null
            }
        }
    }

    fun parseEmailLikeAddress(input: String): EmailLikeAddress? {
        if (!input.contains("@", ignoreCase = true)) return null

        // Ignore excess input, including additional lines, and leading/trailing whitespace
        val line = input.lines().firstOrNull { it.isNotBlank() }?.trim()
        val token = line?.split("\\s+".toRegex())?.firstOrNull()?.let {
            trimMatchingPrefix(it, bitcoinPrefixes + lightningPrefixes + lnurlPrefixes)
        }

        if (token.isNullOrBlank()) return null

        val components = token.split("@")
        if (components.size != 2) return null

        val username = components[0].lowercase()
            .replace("%E2%82%BF", "₿", ignoreCase = true) // the Bitcoin char may be url-encoded
        val domain = components[1]

        if (username.isBlank() || domain.isBlank()) return null

        return if (username.startsWith("₿")) {
            EmailLikeAddress.Bip353(token, username.dropWhile { it == '₿' }, domain)
        } else {
            EmailLikeAddress.UnknownType(token, username, domain)
        }
    }

    /**
     * Parses an input and returns a bip-21 [BitcoinUri] if it is valid, or a typed error otherwise.
     *
     * @param chain the chain this parser expects the address to be valid on.
     * @param input can range from a basic bitcoin address to a sophisticated Bitcoin URI with a prefix and parameters.
     */
    fun parseBip21Uri(
        chain: Chain,
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
            when (val res = Bolt11Invoice.read(it)) {
                is Try.Success -> {
                    val invoiceChain = res.result.chain
                    if (invoiceChain != chain) {
                        if (address.isBlank()) {
                            return Either.Left(BitcoinUriError.InvalidScript(BitcoinError.ChainHashMismatch))
                        } else {
                            null
                        }
                    } else {
                        res.result
                    }
                }
                is Try.Failure -> null
            }
        }
        val offer = url.parameters["lno"]?.let {
            when (val res = OfferTypes.Offer.decode(it)) {
                is Try.Success -> {
                    if (!res.result.chains.contains(chain.chainHash)) {
                        if (address.isBlank()) {
                            return Either.Left(BitcoinUriError.InvalidScript(BitcoinError.ChainHashMismatch))
                        } else {
                            null
                        }
                    } else {
                        res.result
                    }
                }
                is Try.Failure -> null
            }
        }
        val otherParams = ParametersBuilder().apply {
            appendAll(url.parameters.filter { entry, _ ->
                !listOf("amount", "label", "message", "lightning", "lno").contains(entry)
            })
        }.build()

        val scriptParse = address.takeIf { it.isNotBlank() }?.let {
            Bitcoin.addressToPublicKeyScript(chain.chainHash, address)
        }
        return when (scriptParse) {
            is Either.Left -> Either.Left(BitcoinUriError.InvalidScript(scriptParse.left))
            else -> Either.Right(
                BitcoinUri(
                    chain = chain, address = address, script = scriptParse?.right?.let { Script.write(it) }?.byteVector(), label = label,
                    message = message, amount = amount, paymentRequest = lightning, offer = offer, ignoredParams = otherParams,
                )
            )
        }
    }

    /** Transforms a bitcoin address into a public key script if valid, otherwise returns null. */
    fun addressToPublicKeyScriptOrNull(chain: Chain, address: String): ByteVector? {
        return parseBip21Uri(chain, address).right?.script
    }
}

sealed class EmailLikeAddress {
    abstract val source: String
    abstract val username: String
    abstract val domain: String
    data class UnknownType(override val source: String, override val username: String, override val domain: String) : EmailLikeAddress()
    data class LnurlBased(override val source: String, override val username: String, override val domain: String) : EmailLikeAddress() {
        val url = Lnurl.Request(Url("https://$domain/.well-known/lnurlp/$username"), tag = Lnurl.Tag.Pay)
    }
    data class Bip353(override val source: String, override val username: String, override val domain: String) : EmailLikeAddress()
}