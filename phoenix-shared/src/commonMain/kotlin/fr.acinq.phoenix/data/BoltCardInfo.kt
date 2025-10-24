package fr.acinq.phoenix.data

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.Lightning
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.toByteVector
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class BoltCardInfo(
    val id: UUID,
    val name: String,
    val keys: BoltCardKeySet,
    val uid: ByteVector,
    val lastKnownCounter: UInt,
    val isFrozen: Boolean,
    val isArchived: Boolean,
    val isReset: Boolean,
    val isForeign: Boolean,
    val dailyLimit: SpendingLimit?,
    val monthlyLimit: SpendingLimit?,
    val createdAt: Instant
) {
    init {
        require(uid.size() == UID_SIZE) { "Invalid uid size: ${uid.size()} != $UID_SIZE" }
    }

    constructor(
        name: String,
        keys: BoltCardKeySet,
        uid: ByteVector,
        isForeign: Boolean = false
    ) : this(
        id = UUID.randomUUID(),
        name = name,
        keys = keys,
        uid = uid,
        lastKnownCounter = 0u,
        isFrozen = false,
        isArchived = false,
        isReset = false,
        isForeign = isForeign,
        dailyLimit = null,
        monthlyLimit = null,
        createdAt = Clock.System.now()
    )

    companion object {
        /** UID size in bytes. */
        const val UID_SIZE = 7

        /**
         * Useful for debugging & unit testing.
         * Note that the UID of a card is programmed into the chip (immutable).
         */
        fun randomUid() = Lightning.randomBytes(length = UID_SIZE).toByteVector()
    }
}

data class BoltCardKeySet(
    val key0: ByteVector
) {
    init {
        require(key0.size() == KEY_SIZE) { "Invalid key size: ${key0.size()} != $KEY_SIZE" }
    }

    val piccDataKey: ByteVector by lazy {
        keyGen("piccDataKey")
    }

    val cmacKey: ByteVector by lazy {
        keyGen("cmacKey")
    }

    private fun keyGen(keyId: String): ByteVector {
        val inner: ByteArray = sha256Hash(key0.toByteArray())
        val outer: ByteArray = sha256Hash(keyId.toByteArray(Charsets.UTF_8))

        val hashMe: ByteArray = outer + inner + outer
        return sha256Hash(hashMe).toByteVector().take(KEY_SIZE)
    }

    private fun sha256Hash(bytes: ByteArray): ByteArray {
        return Crypto.sha256(bytes)
    }

    companion object {
        /** Key size in bytes. */
        const val KEY_SIZE = 16

        fun random() = BoltCardKeySet(
            key0 = Lightning.randomBytes(length = KEY_SIZE).toByteVector()
        )
    }
}

data class SpendingLimit(
    val currency: CurrencyUnit,
    val amount: Double
)
