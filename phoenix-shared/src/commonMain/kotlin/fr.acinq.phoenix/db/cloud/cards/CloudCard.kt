package fr.acinq.phoenix.db.cloud.cards

import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.phoenix.data.BitcoinUnit
import fr.acinq.phoenix.data.BoltCardInfo
import fr.acinq.phoenix.data.BoltCardKeySet
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.SpendingLimit
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import fr.acinq.phoenix.db.cloud.cborSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed class CloudCard {

    enum class Version(val value: Int) {
        // Initial version
        V0(0)
        // Future versions go here
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
    data class V0(
        @SerialName("v")
        val version: Int,
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val name: String,
        @ByteString val key0: ByteArray,
        @ByteString val uid: ByteArray,
        val lastKnownCounter: UInt,
        val isFrozen: Boolean,
        val isArchived: Boolean,
        val isReset: Boolean,
        val isForeign: Boolean,
        val dailyLimit: SpendingLimitWrapper?,
        val monthlyLimit: SpendingLimitWrapper?,
        val createdAt: Long
    ): CloudCard() {

        constructor(card: BoltCardInfo) : this(
            version = Version.V0.value,
            id = card.id,
            name = card.name,
            key0 = card.keys.key0.toByteArray(),
            uid = card.uid.toByteArray(),
            lastKnownCounter = card.lastKnownCounter,
            isFrozen = card.isFrozen,
            isArchived = card.isArchived,
            isReset = card.isReset,
            isForeign = card.isForeign,
            dailyLimit = card.dailyLimit?.let { SpendingLimitWrapper(it) },
            monthlyLimit = card.monthlyLimit?.let { SpendingLimitWrapper(it) },
            createdAt = card.createdAt.toEpochMilliseconds()
        )

        @OptIn(ExperimentalSerializationApi::class)
        fun cborSerialize(): ByteArray {
            return Cbor.encodeToByteArray(this)
        }

        /**
         * For DEBUGGING:
         *
         * You can use the jsonSerializer to see what the data looks like.
         * Just keep in mind that the ByteArray's will be encoded super-inefficiently.
         * That's because we're optimizing for Cbor.
         * To optimize for JSON, you would use ByteVector's,
         * and encode the data as Base64 via ByteVectorJsonSerializer.
         */
        fun jsonSerialize(): ByteArray {
            return Json.encodeToString(this).encodeToByteArray()
        }

        @Throws(Exception::class)
        fun unwrap(): BoltCardInfo {
            val keys = BoltCardKeySet(key0 = this.key0.toByteVector())
            return BoltCardInfo(
                id = this.id,
                name = this.name,
                keys = keys,
                uid = this.uid.toByteVector(),
                lastKnownCounter = this.lastKnownCounter,
                isFrozen = this.isFrozen,
                isArchived = this.isArchived,
                isReset = this.isReset,
                isForeign = this.isForeign,
                dailyLimit = this.dailyLimit?.unwrap(),
                monthlyLimit = this.monthlyLimit?.unwrap(),
                createdAt = Instant.fromEpochMilliseconds(this.createdAt)
            )
        }

        companion object

        @Serializable
        data class SpendingLimitWrapper(
            val currency: String,
            val amount: Double
        ) {
            constructor(limit: SpendingLimit) : this(
                currency = limit.currency.displayCode,
                amount = limit.amount
            )

            fun unwrap(): SpendingLimit? {
                val currency = FiatCurrency.valueOfOrNull(currency) ?: BitcoinUnit.valueOfOrNull(this.currency)
                return currency?.let {
                    SpendingLimit(currency, this.amount)
                }
            }
        }
    }

    companion object {

        @OptIn(ExperimentalSerializationApi::class)
        @Throws(Exception::class)
        fun cborDeserializeAndUnwrap(
            blob: ByteArray
        ): BoltCardInfo? {
            return cborSerializer().decodeFromByteArray<V0>(blob).unwrap()
        }
    }
}