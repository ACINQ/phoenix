package fr.acinq.phoenix.data

import fr.acinq.bitcoin.*


data class Wallet(val seed: ByteArray) {

    init {
        require(seed.size == 64) { "invalid seed.size" }
    }

    private val master by lazy { DeterministicWallet.generate(seed) }

    fun masterPublicKey(path: String, isMainnet: Boolean): String {
        val publicKey =
            DeterministicWallet.publicKey(
                DeterministicWallet.derivePrivateKey(master, path)
            )
        return DeterministicWallet.encode(
            input = publicKey,
            prefix = if (isMainnet) DeterministicWallet.zpub else DeterministicWallet.vpub
        )
    }

    /** Get the wallet (xpub, path) */
    fun xpub(isMainnet: Boolean): Pair<String, String> {
        val masterPubkeyPath = if (isMainnet) "m/84'/0'/0'" else "m/84'/1'/0'"
        return masterPublicKey(masterPubkeyPath, isMainnet) to masterPubkeyPath
    }

    fun onchainAddress(path: String, isMainnet: Boolean): String {
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
    fun cloudKeyAndEncryptedNodeId(chain: Chain): Pair<ByteVector32, String> {
        val isTestnet = when (chain) {
            is Chain.Testnet, is Chain.Regtest -> true
            else -> false
        }
        val path = if (isTestnet) "m/51'/1'/0'/0" else "m/51'/0'/0'/0"

        val extPrivKey = DeterministicWallet.derivePrivateKey(master, path)

        val cloudKey = extPrivKey.privateKey.value
        val hash = Crypto.hash160(cloudKey).byteVector().toHex()

        return Pair(cloudKey, hash)
    }

    // Recommended when data class props contain arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Wallet

        if (!seed.contentEquals(other.seed)) return false

        return true
    }

    override fun hashCode(): Int {
        return seed.contentHashCode()
    }

    override fun toString(): String = "Wallet"
}
