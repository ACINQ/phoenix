package fr.acinq.phoenix.data

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eklair.utils.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.kodein.db.model.orm.Metadata

@Serializable
data class Wallet(
    override val id: Int = 0,
    val seed: ByteArray) : Metadata {

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
