/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.managers

import fr.acinq.phoenix.managers.Utilities.BitcoinAddressType
import fr.acinq.phoenix.managers.Utilities.BitcoinAddressError
import fr.acinq.phoenix.data.Chain
import io.ktor.http.*
import org.kodein.log.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UtilitiesTest {

    data class TestInput(
        val addr: String,
        val chain: Chain,
        val type: BitcoinAddressType,
        val isValid: Boolean = true
    )

    val testInputs: List<TestInput> = listOf(
        TestInput("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem",
            Chain.Mainnet, BitcoinAddressType.Base58PubKeyHash
        ),
        TestInput("3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX",
            Chain.Mainnet, BitcoinAddressType.Base58ScriptHash
        ),
        TestInput("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            Chain.Mainnet, BitcoinAddressType.SegWitPubKeyHash
        ),
        TestInput("bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3",
            Chain.Mainnet, BitcoinAddressType.SegWitScriptHash
        ),
        TestInput("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn",
            Chain.Testnet, BitcoinAddressType.Base58PubKeyHash
        ),
        TestInput("2MzQwSSnBHWHqSAqtTVQ6v47XtaisrJa1Vc",
            Chain.Testnet, BitcoinAddressType.Base58ScriptHash
        ),
        TestInput("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx",
            Chain.Testnet, BitcoinAddressType.SegWitPubKeyHash
        ),
        TestInput("tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
            Chain.Testnet, BitcoinAddressType.SegWitScriptHash,
        ),
        TestInput("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhe",
            Chain.Testnet, BitcoinAddressType.Base58PubKeyHash, isValid = false
        ),
        TestInput("bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            Chain.Mainnet, BitcoinAddressType.SegWitPubKeyHash
        ),
        TestInput("bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?foo=bar",
            Chain.Mainnet, BitcoinAddressType.SegWitPubKeyHash
        ),
        TestInput("bitcoin://bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            Chain.Mainnet, BitcoinAddressType.SegWitPubKeyHash
        ),
        TestInput("bitcoin://bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?foo=bar",
            Chain.Mainnet, BitcoinAddressType.SegWitPubKeyHash
        )
    )

    @Test
    fun bitcoinAddressParsing() {

        val loggerFactory = LoggerFactory.default

        val chains = listOf(Chain.Mainnet, Chain.Testnet)
        for (chain in chains) {

            val util = Utilities(loggerFactory, chain)

            for (input in testInputs) {
                val result = util.parseBitcoinAddress(input.addr)

                if (!input.isValid) {
                    val error: BitcoinAddressError? = result.left
                    assertNotNull(error)
                }
                else if (input.chain != chain) {
                    val error: BitcoinAddressError? = result.left
                    assertNotNull(error)

                    assertTrue { error is BitcoinAddressError.ChainMismatch }
                    val mismatch = error as BitcoinAddressError.ChainMismatch

                    assertTrue { mismatch.myChain == chain }
                    assertTrue { mismatch.addrChain == input.chain }
                }
                else {
                    val info: Utilities.BitcoinAddressInfo? = result.right
                    assertNotNull(info)

                    assertTrue { info.chain == input.chain }
                    assertTrue { info.type == input.type }
                }
            }

        }
    }

    @Test
    fun bitcoinParametersParsing() {

        val loggerFactory = LoggerFactory.default
        val util = Utilities(loggerFactory, Chain.Mainnet)

        val result1 = util.parseBitcoinAddress(
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?foo=bar"
        )

        val info1 = result1.right
        assertNotNull(info1)
        assertTrue { info1.params.get("foo") == "bar" }

        val result2 = util.parseBitcoinAddress(
            "bitcoin://bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?foo=bar&lightning=abc123"
        )

        val info2 = result2.right
        assertNotNull(info2)

        assertTrue { info2.params.get("foo") == "bar" }
        assertTrue { info2.params.get("lightning") == "abc123" }
    }
}