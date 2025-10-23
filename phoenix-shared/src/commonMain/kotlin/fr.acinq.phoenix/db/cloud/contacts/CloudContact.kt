package fr.acinq.phoenix.db.cloud.contacts

import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer
import fr.acinq.phoenix.db.cloud.OfferSerializer
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import fr.acinq.phoenix.db.cloud.cborSerializer
import fr.acinq.phoenix.db.serialization.contacts.Serialization
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray

sealed class CloudContact {

    enum class Version(val value: Int) {
        // Initial version
        V0(0)
    }

    @Serializable
    data class VersionSwitch(
        @SerialName("v")
        val version: Int
    )

    @Serializable
    data class V0(
        @SerialName("v")
        val version: Int,
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val name: String,
        val useOfferKey: Boolean,
        val offers: List<@Serializable(OfferSerializer::class) OfferTypes.Offer>,
    ): CloudContact()  {

        @Throws(Exception::class)
        fun unwrap(photoUri: String?): ContactInfo {
            val now = currentTimestampMillis()
            val mappedOffers: List<ContactOffer> = this.offers.map {
                ContactOffer(offer = it, label = "", createdAt = now)
            }
            return ContactInfo(
                id = this.id,
                name = this.name,
                photoUri = photoUri,
                useOfferKey = this.useOfferKey,
                offers = mappedOffers,
                addresses = listOf(),
                secrets = listOf()
            )
        }

        companion object
    }

    data class V1(
        val contact: ContactInfo
    ): CloudContact() {

        fun serialize(): ByteArray {
            val cleanContact = contact.copy(photoUri = null)
            val cloudVersion = byteArrayOf(1.toByte())
            val serializedData = Serialization.serialize(cleanContact)
            return cloudVersion + serializedData
        }
    }

    companion object {

        @OptIn(ExperimentalSerializationApi::class)
        @Throws(Exception::class)
        private fun cborDeserializeAndUnwrap(
            blob: ByteArray,
            photoUri: String?
        ): ContactInfo? {
            val serializer = cborSerializer()
            val header = serializer.decodeFromByteArray<VersionSwitch>(blob)
            return when (header.version) {
                Version.V0.value -> {
                    serializer.decodeFromByteArray<V0>(blob).unwrap(photoUri)
                }
                else -> null
            }
        }

        fun deserialize(
            blob: ByteArray,
            photoUri: String?
        ): ContactInfo? {
            return kotlin.runCatching {
                when (val version = blob.first()) {
                    1.toByte() -> {
                        val serializedData = blob.sliceArray(1..<blob.size)
                        Serialization.deserialize(serializedData).getOrThrow()
                    }
                    else -> {
                        throw IllegalArgumentException("unknown version: $version")
                    }
                }.copy(photoUri = photoUri)
            }.recoverCatching {
                cborDeserializeAndUnwrap(blob, photoUri)
            }.getOrNull()
        }
    }
}