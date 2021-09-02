package fr.acinq.phoenix.data

import fr.acinq.bitcoin.*


data class Wallet(val seed: ByteVector64, val chain: Chain) {

    constructor(seed: ByteArray, chain: Chain): this(ByteVector64(seed), chain)

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

    override fun toString(): String = "Wallet"
}
