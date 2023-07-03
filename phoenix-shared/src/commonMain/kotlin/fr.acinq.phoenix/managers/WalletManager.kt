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

package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.*
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.crypto.KeyManager
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.lightning.crypto.div
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*

class WalletManager(
    private val chain: NodeParams.Chain
) : CoroutineScope by MainScope() {

    private val _localKeyManager = MutableStateFlow<LocalKeyManager?>(null)
    val keyManager: StateFlow<LocalKeyManager?> = _localKeyManager

    fun isLoaded(): Boolean = keyManager.value != null

    /** Validates and converts a mnemonics list (stored app side) into a seed (usable by lightning-kmp). */
    fun mnemonicsToSeed(
        mnemonics: List<String>,
        passphrase: String = ""
    ): ByteArray {
        MnemonicCode.validate(mnemonics = mnemonics, wordlist = MnemonicCode.englishWordlist)
        return MnemonicCode.toSeed(mnemonics, passphrase)
    }

    /** Loads a seed and creates the key manager. Returns an objet containing some keys for the iOS app. */
    fun loadWallet(seed: ByteArray): WalletInfo {
        val km = keyManager.value ?: LocalKeyManager(
            seed = seed.byteVector(),
            chain = chain,
            remoteSwapInExtendedPublicKey = NodeParamsManager.remoteSwapInXpub,
        ).also {
            _localKeyManager.value = it
        }
        return WalletInfo(
            nodeId = km.nodeKeys.nodeKey.publicKey,
            nodeIdHash = km.nodeIdHash(),
            cloudKey = km.cloudKey(),
            cloudKeyHash = km.cloudKeyHash()
        )
    }

    /**
     * TODO: Remove this object and and use keyManager methods directly.
     *
     * Utility wrapper for keys needed by the iOS app.
     * - nodeIdHash:
     *   We need to store data in the local filesystem that's associated with the
     *   specific nodeId, but we don't want to leak the nodeId.
     *   (i.e. We don't want to use the nodeId in cleartext anywhere).
     *   So we instead use the nodeIdHash as the identifier for local files.
     *
     * - cloudKey:
     *   We need a key to encypt/decrypt the blobs we store in the cloud.
     *   And we prefer this key to be seperate from other keys.
     *
     * - cloudKeyHash:
     *   Similar to the nodeIdHash, we need to store data in the cloud that's associated
     *   with the specific nodeId, but we don't want to leak the nodeId.
     */
    data class WalletInfo(
        val nodeId: PublicKey,
        val nodeIdHash: String,     // used for local storage
        val cloudKey: ByteVector32, // used for cloud storage
        val cloudKeyHash: String    // used for cloud storage
    )
}

fun LocalKeyManager.nodeIdHash(): String = this.nodeKeys.nodeKey.publicKey.hash160().byteVector().toHex()

/** Key used to encrypt/decrypt blobs we store in the cloud. */
fun LocalKeyManager.cloudKey(): ByteVector32 {
    val path = KeyPath(if (isMainnet()) "m/51'/0'/0'/0" else "m/51'/1'/0'/0")
    return derivePrivateKey(path).privateKey.value
}

fun LocalKeyManager.cloudKeyHash(): String {
    return Crypto.hash160(cloudKey()).byteVector().toHex()
}

fun LocalKeyManager.isMainnet() = chain == NodeParams.Chain.Mainnet

val LocalKeyManager.finalOnChainWalletPath: String
    get() = (KeyManager.Bip84OnChainKeys.bip84BasePath(chain) / finalOnChainWallet.account).toString()
