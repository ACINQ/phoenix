package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.byteVector64
import fr.acinq.bitcoin.scala.Block
import fr.acinq.bitcoin.scala.`MnemonicCode$`
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.LNUrl
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.legacy.lnurl.LNUrlAuthFragment
import org.junit.Assert
import org.junit.Test

class LnurlAuthTest {

    /** This test checks that the new kotlin multiplatform code generates the same lnurl-auth pubkey/signature for the legacy key type than the old code. */
    @Test
    fun lnurlAuthKey_legacy_scheme_compat() {
        val seed = `MnemonicCode$`.`MODULE$`.toSeed("page zebra foam artwork please nuclear sting voice tortoise episode tent genre", "")
        val k1 = "32c56da24a28e09d24832e1cba0cc391049c48036c197e228c7656d022a5eb1f"
        val domain = "fiatjaf.com"

        val legacyKeyManager = fr.acinq.eclair.crypto.LocalKeyManager(seed, Block.TestnetGenesisBlock().hash())
        val (legacySignedK1, legacyAuthKey) = LNUrlAuthFragment.signLnurlAuthK1WithKey(legacyKeyManager.nodeKey(), k1, domain)

        val kmpWallet = Wallet(seed = seed.toArray().byteVector64(), Chain.Testnet)
        val kmpLegacyAuthKey = kmpWallet.lnurlAuthLinkingKey(domain, LNUrl.Auth.KeyType.LEGACY_KEY_TYPE)
        val kmpLegacySignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = kmpLegacyAuthKey)).toHex()

        val expectedLegacyPubkey = "02387a7ff51f0a14914d2c427b9e336bc4b1bac7791e476155081118acd11d406f"
        Assert.assertEquals(expectedLegacyPubkey, legacyAuthKey.publicKey().toString())
        Assert.assertEquals(expectedLegacyPubkey, kmpLegacyAuthKey.publicKey().toString())

        val expectedLegacySignature = "3044022051a2279b2b447c759bd984d94aa51b7a13d522c31f5ce69cafad88819b94aee802207bc1b1d48f6838375d9f13e7fd1979e869d9d11eaf81b37e080afd13e6714384"
        Assert.assertEquals(expectedLegacySignature, legacySignedK1)
        Assert.assertEquals(expectedLegacySignature, kmpLegacySignedK1)
    }

    @Test
    fun lnurlAuthKey_new_scheme() {
        val seed = `MnemonicCode$`.`MODULE$`.toSeed("page zebra foam artwork please nuclear sting voice tortoise episode tent genre", "")
        val k1 = "32c56da24a28e09d24832e1cba0cc391049c48036c197e228c7656d022a5eb1f"
        val domain = "fiatjaf.com"

        val kmpWallet = Wallet(seed = seed.toArray().byteVector64(), Chain.Testnet)
        val kmpNewAuthKey = kmpWallet.lnurlAuthLinkingKey(domain, LNUrl.Auth.KeyType.DEFAULT_KEY_TYPE)
        val kmpNewSignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = kmpNewAuthKey)).toHex()

        val expectedKmpPubkey = "023762f77d06fea8f77cbbcbeed092040976c1f25ac2b91c6fff363edf3b58aac4"
        Assert.assertEquals(expectedKmpPubkey, kmpNewAuthKey.publicKey().toString())

        val expectedKmpSignature = "3045022100f2f29fa1318abcb019930098da71272eaf722194093197dda129a1a22fa345a6022063c3f053031ff3af261980a11685e1e4ebdc6b77a2bf75647c31cfb8e860774a"
        Assert.assertEquals(expectedKmpSignature, kmpNewSignedK1)
    }
}