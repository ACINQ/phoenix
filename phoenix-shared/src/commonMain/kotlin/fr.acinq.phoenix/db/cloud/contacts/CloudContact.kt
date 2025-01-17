package fr.acinq.phoenix.db.cloud.contacts

import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.db.cloud.OfferSerializer
import fr.acinq.phoenix.db.cloud.UUIDSerializer
import fr.acinq.phoenix.db.cloud.cborSerializer
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
    V0(0)
    // Future versions go here
}

@Serializable
data class CloudContact(
    @SerialName("v")
    val version: Int,
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val useOfferKey: Boolean,
    val offers: List<@Serializable(OfferSerializer::class) OfferTypes.Offer>,
) {
    constructor(contact: ContactInfo) : this(
        version = CloudContactVersion.V0.value,
        id = contact.id,
        name = contact.name,
        useOfferKey = contact.useOfferKey,
        offers = contact.offers
    )

    @Throws(Exception::class)
    fun unwrap(photoUri: String?): ContactInfo? {
        return ContactInfo(
            id = this.id,
            name = this.name,
            photoUri = photoUri,
            useOfferKey = this.useOfferKey,
            offers = this.offers
        )
    }

    companion object
}

@OptIn(ExperimentalSerializationApi::class)
fun CloudContact.cborSerialize(): ByteArray {
    return Cbor.encodeToByteArray(this)
}

@OptIn(ExperimentalSerializationApi::class)
@Throws(Exception::class)
fun CloudContact.Companion.cborDeserialize(
    blob: ByteArray
): CloudContact {
    return cborSerializer().decodeFromByteArray(blob)
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