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

package fr.acinq.phoenix.data.lnurl

import fr.acinq.bitcoin.*
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.secp256k1.Hex
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class LnurlAuthTest {
    private val mnemonics = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val seed = MnemonicCode.toSeed(mnemonics, passphrase = "").toByteVector()
    private val keyManager = LocalKeyManager(seed, Bitcoin.Chain.Testnet, remoteSwapInExtendedPublicKey = "tpubDDt5vQap1awkyDXx1z1cP7QFKSZHDCCpbU8nSq9jy7X2grTjUVZDePexf6gc6AHtRRzkgfPW87K6EKUVV6t3Hu2hg7YkHkmMeLSfrP85x41")

    @Test
    fun specs_test_vectors() {
        // Test vector from spec:
        // https://github.com/fiatjaf/lnurl-rfc/blob/luds/05.md
        val domain = "site.com"
        val hashingKey = "0x7d417a6a5e9a6a4a879aeaba11a11838764c8fa2b959c242d43dea682b3e409b01"
        val expectedPath = "m/138'/3751473387/2829804099/4228872783/4134047485"
        assertEquals(KeyPath(expectedPath), LnurlAuth.getDerivationPathForDomain(domain, Hex.decode(hashingKey)))
    }

    @Test
    fun test_default_scheme() {
        val auth = LnurlAuth(
            initialUrl = Url("https://api.lnmarkets.com/v1/lnurl/auth?tag=login&k1=e94e9e54d97164751db976c347a1d325167d48c0f6c2e08688bec185fa5fc20a"),
            k1 = "e94e9e54d97164751db976c347a1d325167d48c0f6c2e08688bec185fa5fc20a"
        )
        val linkingKey = LnurlAuth.getAuthLinkingKey(keyManager, auth.initialUrl, LnurlAuth.Scheme.DEFAULT_SCHEME)
        assertEquals("03702494face111dcd61be4ab4a13fa4cd4ac720b2d3b47e95feee58484f573630", linkingKey.publicKey().toString())

        val signedChallenge = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(auth.k1), privateKey = linkingKey)).toHex()
        val expectedSignature = "3044022078c05792b76a8772c790d4d9d73c793f6cd34ea5e1a1a70bb5cd600cbc3452b902204e1a63654dcc8fcbf07f3b56b4dd4a37ddbdd6bd90738091c891f65ac53bc7b0"
        assertEquals(expectedSignature, signedChallenge)
    }

    @Test
    fun test_android_legacy_scheme() {
        val auth = LnurlAuth(
            initialUrl = Url("https://api.lnmarkets.com/v1/lnurl/auth?tag=login&k1=179062fdf971ec045883a6297fb1d260333358905086c33a9f44ff26f63bb425&hmac=75344d9151fe788345e620aa3de0e69b51698e759fd667272e3ea682a2bbcd12"),
            k1 = "179062fdf971ec045883a6297fb1d260333358905086c33a9f44ff26f63bb425"
        )
        val linkingKey = LnurlAuth.getAuthLinkingKey(keyManager, auth.initialUrl, LnurlAuth.Scheme.ANDROID_LEGACY_SCHEME)
        assertEquals("024d82b199464c9568f5cce92cf7370a8154d9ec0571905b596a4e9dcae69136d8", linkingKey.publicKey().toString())

        val signedChallenge = Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(auth.k1), privateKey = linkingKey)).toHex()
        val expectedSignature = "3044022056299db29f515fa2941e5212bfc6de7bd64ca0edd6067e3575a4753ca00be1ec02200f63e9905eef29b1ba8b54c79cfcf41c1380bc6eeb8ca8e5c2748a79c962b663"
        assertEquals(expectedSignature, signedChallenge)
    }

    @Test
    fun test_legacy_domains() {
        listOf(
            Url("https://auth.geyser.fund") to "geyser.fund",
            Url("https://api.kollider.xyz") to "kollider.xyz",
            Url("https://api.lnmarkets.com") to "lnmarkets.com",
            Url("https://getalby.com") to "getalby.com",
            Url("https://lightning.video") to "lightning.video",
            Url("https://api.loft.trade") to "loft.trade",
            Url("https://lnshort.it") to "lnshort.it",
            Url("https://stacker.news") to "stacker.news",
        ).forEach { (url, expectedDomain) ->
            assertEquals(expectedDomain, LnurlAuth.LegacyDomain.filterDomain(url))
        }
    }

    @Test
    fun test_non_legacy_domains() {
        listOf(
            Url("https://auth.google.com"),
            Url("https://staging.foo.bar"),
            Url("https://apı̇.lnmarkets.com"), // u+0307  ̇ + u+0131 ı = ı̇
            Url("https://api2.lnmarkets.com"),
            Url("https://x.lnmarkets.com"),
            Url("https://login.stacker.news"),
            Url("http://one.herokuapp.com"),
            Url("http://two.herokuapp.com"),
        ).forEach { url ->
            assertEquals(url.host, LnurlAuth.LegacyDomain.filterDomain(url))
        }
    }
}
