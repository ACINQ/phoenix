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


import fr.acinq.bitcoin.Bitcoin
import fr.acinq.bitcoin.BitcoinError
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.data.BitcoinUriError
import fr.acinq.phoenix.data.BitcoinUri
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserTest {

    @Test
    fun parse_bitcoin_uri_with_valid_addresses() {
        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem"))
        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX"))
        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3"))

        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Testnet, "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn"))
        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Testnet, "2MzQwSSnBHWHqSAqtTVQ6v47XtaisrJa1Vc"))
        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Testnet, "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"))
        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Testnet, "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7"))
        assertIs<Either.Right<BitcoinUri>>(Parser.readBitcoinAddress(Bitcoin.Chain.Testnet, "tb1p607g5ea77m370pey3y5rg58fz7542hnpg40rs2cqw6w69yt5lf2qlktj2a"))
    }

    @Test
    fun parse_bitcoin_uri_chain_mismatch() {
        assertEquals(
            expected = Either.Left(BitcoinUriError.InvalidScript(error = BitcoinError.ChainHashMismatch)),
            actual = Parser.readBitcoinAddress(Bitcoin.Chain.Testnet, "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3")
        )
        assertEquals(
            expected = Either.Left(BitcoinUriError.InvalidScript(error = BitcoinError.ChainHashMismatch)),
            actual = Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")
        )
    }

    @Test
    fun parse_bitcoin_uri_with_invalid_addresses() {
        assertIs<Either.Left<BitcoinUriError.InvalidScript>>(
            Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhe")
        )
    }

    @Test
    fun parse_bitcoin_uri_with_bitcoin_prefixes() {
        assertIs<Either.Right<BitcoinUri>>(
            Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
        assertIs<Either.Right<BitcoinUri>>(
            Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "bitcoin://bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
    }

    @Test
    fun parse_bitcoin_uri_with_non_bitcoin_prefixes() {
        // non-bitcoin prefixes are not trimmed, so error is invalid script
        assertIs<Either.Left<BitcoinUriError.InvalidScript>>(
            Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "btc:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
        assertIs<Either.Left<BitcoinUriError.InvalidScript>>(
            Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "lightning:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
        assertIs<Either.Left<BitcoinUriError.InvalidScript>>(
            Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, "lnurl://bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
    }

    @Test
    fun parse_bitcoin_uri_with_parameters() {
        listOf<Pair<String, Either<BitcoinUriError, BitcoinUri>>>(
            // ignore unhandled params
            "bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa?somethingyoudontunderstand=50&somethingelseyoudontget=999" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
                    script = ByteVector("76a91462e907b15cbf27d5425399ebf6f0fb50ebb88f1888ac"),
                    ignoredParams = ParametersBuilder().apply { set("somethingyoudontunderstand", "50"); set("somethingelseyoudontget", "999") }.build()
                )
            ),
            // ignore payjoin parameter
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?pj=https://acinq.co" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    ignoredParams = ParametersBuilder().apply { set("pj", "https://acinq.co") }.build()
                )
            ),
            // fail if uri contains required params we don't understand
            "bitcoin:175tWpb8K1S7NmH4Zx6rewF9WQrcZv245W?req-somethingyoudontunderstand=50&req-somethingelseyoudontget=999" to Either.Left(
                BitcoinUriError.UnhandledRequiredParams(parameters = listOf("req-somethingyoudontunderstand" to "50", "req-somethingelseyoudontget" to "999"))
            ),
        ).forEach {
            assertEquals(it.second, Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, it.first))
        }
    }

    @Test
    fun parse_bitcoin_uri_with_lightning_invoice() {
        listOf<Pair<String, Either<BitcoinUriError, BitcoinUri>>>(
            // valid lightning invoice
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?foo=bar&lightning=lntb15u1p05vazrpp5apz75ghtq3ynmc5qm98tsgucmsav44fyffpguhzdep2kcgkfme4sdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5v4hqr48qe0u7al6lxwdpmp3w6k7evjdavm0lh7arpv3qaf038s5st2d8k8vvmxyav2wkfym9jp4mk64srmswgh7l6sqtq7l4xl3nknf8snltamvpw5p3yl9nxg0ax9k0698rr94qx6unrv8yhccmh4z9ghcq77hxps" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    paymentRequest = PaymentRequest.read("lntb15u1p05vazrpp5apz75ghtq3ynmc5qm98tsgucmsav44fyffpguhzdep2kcgkfme4sdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5v4hqr48qe0u7al6lxwdpmp3w6k7evjdavm0lh7arpv3qaf038s5st2d8k8vvmxyav2wkfym9jp4mk64srmswgh7l6sqtq7l4xl3nknf8snltamvpw5p3yl9nxg0ax9k0698rr94qx6unrv8yhccmh4z9ghcq77hxps").get(),
                    ignoredParams = ParametersBuilder().apply { set("foo", "bar") }.build()
                )
            ),
            // invalid lightning invoice
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lightning=lntb15u1p05vazrpp" to Either.Right(
                BitcoinUri(chain = Bitcoin.Chain.Mainnet, address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"))
            ),
            // empty lightning invoice
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lightning=" to Either.Right(
                BitcoinUri(chain = Bitcoin.Chain.Mainnet, address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"))
            ),
        ).forEach { (address, expected) ->
            val uri = Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, address)
            assertEquals(expected, uri)
        }
    }

    @Test
    fun parse_bitcoin_uri_with_amount() {
        listOf<Pair<String, Either<BitcoinUriError, BitcoinUri>>>(
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.0123  " to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = 12_30000.sat
                )
            ),
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=1.23456789999" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = 1_234_56789.sat
                )
            ),
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=21000000.000" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = 21_000_000_000_00000.sat
                )
            ),
            // amount with invalid chars is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.001a2" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            ),
            // amount with two decimal separators is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.001.2" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            ),
            // amount with a comma separator is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0,0012" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            ),
            // amount < 1 sat is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.000000001" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            ),
            // amount > 21e6 btc is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=21000000.00000001" to Either.Right(
                BitcoinUri(
                    chain = Bitcoin.Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            )
        ).forEach {
            assertEquals(it.second, Parser.readBitcoinAddress(Bitcoin.Chain.Mainnet, it.first))
        }
    }

    @Test
    fun test_prefixes() {
        val lnurlw = "LNURL1DP68GURN8GHJ7MRWW4EXCTNXD9SHG6NPVCHXXMMD9AKXUATJDSKHW6T5DPJ8YCTH8AEK2UMND9HKU0FKVESNZDFEX4SNXENZV4JNWWF3VENXVV3H8YUXYE3JXQMNJCF4VYMNSCEKXFSKGC3S8YENVCEJVDJXXVRXXSUKGCMY8QERSCFKXFJRZ0FPS8D"
        val address = "3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX"
        val invoice = "lntb10u1pjfgej2pp5cr2l5v08ty6uymjhs4pgrpar2f4anwaefzjjam7u7zhnvjnr97nsdq5xysyymr0ddskxcmfdehsxqrrsscqp79qy9qsqsp58uwzthcgl8lmcp8s4f9du2pmxaf6rqxc2s80gtcm0u43e2qy3apqe9tj28qmhxdgzpd6ax2z344m6ct62f4updckhpww3jpuf08ldq4nejfvnqfem8vkw39r9jtndy3vqd63gwwfahfprk68n7xm4ypgmggq3v0sy8"
        listOf(
            address to "phoenix:bitcoin:$address",
            address to "phoenix:bitcoin://$address             ",
            address to "bitcoin://$address     ",
            address to "   bitcoin:$address",

            invoice to "phoenix:lightning:$invoice",
            invoice to " phoenix:lightning://$invoice",
            invoice to "    lightning:$invoice",
            invoice to "lightning://$invoice",

            lnurlw to " phoenix:lnurl:$lnurlw   ",
            lnurlw to "phoenix:lnurl://$lnurlw　",
            lnurlw to " lnurl:$lnurlw ",
            lnurlw to " lnurl://$lnurlw ",
        ).forEach { (payload, input) ->
            val trimmed = Parser.trimMatchingPrefix(Parser.removeExcessInput(input), Parser.bitcoinPrefixes + Parser.lightningPrefixes + Parser.lnurlPrefixes)
            assertEquals(payload, trimmed)
        }

        // lud-17 prefixes should NOT be trimmed - they are handled separately
        listOf(
            "keyauth:LNURL1DP68GURN8GHJ7MRWW4EXCTNXD9SHG6NPVCHXXMMD9AKXUATJDSKKCMM8D9HR7ARPVU7KCMM8D9HZV6E3843KYV3SVYCRZDN9V5URVWRZ89JRWCMRX3NXZVMPVDNRJVT9V5UKGDECV3JRYVFCXA3KGVNXX9NRSCN9XYERGDF4VFNXVCEEXFSN2WP482E5EP",
            "lnurp:LNURL1DP68GURN8GHJ7MRWW4EXCTNXD9SHG6NPVCHXXMMD9AKXUATJDSKHQCTE8AEK2UMND9HKU0TRVGERQCFSXYMX2EFCXCUXYWTYXA3KXDRXVYEKZCMX8YCK2EFEVSMNSERYXGCNSDMRVSEXVVTX8P3X2VFJXS6N2CNXVE3NJVNPX5UR242GXTQ",
            "lnurlw:LNURL1DP68GURN8GHJ7MRWW4EXCTNXD9SHG6NPVCHXXMMD9AKXUATJDSKHW6T5DPJ8YCTH8AEK2UMND9HKU0FKVESNZDFEX4SNXENZV4JNWWF3VENXVV3H8YUXYE3JXQMNJCF4VYMNSCEKXFSKGC3S8YENVCEJVDJXXVRXXSUKGCMY8QERSCFKXFJRZ0FPS8D",
        ).forEach { input ->
            val trimmed = Parser.trimMatchingPrefix(Parser.removeExcessInput(input), Parser.bitcoinPrefixes + Parser.lightningPrefixes + Parser.lnurlPrefixes)
            assertEquals(input, trimmed)
        }
    }

}