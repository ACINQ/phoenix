package fr.acinq.phoenix.data

import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.KeyPath
import fr.acinq.bitcoin.MnemonicCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletTest {
    @Test
    fun derivedPublicKey(): Unit {
        val wallet = Wallet(mnemonics = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "))
        assertTrue(wallet.seed.contentEquals(MnemonicCode.toSeed("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "), "")))
        // Mainnet
        assertEquals(wallet.derivedPublicKey(KeyPath.computePath("m/84'/0'/0'"), true), "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs")
        // Testnet
        assertEquals(wallet.derivedPublicKey(KeyPath.computePath("m/84'/1'/0'"), false), "vpub5Y6cjg78GGuNLsaPhmYsiw4gYX3HoQiRBiSwDaBXKUafCt9bNwWQiitDk5VZ5BVxYnQdwoTyXSs2JHRPAgjAvtbBrf8ZhDYe2jWAqvZVnsc")
    }
}
