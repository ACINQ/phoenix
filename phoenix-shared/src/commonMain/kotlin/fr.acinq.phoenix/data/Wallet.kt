package fr.acinq.phoenix.data

import fr.acinq.bitcoin.*
import fr.acinq.bitcoin.crypto.Digest
import fr.acinq.bitcoin.crypto.Pack
import fr.acinq.bitcoin.crypto.hmac
import fr.acinq.lightning.crypto.LocalKeyManager
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*


data class Wallet(val seed: ByteVector64, val chain: Chain) {

    constructor(seed: ByteArray, chain: Chain) : this(ByteVector64(seed), chain)

    private val master by lazy { DeterministicWallet.generate(seed) }

    fun masterPublicKey(path: String): String {
        val publicKey =
            DeterministicWallet.publicKey(
                DeterministicWallet.derivePrivateKey(master, path)
            )
        return DeterministicWallet.encode(
            input = publicKey,
            prefix = if (chain.isMainnet()) DeterministicWallet.zpub else DeterministicWallet.vpub
        )
    }

    /** Get the wallet (xpub, path) */
    fun xpub(): Pair<String, String> {
        val isMainnet = chain.isMainnet()
        val masterPubkeyPath = if (isMainnet) "m/84'/0'/0'" else "m/84'/1'/0'"
        return masterPublicKey(masterPubkeyPath) to masterPubkeyPath
    }

    fun onchainAddress(path: String): String {
        val isMainnet = chain.isMainnet()
        val chainHash = if (isMainnet) Block.LivenetGenesisBlock.hash else Block.TestnetGenesisBlock.hash
        val publicKey = DeterministicWallet.derivePrivateKey(master, path).publicKey
        return Bitcoin.computeBIP84Address(publicKey, chainHash)
    }

    // For cloud storage, we need:
    // - an encryption key
    // - a cleartext string that's tied to a specific nodeId
    //
    // But we also don't want to expose the nodeId.
    // So we deterministically derive both values from the seed.
    //
    fun cloudKeyAndEncryptedNodeId(): Pair<ByteVector32, String> {
        val path = if (chain.isMainnet()) "m/51'/0'/0'/0" else "m/51'/1'/0'/0"
        val extPrivKey = DeterministicWallet.derivePrivateKey(master, path)

        val cloudKey = extPrivKey.privateKey.value
        val hash = Crypto.hash160(cloudKey).byteVector().toHex()

        return Pair(cloudKey, hash)
    }

    fun nodeId(): PublicKey {
        val keyManager = LocalKeyManager(seed = seed, chainHash = chain.chainHash)
        return keyManager.nodeId
    }

    fun lnurlAuthLinkingKey(domain: String, keyType: LNUrl.Auth.KeyType): PrivateKey {
        val baseKey = if (keyType == LNUrl.Auth.KeyType.LEGACY_KEY_TYPE) LocalKeyManager(seed = seed, chainHash = chain.chainHash).legacyNodeKey else master
        val hashingKey = DeterministicWallet.derivePrivateKey(baseKey, "m/138'/0")
        val path = lnurlAuthPath(domain, hashingKey.privateKey.value.toByteArray())
        return DeterministicWallet.derivePrivateKey(
            parent = if (keyType == LNUrl.Auth.KeyType.LEGACY_KEY_TYPE) hashingKey else master,
            keyPath = path
        ).privateKey
    }

    /* lnurl-auth path derivation, as described in spec:
     * https://github.com/fiatjaf/lnurl-rfc/blob/luds/05.md
     *
     * Test vectors exist for path derivation.
     */
    internal fun lnurlAuthPath(domain: String, hashingKey: ByteArray): KeyPath {
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

    override fun toString(): String = "Wallet"
}
