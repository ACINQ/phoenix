package fr.acinq.phoenix.data

import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.bitcoin.KeyPath
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.phoenix.utils.TAG_IS_MAINNET
import org.kodein.di.instance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletTest {
    private val wallet = Wallet(mnemonics = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "))

    @Test
    fun masterPublicKey() {
        // Mainnet
        assertEquals(wallet.masterPublicKey("m/84'/0'/0'",true), "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs")
        // Testnet
        assertEquals(wallet.masterPublicKey("m/84'/1'/0'",false), "vpub5Y6cjg78GGuNLsaPhmYsiw4gYX3HoQiRBiSwDaBXKUafCt9bNwWQiitDk5VZ5BVxYnQdwoTyXSs2JHRPAgjAvtbBrf8ZhDYe2jWAqvZVnsc")
    }

    @Test
    fun onchainAddress() {
        // Mainnet
        assertEquals(wallet.onchainAddress("m/84'/0'/0'/0/0",true), "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu")
        // Testnet
        assertEquals(wallet.onchainAddress("m/84'/1'/0'/0/0", false), "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl")
    }
}
