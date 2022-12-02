package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.*
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.phoenix.data.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class WalletManager(
    private val chain: Chain
) : CoroutineScope by MainScope() {
    internal var _master: DeterministicWallet.ExtendedPrivateKey? = null

    private val _localKeyManager = MutableStateFlow<LocalKeyManager?>(null)
    internal val keyManager: StateFlow<LocalKeyManager?> = _localKeyManager

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
        _master = DeterministicWallet.generate(seed)
        val km = keyManager.value ?: LocalKeyManager(seed.byteVector(), chain.chainHash).also {
            _localKeyManager.value = it
        }
        val (cloudKey, encryptedNodeId) = cloudKeyAndEncryptedNodeId()
        return WalletInfo(
            nodeId = km.nodeId,
            cloudKey = cloudKey,
            encryptedNodeId = encryptedNodeId
        )
    }

    fun getXpub(): Pair<String, String>? {
        if (_master == null) return null
        val masterPubkeyPath = KeyPath(if (isMainnet()) "m/84'/0'/0'" else "m/84'/1'/0'")
        val publicKey = DeterministicWallet.publicKey(privateKey(masterPubkeyPath))

        return DeterministicWallet.encode(
            input = publicKey,
            prefix = if (isMainnet()) DeterministicWallet.zpub else DeterministicWallet.vpub
        ) to masterPubkeyPath.toString()
    }

    /** Key used to encrypt/decrypt blobs we store in the cloud. */
    private fun cloudKeyAndEncryptedNodeId(): Pair<ByteVector32, String> {
        val path = KeyPath(if (isMainnet()) "m/51'/0'/0'/0" else "m/51'/1'/0'/0")
        val extPrivKey = privateKey(path)

        val cloudKey = extPrivKey.privateKey.value
        val hash = Crypto.hash160(cloudKey).byteVector().toHex()

        return Pair(cloudKey, hash)
    }

    private fun isMainnet() = chain.isMainnet()

    fun privateKey(keyPath: KeyPath): DeterministicWallet.ExtendedPrivateKey = DeterministicWallet.derivePrivateKey(_master!!, keyPath)

    fun onchainAddress(path: KeyPath): String {
        val chainHash = if (isMainnet()) Block.LivenetGenesisBlock.hash else Block.TestnetGenesisBlock.hash
        val publicKey = privateKey(path).publicKey
        return Bitcoin.computeBIP84Address(publicKey, chainHash)
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
        val cloudKey: ByteVector32, // used for cloud storage
        val encryptedNodeId: String // used for cloud storage
    )
}
