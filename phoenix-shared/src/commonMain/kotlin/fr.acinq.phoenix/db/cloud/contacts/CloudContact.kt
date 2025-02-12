package fr.acinq.phoenix.db.cloud.contacts

import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer
import fr.acinq.phoenix.db.cloud.OfferSerializer
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import fr.acinq.phoenix.db.cloud.cborSerializer
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class CloudContactVersion(val value: Int) {
    // Initial version
    V0(0),
    V1(1)
    // Future versions go here
}

@Serializable
data class CloudContactVersionSwitch(
    @SerialName("v")
    val version: Int
)

@Serializable
data class CloudContact_V1(
    @SerialName("v")
    val version: Int,
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val useOfferKey: Boolean,
    val offers: List<@Serializable(OfferSerializer::class) OfferTypes.Offer>,
) {
    @Throws(Exception::class)
    fun unwrap(photoUri: String?): ContactInfo {
        val now = Clock.System.now()
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
data class CloudContact_V2(
    @SerialName("v")
    val version: Int,
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val useOfferKey: Boolean,
    val offers: List<ContactOfferWrapper>,
    val addresses: List<ContactAddressWrapper>
) {
    constructor(contact: ContactInfo) : this(
        version = CloudContactVersion.V0.value,
        id = contact.id,
        name = contact.name,
        useOfferKey = contact.useOfferKey,
        offers = contact.offers.map { ContactOfferWrapper(it) },
        addresses = contact.addresses.map { ContactAddressWrapper(it) }
    )

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
            createdAt = offer.createdAt.toEpochMilliseconds()
        )

        @Throws(Exception::class)
        fun unwrap(): ContactOffer {
            return ContactOffer(
                offer = this.offer,
                label = this.label,
                createdAt = Instant.fromEpochMilliseconds(this.createdAt)
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
            createdAt = address.createdAt.toEpochMilliseconds()
        )

        @Throws(Exception::class)
        fun unwrap(): ContactAddress {
            return ContactAddress(
                address = this.address,
                label = this.label,
                createdAt = Instant.fromEpochMilliseconds(this.createdAt)
            )
        }
    }
}

typealias CloudContact = CloudContact_V2

@OptIn(ExperimentalSerializationApi::class)
fun CloudContact.cborSerialize(): ByteArray {
    return Cbor.encodeToByteArray(this)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun CloudContact_V2.Companion.cborDeserializeAndUnwrap(
    blob: ByteArray,
    photoUri: String?
): ContactInfo? {
    val serializer = cborSerializer()
    val header: CloudContactVersionSwitch = serializer.decodeFromByteArray(blob)
    return when (header.version) {
        CloudContactVersion.V0.value -> {
            serializer.decodeFromByteArray<CloudContact_V1>(blob).unwrap(photoUri)
        }
        CloudContactVersion.V1.value -> {
            serializer.decodeFromByteArray<CloudContact_V2>(blob).unwrap(photoUri)
        }
        else -> null
    }
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
fun CloudContact.jsonSerialize(): ByteArray {
    return Json.encodeToString(this).encodeToByteArray()
}