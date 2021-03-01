package fr.acinq.phoenix.data

import fr.acinq.bitcoin.*

// @DefinitelyNotSerializable_DoNotPutMeInTheDatabase_Ever
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

    fun onchainAddress(path: String, isMainnet: Boolean): String {
        val chainHash = if (isMainnet) Block.LivenetGenesisBlock.hash else Block.TestnetGenesisBlock.hash
        val publicKey = DeterministicWallet.derivePrivateKey(master, path).publicKey
        return computeBIP84Address(publicKey, chainHash)
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
