package fr.acinq.phoenix.app

import fr.acinq.phoenix.app.Utilities.BitcoinAddressType
import fr.acinq.phoenix.app.Utilities.BitcoinAddressError
import fr.acinq.phoenix.data.Chain
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
            Chain.MAINNET, BitcoinAddressType.Base58PubKeyHash
        ),
        TestInput("3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX",
            Chain.MAINNET, BitcoinAddressType.Base58ScriptHash
        ),
        TestInput("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            Chain.MAINNET, BitcoinAddressType.SegWitPubKeyHash
        ),
        TestInput("bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3",
            Chain.MAINNET, BitcoinAddressType.SegWitScriptHash
        ),
        TestInput("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn",
            Chain.TESTNET, BitcoinAddressType.Base58PubKeyHash
        ),
        TestInput("2MzQwSSnBHWHqSAqtTVQ6v47XtaisrJa1Vc",
            Chain.TESTNET, BitcoinAddressType.Base58ScriptHash
        ),
        TestInput("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx",
            Chain.TESTNET, BitcoinAddressType.SegWitPubKeyHash
        ),
        TestInput("tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
            Chain.TESTNET, BitcoinAddressType.SegWitScriptHash,
        ),
        TestInput("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhe",
            Chain.MAINNET, BitcoinAddressType.Base58PubKeyHash, isValid = false
        )
    )

    @Test
    fun bitcoinAddressParsing() {

        val loggerFactory = LoggerFactory.default

        val chains = listOf(Chain.MAINNET, Chain.TESTNET)
        for (chain in chains) {

            val util = Utilities(loggerFactory, chain)

            for (input in testInputs) {
                val result = util.parseBitcoinAddress(input.addr)

                if (!input.isValid) {
                    val error = result.right
                    assertNotNull(error)
                }
                else if (input.chain != chain) {
                    val error = result.left
                    assertNotNull(error)

                    assertTrue { error is BitcoinAddressError.ChainMismatch }
                    val mismatch = error as BitcoinAddressError.ChainMismatch

                    assertTrue { mismatch.myChain == chain }
                    assertTrue { mismatch.addrChain == input.chain }
                }
                else {
                    val info = result.right
                    assertNotNull(info)

                    assertTrue { info.chain == input.chain }
                    assertTrue { info.type == input.type }
                }
            }

        }
    }
}