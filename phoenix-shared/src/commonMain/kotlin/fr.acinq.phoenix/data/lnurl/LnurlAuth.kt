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
import fr.acinq.bitcoin.crypto.Digest
import fr.acinq.bitcoin.crypto.Pack
import fr.acinq.bitcoin.crypto.hmac
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.data.lnurl.Lnurl.Companion.log
import fr.acinq.phoenix.utils.loggerExtensions.*
import io.ktor.http.*

data class LnurlAuth(
    override val initialUrl: Url,
    val k1: String
) : Lnurl.Qualified {

    enum class Action {
        Register, Login, Link, Auth
    }

    val action = initialUrl.parameters["action"]?.let { action ->
        when (action.lowercase()) {
            "register" -> Action.Register
            "login" -> Action.Login
            "link" -> Action.Link
            "auth" -> Action.Auth
            else -> null
        }
    }

    /**
     * Early versions of our lnurl-auth implementation used non-standard keys on Android. We define a legacy-friendly
     * scheme to let the user switch to that old behaviour when needed. The default scheme is the recommended
     * one and is compliant with the specifications.
     *
     * Note: this does **not** affect the [LnurlAuth.LegacyDomain.filterDomain] method for the key derivation path,
     * which will apply in both cases.
     */
    sealed class Scheme(val id: Int) {
        /**
         * The default scheme is spec compliant and that's what should be used on new service. The hashing key and
         * the linking key are computed by deriving the master key. The iOS app should always use that scheme.
         */
        object DEFAULT_SCHEME : Scheme(0)

        /**
         * This is the scheme used by the legacy android wallet. The hashing key is derived from the node key, and
         * the linking key derived from the hashing key. Only use this when needed.
         */
        object ANDROID_LEGACY_SCHEME : Scheme(1)
    }

    companion object {

        /** Signs the challenge with the key provided and returns the public key and the DER-encoded signed data. */
        fun signChallenge(
            challenge: String,
            key: PrivateKey
        ): Pair<PublicKey, ByteVector> {
            return key.publicKey() to Crypto.compact2der(Crypto.sign(data = ByteVector32.fromValidHex(challenge), privateKey = key))
        }

        /**
         * Returns a key to sign a lnurl-auth challenge. This key is derived from the wallet's master key. The derivation
         * path depends on the domain provided and the type of the key.
         *
         * @param scheme This type helps with backward compatibility. We use compatibility keys on some domains, see [LegacyDomain].
         */
        fun getAuthLinkingKey(
            localKeyManager: LocalKeyManager,
            serviceUrl: Url,
            scheme: Scheme
        ): PrivateKey {
            // no need to use the legacy scheme on non-legacy domain
            val useAndroidLegacyScheme = scheme == Scheme.ANDROID_LEGACY_SCHEME && LegacyDomain.isEligible(serviceUrl)
            val hashingKeyPath = KeyPath("m/138'/0")
            val hashingKey = if (useAndroidLegacyScheme) {
                DeterministicWallet.derivePrivateKey(localKeyManager.nodeKeys.legacyNodeKey, hashingKeyPath)
            } else {
                localKeyManager.derivePrivateKey(hashingKeyPath)
            }
            // the domain used for the derivation path may not be the full domain name.
            val path = getDerivationPathForDomain(
                domain = LegacyDomain.filterDomain(serviceUrl),
                hashingKey = hashingKey.privateKey.value.toByteArray()
            )
            return if (useAndroidLegacyScheme) {
                DeterministicWallet.derivePrivateKey(hashingKey, path).privateKey
            } else {
                localKeyManager.derivePrivateKey(path).privateKey
            }
        }

        /**
         * Returns lnurl-auth path derivation, as described in spec:
         * https://github.com/fiatjaf/lnurl-rfc/blob/luds/05.md
         *
         * Test vectors exist for path derivation.
         */
        fun getDerivationPathForDomain(
            domain: String,
            hashingKey: ByteArray
        ): KeyPath {
            log.debug { "creating auth derivation path for domain=$domain" }
            val fullHash = Digest.sha256().hmac(
                key = hashingKey,
                data = domain.encodeToByteArray(),
                blockSize = 64
            )
            require(fullHash.size >= 16) { "domain hash must be at least 16 bytes" }
            val path1 = fullHash.sliceArray(IntRange(0, 3)).let { Pack.int32BE(it, 0) }.toUInt()
            val path2 = fullHash.sliceArray(IntRange(4, 7)).let { Pack.int32BE(it, 0) }.toUInt()
            val path3 = fullHash.sliceArray(IntRange(8, 11)).let { Pack.int32BE(it, 0) }.toUInt()
            val path4 = fullHash.sliceArray(IntRange(12, 15)).let { Pack.int32BE(it, 0) }.toUInt()

            return KeyPath("m/138'/$path1/$path2/$path3/$path4")
        }
    }

    /**
     * Domains where we should use a legacy path instead of the regular full domain, for
     * backward compatibility reasons.
     *
     * Those services are listed as using LUD-04 on the lnurl specs:
     * https://github.com/fiatjaf/lnurl-rfc/tree/38d8baa6f8e3b3dfd13649bfa79e2175d6ca42ff#services
     */
    enum class LegacyDomain(val host: String, val legacyCompatDomain: String) {
        GEYSER("auth.geyser.fund", "geyser.fund"),
        KOLLIDER("api.kollider.xyz", "kollider.xyz"),
        LNMARKETS("api.lnmarkets.com", "lnmarkets.com"),
        // LNBITS("", ""),
        GETALBY("getalby.com", "getalby.com"),
        LIGHTNING_VIDEO("lightning.video", "lightning.video"),
        LOFT("api.loft.trade", "loft.trade"),
        // WHEEL_OF_FORTUNE("", ""),
        // COINOS("", ""),
        LNSHORT("lnshort.it", "lnshort.it"),
        STACKERNEWS("stacker.news", "stacker.news"),
        BOLTFUN("auth.bolt.fun", "bolt.fun")
        ;

        companion object {
            /** Return true if this host is eligible to use legacy keys, false otherwise. */
            fun isEligible(url: Url): Boolean {
                return values().any { it.host == url.host }
            }

            /** Get the legacy domain for the given [Url] if eligible, or the full domain name otherwise (i.e. specs compliant). */
            fun filterDomain(url: Url): String {
                return values().firstOrNull() { it.host == url.host }?.legacyCompatDomain ?: url.host
            }
        }
    }

    override fun toString(): String {
        return "LnurlAuth(action=$action, initialUrl=$initialUrl)".take(100)
    }
}