package fr.acinq.phoenix.data

import fr.acinq.bitcoin.*
import kotlinx.serialization.Serializable
import org.kodein.db.model.orm.Metadata

@Serializable
data class Wallet(
    // Unique ID a their is only one wallet per app
    override val id: Int = 0,
    val mnemonics: List<String>) : Metadata {

    val seed by lazy { MnemonicCode.toSeed(mnemonics, "") }
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
        val chainHash=  if (isMainnet) Block.LivenetGenesisBlock.hash else Block.TestnetGenesisBlock.hash
        val publicKey = DeterministicWallet.derivePrivateKey(master, path).publicKey
        return computeBIP84Address(publicKey, chainHash)
    }

    // Recommended when data class props contain arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Wallet

        if (id != other.id) return false
        if (!seed.contentEquals(other.seed)) return false

        return true
    }
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + seed.contentHashCode()
        return result
    }
}
