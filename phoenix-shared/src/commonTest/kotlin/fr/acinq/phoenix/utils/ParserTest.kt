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


import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.BitcoinError
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.sat
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.BitcoinUriError
import fr.acinq.phoenix.data.BitcoinUri
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ParserTest {

    @Test
    fun parse_bitcoin_uri_with_valid_addresses() {
        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Mainnet, "17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem"))
        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Mainnet, "3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX"))
        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Mainnet, "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Mainnet, "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3"))

        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Testnet, "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn"))
        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Testnet, "2MzQwSSnBHWHqSAqtTVQ6v47XtaisrJa1Vc"))
        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Testnet, "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"))
        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Testnet, "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7"))
        assertIs<Either.Right<BitcoinUri>>(Parser.parseBip21Uri(Chain.Testnet, "tb1p607g5ea77m370pey3y5rg58fz7542hnpg40rs2cqw6w69yt5lf2qlktj2a"))
    }

    @Test
    fun parse_bitcoin_uri_chain_mismatch() {
        assertEquals(
            expected = Either.Left(BitcoinUriError.InvalidScript(error = BitcoinError.ChainHashMismatch)),
            actual = Parser.parseBip21Uri(Chain.Testnet, "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3")
        )
        assertEquals(
            expected = Either.Left(BitcoinUriError.InvalidScript(error = BitcoinError.ChainHashMismatch)),
            actual = Parser.parseBip21Uri(Chain.Mainnet, "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")
        )
    }

    @Test
    fun parse_bitcoin_uri_with_invalid_addresses() {
        assertIs<Either.Left<BitcoinUriError.InvalidScript>>(
            Parser.parseBip21Uri(Chain.Mainnet, "17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhe")
        )
    }

    @Test
    fun parse_bitcoin_uri_with_bitcoin_prefixes() {
        assertIs<Either.Right<BitcoinUri>>(
            Parser.parseBip21Uri(Chain.Mainnet, "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
        assertIs<Either.Right<BitcoinUri>>(
            Parser.parseBip21Uri(Chain.Mainnet, "bitcoin://bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
        assertIs<Either.Right<BitcoinUri>>(
            Parser.parseBip21Uri(
                Chain.Testnet,
                "bitcoin:?lno=lno1qgsyxjtl6luzd9t3pr62xr7eemp6awnejusgf6gw45q75vcfqqqqqqqsespexwyy4tcadvgg89l9aljus6709kx235hhqrk6n8dey98uyuftzdqrt2gkjvf2rj2vnt7m7chnmazen8wpur2h65ttgftkqaugy6ql9dcsyq39xc2g084xfn0s50zlh2ex22vvaqxqz3vmudklz453nns4d0624sqr8ux4p5usm22qevld4ydfck7hwgcg9wc3f78y7jqhc6hwdq7e9dwkhty3svq5ju4dptxtldjumlxh5lw48jsz6pnagtwrmeus7uq9rc5g6uddwcwldpklxexvlezld8egntua4gsqqy8auz966nksacdac8yv3maq6elp"
            )
        )
    }

    @Test
    fun parse_bitcoin_uri_with_non_bitcoin_prefixes() {
        // non-bitcoin prefixes are not trimmed, so error is invalid script
        assertIs<Either.Left<BitcoinUriError.InvalidScript>>(
            Parser.parseBip21Uri(Chain.Mainnet, "btc:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
        assertIs<Either.Left<BitcoinUriError.InvalidScript>>(
            Parser.parseBip21Uri(Chain.Mainnet, "lightning:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
        assertIs<Either.Left<BitcoinUriError.InvalidScript>>(
            Parser.parseBip21Uri(Chain.Mainnet, "lnurl://bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        )
    }

    @Test
    fun parse_bitcoin_uri_with_parameters() {
        listOf<Pair<String, Either<BitcoinUriError, BitcoinUri>>>(
            // ignore unhandled params
            "bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa?somethingyoudontunderstand=50&somethingelseyoudontget=999" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
                    script = ByteVector("76a91462e907b15cbf27d5425399ebf6f0fb50ebb88f1888ac"),
                    ignoredParams = ParametersBuilder().apply { set("somethingyoudontunderstand", "50"); set("somethingelseyoudontget", "999") }.build()
                )
            ),
            // ignore payjoin parameter
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?pj=https://acinq.co" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
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
            assertEquals(it.second, Parser.parseBip21Uri(Chain.Mainnet, it.first))
        }
    }

    @Test
    fun parse_bitcoin_uri_with_lightning_invoice() {
        listOf<Pair<String, Either<BitcoinUriError, BitcoinUri>>>(
            // valid lightning invoice
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?foo=bar&lightning=lntb15u1p05vazrpp5apz75ghtq3ynmc5qm98tsgucmsav44fyffpguhzdep2kcgkfme4sdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5v4hqr48qe0u7al6lxwdpmp3w6k7evjdavm0lh7arpv3qaf038s5st2d8k8vvmxyav2wkfym9jp4mk64srmswgh7l6sqtq7l4xl3nknf8snltamvpw5p3yl9nxg0ax9k0698rr94qx6unrv8yhccmh4z9ghcq77hxps" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    paymentRequest = Bolt11Invoice.read("lntb15u1p05vazrpp5apz75ghtq3ynmc5qm98tsgucmsav44fyffpguhzdep2kcgkfme4sdq4xysyymr0vd4kzcmrd9hx7cqp2xqrrss9qy9qsqsp5v4hqr48qe0u7al6lxwdpmp3w6k7evjdavm0lh7arpv3qaf038s5st2d8k8vvmxyav2wkfym9jp4mk64srmswgh7l6sqtq7l4xl3nknf8snltamvpw5p3yl9nxg0ax9k0698rr94qx6unrv8yhccmh4z9ghcq77hxps")
                        .get(),
                    ignoredParams = ParametersBuilder().apply { set("foo", "bar") }.build()
                )
            ),
            // invalid lightning invoice
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lightning=lntb15u1p05vazrpp" to Either.Right(
                BitcoinUri(chain = Chain.Mainnet, address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"))
            ),
            // empty lightning invoice
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lightning=" to Either.Right(
                BitcoinUri(chain = Chain.Mainnet, address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"))
            ),
        ).forEach { (address, expected) ->
            val uri = Parser.parseBip21Uri(Chain.Mainnet, address)
            assertEquals(expected, uri)
        }
    }

    @Test
    fun parse_bitcoin_uri_with_amount() {
        listOf<Pair<String, Either<BitcoinUriError, BitcoinUri>>>(
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.0123  " to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = 12_30000.sat
                )
            ),
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=1.23456789999" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = 1_234_56789.sat
                )
            ),
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=21000000.000" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = 21_000_000_000_00000.sat
                )
            ),
            // amount with invalid chars is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.001a2" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            ),
            // amount with two decimal separators is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.001.2" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            ),
            // amount with a comma separator is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0,0012" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            ),
            // amount < 1 sat is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=0.000000001" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            ),
            // amount > 21e6 btc is ignored
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?amount=21000000.00000001" to Either.Right(
                BitcoinUri(
                    chain = Chain.Mainnet,
                    address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                    script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
                    amount = null
                )
            )
        ).forEach {
            assertEquals(it.second, Parser.parseBip21Uri(Chain.Mainnet, it.first))
        }
    }

    @Test
    fun test_prefixes() {
        val lnurlw = "LNURL1DP68GURN8GHJ7MRWW4EXCTNXD9SHG6NPVCHXXMMD9AKXUATJDSKHW6T5DPJ8YCTH8AEK2UMND9HKU0FKVESNZDFEX4SNXENZV4JNWWF3VENXVV3H8YUXYE3JXQMNJCF4VYMNSCEKXFSKGC3S8YENVCEJVDJXXVRXXSUKGCMY8QERSCFKXFJRZ0FPS8D"
        val address = "3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX"
        val invoice =
            "lntb10u1pjfgej2pp5cr2l5v08ty6uymjhs4pgrpar2f4anwaefzjjam7u7zhnvjnr97nsdq5xysyymr0ddskxcmfdehsxqrrsscqp79qy9qsqsp58uwzthcgl8lmcp8s4f9du2pmxaf6rqxc2s80gtcm0u43e2qy3apqe9tj28qmhxdgzpd6ax2z344m6ct62f4updckhpww3jpuf08ldq4nejfvnqfem8vkw39r9jtndy3vqd63gwwfahfprk68n7xm4ypgmggq3v0sy8"
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

    @Test
    fun parse_bitcoin_uri_with_offer_parameter() {
        val offer = OfferTypes.Offer
            .decode("lno1zrxq8pjw7qjlm68mtp7e3yvxee4y5xrgjhhyf2fxhlphpckrvevh50u0q0fpur5ezagkrszpfuyqvpw27kkyw9gnhl98c3w75hdadwz00jmqsqsr73zphy9alpauz89rs56m27cwyw3nwhhsdgj9nypa6ljxcnq5qf8sqve6fzk2nestscasjl5zct4a6kmeks9nxwjnchtllr8cv73z3c72gljcze9ejfz3856r0d3r0h29fr8rfwwmq2nfm9fjy2j380fnz65ulqhcdsgr8m2thr63ga44nnzj30e927gexqqs45znd4hfeha55kemx03zzr0gau")
            .get()
        val testnetOffer = OfferTypes.Offer
            .decode("lno1qgsyxjtl6luzd9t3pr62xr7eemp6awnejusgf6gw45q75vcfqqqqqqqsespexwyy4tcadvgg89l9aljus6709kx235hhqrk6n8dey98uyuftzdqzrtkahuum7m56dxlnx8r6tffy54004l7kvs7pylmxx7xs4n54986qyqeeuhhunayntt50snmdkq4t7fzsgghpl69v9csgparek8kv7dlp5uqr8ymp5s4z9upmwr2s8xu020d45t5phqc8nljrq8gzsjmurzevawjz6j6rc95xwfvnhgfx6v4c3jha7jwynecrz3y092nn25ek4yl7xp9yu9ry9zqagt0ktn4wwvqg52v9ss9ls22sqyqqestzp2l6decpn87pq96udsvx")
            .get()

        val validUri = BitcoinUri(
            chain = Chain.Mainnet,
            address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6"),
            offer = offer,
        )

        listOf(
            // valid offer uris
            "bitcoin:?lno=$offer" to validUri.copy(address = "", script = null),
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lno=$offer" to validUri,
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lno=$offer&foo=bar" to
                    validUri.copy(
                        ignoredParams = ParametersBuilder().apply { set("foo", "bar") }.build()
                    ),
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?foo=bar&lno=$offer" to
                    validUri.copy(ignoredParams = ParametersBuilder().apply { set("foo", "bar") }.build()),
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?foo=bar&lno=$offer&bar=baz" to
                    validUri.copy(ignoredParams = ParametersBuilder().apply { set("foo", "bar"); set("bar", "baz") }.build()),
            // valid offer in a typical bip353 uri
            "bitcoin:?sp=silentpayment&lno=$offer" to BitcoinUri(
                chain = Chain.Mainnet,
                address = "",
                script = null,
                offer = offer,
                ignoredParams = ParametersBuilder().apply { set("sp", "silentpayment") }.build()
            ),
            // invalid offer parameter
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lightning=lno1qgsyxjtl6luzd9t3pr62xr7eemp6awnejusgf6gw45q75vcfqqqqqqqsespexwyy4tcadvgg89l9a" to
                    BitcoinUri(
                        chain = Chain.Mainnet,
                        address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                        script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6")
                    ),
            // empty offer invoice
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lno=" to
                    BitcoinUri(
                        chain = Chain.Mainnet,
                        address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                        script = ByteVector("0014751e76e8199196d454941c45d1b3a323f1433bd6")
                    ),
            // offer chain mismatch with valid address fails silently
            "bitcoin:bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4?lno=$testnetOffer" to
                    validUri.copy(offer = null)
        ).forEach { (address, expected) ->
            val uri = Parser.parseBip21Uri(expected.chain, address)
            assertEquals(Either.Right(expected), uri)
        }

        // chain mismatch errors
        assertEquals(
            expected = Either.Left(BitcoinUriError.InvalidScript(error = BitcoinError.ChainHashMismatch)),
            actual = Parser.parseBip21Uri(Chain.Mainnet, "bitcoin:?lno=$testnetOffer")
        )
        assertEquals(
            expected = Either.Left(BitcoinUriError.InvalidScript(error = BitcoinError.ChainHashMismatch)),
            actual = Parser.parseBip21Uri(Chain.Mainnet, "bitcoin:tb1pan78kdkqnwe5ym7k8lnd77rrq5gfnxsyg7j7fay5rdhkwrzgkg0s6u8pvx?lno=$testnetOffer")
        )
    }

    @Test
    fun parse_email_like() {

        // addresses without a prefix are of an unknown type -- maybe bip353 or lnurl-based
        assertIs<EmailLikeAddress.UnknownType>(Parser.parseEmailLikeAddress("foobar@acinq.co"))

        // addresses with a ₿ prefix are bip353
        assertIs<EmailLikeAddress.Bip353>(Parser.parseEmailLikeAddress("₿foobar@acinq.co"))
        assertIs<EmailLikeAddress.Bip353>(Parser.parseEmailLikeAddress("%E2%82%BFfoobar@acinq.co"))

        // check domains & username
        assertEquals("foobar", Parser.parseEmailLikeAddress("foobar@acinq.co")!!.username)
        assertEquals("foobar", Parser.parseEmailLikeAddress("₿foobar@acinq.co")!!.username)
        assertEquals("foobar", Parser.parseEmailLikeAddress("%E2%82%BFfoobar@acinq.co")!!.username)
        assertEquals("acinq.co", Parser.parseEmailLikeAddress("foobar@acinq.co")!!.domain)
        assertEquals("acinq.co.fr", Parser.parseEmailLikeAddress("foobar@acinq.co.fr")!!.domain)

        // should accept URI scheme (see #616)
        assertIs<EmailLikeAddress>(Parser.parseEmailLikeAddress("lightning:₿foobar@acinq.co"))
        assertIs<EmailLikeAddress>(Parser.parseEmailLikeAddress("lightning:foobar@acinq.co"))
        assertIs<EmailLikeAddress>(Parser.parseEmailLikeAddress("lnurlp:foobar@acinq.co"))
        assertIs<EmailLikeAddress>(Parser.parseEmailLikeAddress("bitcoin:foobar@acinq.co"))
        assertIs<EmailLikeAddress>(Parser.parseEmailLikeAddress("phoenix:bitcoin:foobar@acinq.co"))

        // invalid
        assertNull(Parser.parseEmailLikeAddress(""))
        assertNull(Parser.parseEmailLikeAddress("foobar"))
        assertNull(Parser.parseEmailLikeAddress("foobar@@acinq.co"))
        assertNull(Parser.parseEmailLikeAddress("foobar@foobar@acinq.co"))
        assertNull(Parser.parseEmailLikeAddress("@acinq.co"))
        assertNull(Parser.parseEmailLikeAddress("foobar@"))
        assertNull(Parser.parseEmailLikeAddress("lntb10n1pnwrxrapp5x9plpwr7gn3vgvgxqve9s60e7lmcecej60xjzgyepjzmazm3jdnscqpjsp5jd29flqxjffcl8ul9e2m7de7j8jmz4mu0nyxtcltknxtmapf4v2s9q7sqqqqqqqqqqqqqqqqqqqsqqqqqysgqdq4xysyymr0vd4kzcmrd9hx7mqz9grzjqwfn3p9278ttzzpe0e00uhyxhned3j5d9acqak5emwfpflp8z2cnfl6h8msfh3505gqqqqlgqqqqqeqqjqtznlghccn9yspm3mt7kqp8wxkmadvs0r7t3tujnqg7qj0qrc2jvjn25zpv0dhdqq0nmvsx2rwtsc35wcmyjl49qmt9lmvk7hckm4wvcqftm4c5"))
        assertNull(Parser.parseEmailLikeAddress("lno1qgsyxjtl6luzd9t3pr62xr7eemp6awnejusgf6gw45q75vcfqqqqqqqsespexwyy4tcadvgg89l9aljus6709kx235hhqrk6n8dey98uyuftzdqzrtkahuum7m56dxlnx8r6tffy54004l7kvs7pylmxx7xs4n54986qyqeeuhhunayntt50snmdkq4t7fzsgghpl69v9csgparek8kv7dlp5uqr8ymp5s4z9upmwr2s8xu020d45t5phqc8nljrq8gzsjmurzevawjz6j6rc95xwfvnhgfx6v4c3jha7jwynecrz3y092nn25ek4yl7xp9yu9ry9zqagt0ktn4wwvqg52v9ss9ls22sqyqqestzp2l6decpn87pq96udsvx"))
    }
}