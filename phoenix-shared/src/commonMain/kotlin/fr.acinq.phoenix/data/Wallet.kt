package fr.acinq.phoenix.data

import fr.acinq.bitcoin.*
import fr.acinq.bitcoin.DeterministicWallet.hardened
import fr.acinq.lightning.crypto.LocalKeyManager


data class Wallet(val seed: ByteVector64, val chain: Chain) {

    constructor(seed: ByteArray, chain: Chain) : this(ByteVector64(seed), chain)

    internal val master by lazy { DeterministicWallet.generate(seed) }

    internal val legacyNodeKey: DeterministicWallet.ExtendedPrivateKey =
        DeterministicWallet.derivePrivateKey(master, eclairNodeKeyBasePath())

    private fun eclairNodeKeyBasePath() = when (chain.chainHash) {
        Block.RegtestGenesisBlock.hash, Block.TestnetGenesisBlock.hash -> listOf(hardened(46), hardened(0))
        Block.LivenetGenesisBlock.hash -> listOf(hardened(47), hardened(0))
        else -> throw IllegalArgumentException("unknown chain hash ${chain.chainHash}")
    }

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

    /**
     * We use a separate key for cloud storage.
     * That is, the key we use to encyrpt/decrypt the blobs we store in the cloud.
    **/
    fun cloudKey(): ByteVector32 {
        val path = if (chain.isMainnet()) "m/51'/0'/0'/0" else "m/51'/1'/0'/0"
        val extPrivKey = DeterministicWallet.derivePrivateKey(master, path)

        return extPrivKey.privateKey.value
    }

    fun nodeId(): PublicKey {
        val keyManager = LocalKeyManager(seed = seed, chainHash = chain.chainHash)
        return keyManager.nodeId
    }

    override fun toString(): String = "Wallet"
}
