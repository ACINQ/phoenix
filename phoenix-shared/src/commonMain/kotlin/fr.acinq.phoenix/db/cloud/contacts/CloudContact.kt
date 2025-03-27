package fr.acinq.phoenix.db.cloud.contacts

import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer
import fr.acinq.phoenix.db.cloud.OfferSerializer
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import fr.acinq.phoenix.db.cloud.cborSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json

sealed class CloudContact {

    enum class Version(val value: Int) {
        // Initial version
        V0(0),
        V1(1)
        // Future versions go here
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
                addresses = listOf()
            )
        }

        companion object
    }

    @Serializable
    data class V1(
        @SerialName("v")
        val version: Int,
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val name: String,
        val useOfferKey: Boolean,
        val offers: List<ContactOfferWrapper>,
        val addresses: List<ContactAddressWrapper>
    ): CloudContact() {

        constructor(contact: ContactInfo) : this(
            version = Version.V1.value,
            id = contact.id,
            name = contact.name,
            useOfferKey = contact.useOfferKey,
            offers = contact.offers.map { ContactOfferWrapper(it) },
            addresses = contact.addresses.map { ContactAddressWrapper(it) }
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
        fun unwrap(photoUri: String?): ContactInfo {
            return ContactInfo(
                id = this.id,
                name = this.name,
                photoUri = photoUri,
                useOfferKey = this.useOfferKey,
                offers = this.offers.map { it.unwrap() },
                addresses = this.addresses.map { it.unwrap() }
            )
        }

        companion object

        @Serializable
        data class ContactOfferWrapper(
            @Serializable(with = OfferSerializer::class)
            val offer: OfferTypes.Offer,
            val label: String,
            val createdAt: Long
        ) {
            constructor(offer: ContactOffer) : this(
                offer = offer.offer,
                label = offer.label ?: "",
                createdAt = offer.createdAt
            )

            @Throws(Exception::class)
            fun unwrap(): ContactOffer {
                return ContactOffer(
                    offer = this.offer,
                    label = this.label,
                    createdAt = this.createdAt
                )
            }
        }

        @Serializable
        data class ContactAddressWrapper(
            val address: String,
            val label: String,
            val createdAt: Long
        ) {
            constructor(address: ContactAddress) : this(
                address = address.address,
                label = address.label ?: "",
                createdAt = address.createdAt
            )

            @Throws(Exception::class)
            fun unwrap(): ContactAddress {
                return ContactAddress(
                    address = this.address,
                    label = this.label,
                    createdAt = this.createdAt
                )
            }
        }
    }

    companion object {

        @OptIn(ExperimentalSerializationApi::class)
        @Throws(Exception::class)
        fun cborDeserializeVersion(
            blob: ByteArray
        ): Int {
            val serializer = cborSerializer()
            val header: VersionSwitch = serializer.decodeFromByteArray(blob)
            return header.version
        }

        @OptIn(ExperimentalSerializationApi::class)
        @Throws(Exception::class)
        fun cborDeserializeAndUnwrap(
            blob: ByteArray,
            photoUri: String?
        ): ContactInfo? {
            val serializer = cborSerializer()
            val header: VersionSwitch = serializer.decodeFromByteArray(blob)
            return when (header.version) {
                Version.V0.value -> {
                    serializer.decodeFromByteArray<V0>(blob).unwrap(photoUri)
                }
                Version.V1.value -> {
                    serializer.decodeFromByteArray<V1>(blob).unwrap(photoUri)
                }
                else -> null
            }
        }
    }
}