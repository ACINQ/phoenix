package fr.acinq.phoenix.db.serialization.contacts.v1

import fr.acinq.bitcoin.io.ByteArrayInput
import fr.acinq.bitcoin.io.Input
import fr.acinq.lightning.serialization.InputExtensions.readBoolean
import fr.acinq.lightning.serialization.InputExtensions.readByteVector32
import fr.acinq.lightning.serialization.InputExtensions.readCollection
import fr.acinq.lightning.serialization.InputExtensions.readNullable
import fr.acinq.lightning.serialization.InputExtensions.readNumber
import fr.acinq.lightning.serialization.InputExtensions.readString
import fr.acinq.lightning.serialization.InputExtensions.readUuid
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer

object Deserialization {

    fun deserialize(bin: ByteArray): ContactInfo {
        val input = ByteArrayInput(bin)
        val version = input.read()
        require(version == Serialization.VERSION_MAGIC) { "incorrect version $version, expected ${Serialization.VERSION_MAGIC}" }
        return input.readContactInfo()
    }

    private fun Input.readContactInfo() = ContactInfo(
        id = readUuid(),
        name = readString(),
        photoUri = readNullable { readString() },
        useOfferKey = readBoolean(),
        offers = readCollection { readContactOffer() }.toList(),
        addresses = readCollection { readContactAddress() }.toList()
    )

    private fun Input.readContactOffer() = ContactOffer(
        id = readByteVector32(),
        offer = OfferTypes.Offer.decode(readString()).get(),
        label = readNullable { readString() },
        createdAt = readNumber()
    )

    private fun Input.readContactAddress() = ContactAddress(
        id = readByteVector32(),
        address = readString(),
        label = readNullable { readString() },
        createdAt = readNumber()
    )
}