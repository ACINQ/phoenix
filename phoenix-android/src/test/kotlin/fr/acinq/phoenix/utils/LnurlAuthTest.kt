package fr.acinq.phoenix.utils

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.data.lnurl.LnurlAuth
import io.ktor.http.*
import org.junit.Assert
import org.junit.Test

class LnurlAuthTest {

    /** Checks that the default scheme generates a DIFFERENT key/sig tuple for a non-legacy domain than the legacy scheme. */
    @Test
    fun legacy_domain_different_keys() {
        val seed = MnemonicCode.toSeed("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", "")
        val kmpKeyManager = LocalKeyManager(
            seed = seed.byteVector(),
            chain = Chain.Testnet3,
            remoteSwapInExtendedPublicKey = "tpubDDt5vQap1awkyDXx1z1cP7QFKSZHDCCpbU8nSq9jy7X2grTjUVZDePexf6gc6AHtRRzkgfPW87K6EKUVV6t3Hu2hg7YkHkmMeLSfrP85x41"
        )

        val k1 = "179062fdf971ec045883a6297fb1d260333358905086c33a9f44ff26f63bb425"
        val url = Url("https://api.lnmarkets.com/v1/lnurl/auth?tag=login&k1=$k1&hmac=75344d9151fe788345e620aa3de0e69b51698e759fd667272e3ea682a2bbcd12")

        // key when using the legacy friendly scheme
        val legacyAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME)
        val legacySignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = legacyAuthKey)).toHex()
        // key when using the default scheme
        val defaultAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.DEFAULT_SCHEME)
        val defaultSignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = defaultAuthKey)).toHex()

        val expectedLegacyPubkey = "024d82b199464c9568f5cce92cf7370a8154d9ec0571905b596a4e9dcae69136d8"
        val expectedNewPubkey = "03702494face111dcd61be4ab4a13fa4cd4ac720b2d3b47e95feee58484f573630"
        Assert.assertEquals(expectedLegacyPubkey, legacyAuthKey.publicKey().toString())
        Assert.assertEquals(expectedLegacyPubkey, legacyAuthKey.publicKey().toString())
        Assert.assertEquals(expectedNewPubkey, defaultAuthKey.publicKey().toString())

        val expectedLegacySig = "3044022056299db29f515fa2941e5212bfc6de7bd64ca0edd6067e3575a4753ca00be1ec02200f63e9905eef29b1ba8b54c79cfcf41c1380bc6eeb8ca8e5c2748a79c962b663"
        val expectedNewSig = "304402204cc2411cebd5c5c9722da29aad92788434026744fd6e5aa6a98a5a3f30f2595f022044fdeeb8158611a270ee933510779b117493653011dc3e30172167ca721056a5"
        Assert.assertEquals(expectedLegacySig, legacySignedK1)
        Assert.assertEquals(expectedLegacySig, legacySignedK1)
        Assert.assertEquals(expectedNewSig, defaultSignedK1)
    }

    /** Checks that the app generates the SAME pubkey/signature for a non-legacy domain whatever the scheme used. */
    @Test
    fun standard_domain_same_key() {
        val seed = MnemonicCode.toSeed("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", "")
        val kmpKeyManager = LocalKeyManager(
            seed = seed.byteVector(),
            chain = Chain.Testnet3,
            remoteSwapInExtendedPublicKey = "tpubDDt5vQap1awkyDXx1z1cP7QFKSZHDCCpbU8nSq9jy7X2grTjUVZDePexf6gc6AHtRRzkgfPW87K6EKUVV6t3Hu2hg7YkHkmMeLSfrP85x41"
        )

        val k1 = "32c56da24a28e09d24832e1cba0cc391049c48036c197e228c7656d022a5eb1f"
        val url = Url("https://foo.bar.com/auth?tag=login&k1=$k1")

        // key when using the legacy friendly scheme
        val legacyAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME)
        val legacySignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = legacyAuthKey)).toHex()
        // key when using the default scheme
        val defaultNewAuthKey = LnurlAuth.getAuthLinkingKey(kmpKeyManager, url, LnurlAuth.Scheme.DEFAULT_SCHEME)
        val defaultNewSignedK1 = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(k1), privateKey = defaultNewAuthKey)).toHex()

        val expectedPubkey = "02ebfff275eccdd929a3843eff9481a53b0445cdae9a931fd43baa91dcd35836e9"
        Assert.assertEquals(expectedPubkey, legacyAuthKey.publicKey().toString())
        Assert.assertEquals(expectedPubkey, defaultNewAuthKey.publicKey().toString())

        val expectedSignature = "3045022100f3710b22bd3433b1aa56fa61b1ad9ced79d4e5010430d0afa8d6c7dc77481ef502201a80ab49a2facf8810652bf9e0f85d6c834f6e9fd59f312e7eeece36e51ce957"
        Assert.assertEquals(expectedSignature, legacySignedK1)
        Assert.assertEquals(expectedSignature, defaultNewSignedK1)
    }
}