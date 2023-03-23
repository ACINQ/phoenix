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
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.utils.Parser
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BitcoinAddressTest {
    @Test
    fun test_bitcoin_address_info_write_read() {

        val testCases = listOf(
            BitcoinUri(
                address = "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
                chain = Chain.Testnet,
                type = BitcoinAddressType.SegWitScriptHash,
                hash = ByteVector.fromHex("1863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262"),
                label = "lorem ipsum",
                message = "dolor sit amet",
                amount = 123_456_789.sat,
                paymentRequest = PaymentRequest.read("lntb10u1p3f5y7vpp5kp4rp945z3xp83ef34zhnunf36z0k03ps0e5m33dy8jhux2ucqtsdq5xysyymr0ddskxcmfdehsxqrrsscqp79qy9qsqsp5j3hxmhcyhk6t57fsms6upgm7jkr8nyw6jwjuy8wepvvnrdl3gwts0cv85hddt2xvwszga7fqy7559ryk4v2kwfjj8ykr3nwg479v03dxtugzwzyw233nnmhk6uffkld0cca3rex4ggmp7jatnr6f089rnlgq6c9k3f"),
                ignoredParams = Parameters.build {
                    append("foo", "bar")
                    append("foo2", 1234L.toString())
                }
            ),
            BitcoinUri(
                address = "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
                chain = Chain.Testnet,
                type = BitcoinAddressType.SegWitScriptHash,
                hash = ByteVector.fromHex("1863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262"),
                label = "",
                message = null,
                amount = 1.sat,
                paymentRequest = null,
                ignoredParams = Parameters.Empty
            ),
            BitcoinUri(
                address = "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
                chain = Chain.Testnet,
                type = BitcoinAddressType.SegWitScriptHash,
                hash = ByteVector.fromHex("1863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262"),
                label = "max amount",
                message = null,
                amount = 21_000_000_000_00000.sat,
                paymentRequest = null,
                ignoredParams = Parameters.Empty
            ),
            BitcoinUri(
                address = "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
                chain = Chain.Testnet,
                type = BitcoinAddressType.SegWitScriptHash,
                hash = ByteVector.fromHex("1863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262"),
                label = null,
                message = null,
                amount = null,
                paymentRequest = null,
                ignoredParams = Parameters.Empty
            )
        )

        testCases.forEach {
            val serialized = it.write()
            val decoded = Parser.readBitcoinAddress(Chain.Testnet, serialized)
            assertTrue { decoded is Either.Right }
            assertEquals(it, decoded.right!!)
        }

    }
}