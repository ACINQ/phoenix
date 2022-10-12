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
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.data.*
import io.ktor.http.*
import io.ktor.util.*

object Parser {

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
    ): PaymentRequest? = try {
        PaymentRequest.read(trimMatchingPrefix(removeExcessInput(input), listOf("lightning://", "lightning:", "bitcoin://", "bitcoin:")))
    } catch (t: Throwable) {
        null
    }

    /**
     * Parses an input and returns a [BitcoinAddressInfo] if it is valid, or a typed error otherwise.
     *
     * @param chain the chain this parser expects the address to be valid on.
     * @param input can range from a basic bitcoin address to a sophisticated Bitcoin URI with a prefix and parameters.
     */
    fun readBitcoinAddress(
        chain: Chain,
        input: String
    ): Either<BitcoinAddressError, BitcoinAddressInfo> {
        val cleanInput = removeExcessInput(input)
        val url = try {
            Url(cleanInput)
        } catch (e: Exception) {
            return Either.Left(BitcoinAddressError.UnknownFormat)
        }

        // -- get address
        // The input might look like: bitcoin:tb1qla78tll0eua3l5f4nvfq3tx58u35yc3m44flfu?time=1618931109&exp=604800
        // We want to parse the parameters and the address. However the Url api lacks a simple property to extract an address.
        val address = trimMatchingPrefix(cleanInput, listOf("bitcoin://", "bitcoin:")).substringBefore("?")

        // -- read parameters
        val requiredParams = url.parameters.entries().filter { it.key.startsWith("req-") }.map { it.key to it.value.joinToString(";") }
        if (requiredParams.isNotEmpty()) {
            return Either.Left(BitcoinAddressError.UnhandledRequiredParams(requiredParams))
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
            try {
                PaymentRequest.read(it)
            } catch (e: Exception) {
                null
            }
        }
        val otherParams = ParametersBuilder().apply {
            appendAll(url.parameters.filter { entry, value ->
                !listOf("amount", "label", "message", "lightning").contains(entry)
            })
        }.build()

        // -- check address type
        try { // is Base58 ?
            val (prefix, bin) = Base58Check.decode(address)
            val (prefixChain, prefixType) = when (prefix) {
                Base58.Prefix.PubkeyAddress -> Chain.Mainnet to BitcoinAddressType.Base58PubKeyHash
                Base58.Prefix.ScriptAddress -> Chain.Mainnet to BitcoinAddressType.Base58ScriptHash
                Base58.Prefix.PubkeyAddressTestnet -> Chain.Testnet to BitcoinAddressType.Base58PubKeyHash
                Base58.Prefix.ScriptAddressTestnet -> Chain.Testnet to BitcoinAddressType.Base58ScriptHash
                else -> return Either.Left(BitcoinAddressError.UnknownBase58Prefix(prefix))
            }
            if (prefixChain != chain) {
                return Either.Left(BitcoinAddressError.ChainMismatch(chain, prefixChain))
            }
            return Either.Right(
                BitcoinAddressInfo(address, prefixChain, prefixType, bin.byteVector(), label, message, amount, lightning, otherParams)
            )
        } catch (e: Exception) {
            // Not Base58
        }

        try { // is Bech32 ?
            val (hrp, version, bin) = Bech32.decodeWitnessAddress(address)
            val prefixChain = when (hrp) {
                "bc" -> Chain.Mainnet
                "tb" -> Chain.Testnet
                "bcrt" -> Chain.Regtest
                else -> return Either.Left(BitcoinAddressError.UnknownBech32Prefix(hrp))
            }

            if (prefixChain != chain) {
                return Either.Left(BitcoinAddressError.ChainMismatch(chain, prefixChain))
            }

            if (version == 0.toByte()) {
                val type = when (bin.size) {
                    20 -> BitcoinAddressType.SegWitPubKeyHash
                    32 -> BitcoinAddressType.SegWitScriptHash
                    else -> return Either.Left(BitcoinAddressError.UnknownFormat)
                }
                return Either.Right(
                    BitcoinAddressInfo(address, prefixChain, type, bin.byteVector(), label, message, amount, lightning, otherParams)
                )
            } else {
                // Unknown version - we don't have any validation logic in place for it
                return Either.Left(BitcoinAddressError.UnknownBech32Version(version))
            }
        } catch (e: Throwable) {
            // Not Bech32
        }

        return Either.Left(BitcoinAddressError.UnknownFormat)
    }

    /** Transforms a bitcoin address into a public key script if valid, otherwise returns null. */
    fun addressToPublicKeyScript(chain: Chain, address: String): ByteArray? {
        val info = readBitcoinAddress(chain, address).right ?: return null
        val script = when (info.type) {
            BitcoinAddressType.Base58PubKeyHash -> Script.pay2pkh(
                pubKeyHash = info.hash.toByteArray()
            )
            BitcoinAddressType.Base58ScriptHash -> {
                // We cannot use Script.pay2sh() here, because that function expects a script.
                // And what we have is a script hash.
                listOf(OP_HASH160, OP_PUSHDATA(info.hash), OP_EQUAL)
            }
            BitcoinAddressType.SegWitPubKeyHash -> Script.pay2wpkh(
                pubKeyHash = info.hash.toByteArray()
            )
            BitcoinAddressType.SegWitScriptHash -> {
                // We cannot use Script.pay2wsh() here, because that function expects a script.
                // And what we have is a script hash.
                listOf(OP_0, OP_PUSHDATA(info.hash))
            }
        }
        return Script.write(script)
    }
}