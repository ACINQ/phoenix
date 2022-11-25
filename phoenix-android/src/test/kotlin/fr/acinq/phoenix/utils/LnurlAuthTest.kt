package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.byteVector64
import fr.acinq.bitcoin.scala.Block
import fr.acinq.bitcoin.scala.`MnemonicCode$`
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import fr.acinq.phoenix.legacy.lnurl.LNUrlAuthFragment
import io.ktor.http.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert
import org.junit.Test

class LnurlAuthTest {
    private val seed = `MnemonicCode$`.`MODULE$`.toSeed("page zebra foam artwork please nuclear sting voice tortoise episode tent genre", "")
    private val legacyKeyManager = fr.acinq.eclair.crypto.LocalKeyManager(seed, Block.TestnetGenesisBlock().hash())
    private val kmpKeyManager = LocalKeyManager(seed = seed.toArray().byteVector64(), Chain.Testnet.chainHash)

    private val k1 = "32c56da24a28e09d24832e1cba0cc391049c48036c197e228c7656d022a5eb1f"
    private val url = Url("https://fiatjaf.com")

    @Test
    fun test_sign_alternative_key() {
        val url = "https://api.lnmarkets.com/v1/lnurl/auth?tag=login&k1=179062fdf971ec045883a6297fb1d260333358905086c33a9f44ff26f63bb425&hmac=75344d9151fe788345e620aa3de0e69b51698e759fd667272e3ea682a2bbcd12".toHttpUrlOrNull()
        val keyManager = fr.acinq.eclair.crypto.LocalKeyManager(`MnemonicCode$`.`MODULE$`.toSeed("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", ""), Block.TestnetGenesisBlock().hash())
        val (legacySignedK1, legacyAuthKey) = LNUrlAuthFragment.signLnurlAuthK1WithKey(keyManager, k1, url!!)
        Assert.assertEquals("024d82b199464c9568f5cce92cf7370a8154d9ec0571905b596a4e9dcae69136d8", legacyAuthKey.publicKey().toString())
        Assert.assertEquals(
            "30440220653ccb25e2516eaaf2974f938ccd55f4aaab4c0801e9df864c2548566d897f23022062a36653e10dda7b2d48e50ec8e2b6876b43d1b5aac9be7e7a9251c25499d493",
            legacySignedK1
        )
    }

    /** This test checks that the new kotlin multiplatform code generates the same lnurl-auth pubkey/signature for the legacy key type than the old code. */
    @Test
    fun lnurlAuthKey_legacy_scheme_compat() {
        val (legacySignedK1, legacyAuthKey) = LNUrlAuthFragment.signLnurlAuthK1WithKey(legacyKeyManager, k1, url.toString().toHttpUrlOrNull()!!)

        val kmpLegacyAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME)
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
        val kmpNewAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.DEFAULT_SCHEME)
        val kmpNewSignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = kmpNewAuthKey)).toHex()

        val expectedKmpPubkey = "023762f77d06fea8f77cbbcbeed092040976c1f25ac2b91c6fff363edf3b58aac4"
        Assert.assertEquals(expectedKmpPubkey, kmpNewAuthKey.publicKey().toString())

        val expectedKmpSignature = "3045022100f2f29fa1318abcb019930098da71272eaf722194093197dda129a1a22fa345a6022063c3f053031ff3af261980a11685e1e4ebdc6b77a2bf75647c31cfb8e860774a"
        Assert.assertEquals(expectedKmpSignature, kmpNewSignedK1)
    }
}