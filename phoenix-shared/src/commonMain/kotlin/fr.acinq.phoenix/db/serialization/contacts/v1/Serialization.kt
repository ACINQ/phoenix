package fr.acinq.phoenix.db.serialization.contacts.v1

import fr.acinq.bitcoin.io.ByteArrayOutput
import fr.acinq.bitcoin.io.Output
import fr.acinq.lightning.serialization.OutputExtensions.writeBoolean
import fr.acinq.lightning.serialization.OutputExtensions.writeByteVector32
import fr.acinq.lightning.serialization.OutputExtensions.writeCollection
import fr.acinq.lightning.serialization.OutputExtensions.writeNullable
import fr.acinq.lightning.serialization.OutputExtensions.writeNumber
import fr.acinq.lightning.serialization.OutputExtensions.writeString
import fr.acinq.lightning.serialization.OutputExtensions.writeUuid
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer

object Serialization {

    const val VERSION_MAGIC = 1

    fun serialize(o: ContactInfo): ByteArray {
        val out = ByteArrayOutput()
        out.write(VERSION_MAGIC)
        out.writeContactInfo(o)
        return out.toByteArray()
    }

    private fun Output.writeContactInfo(o: ContactInfo) {
        writeUuid(o.id)
        writeString(o.name)
        writeNullable(o.photoUri) { writeString(it) }
        writeBoolean(o.useOfferKey)
        writeCollection(o.offers) { writeContactOffer(it) }
        writeCollection(o.addresses) { writeContactAddress(it) }
    }

    private fun Output.writeContactOffer(o: ContactOffer) {
        writeByteVector32(o.id)
        writeString(o.offer.encode())
        writeNullable(o.label) { writeString(it) }
        writeNumber(o.createdAt)
    }

    private fun Output.writeContactAddress(o: ContactAddress) {
        writeByteVector32(o.id)
        writeString(o.address)
        writeNullable(o.label) { writeString(it) }
        writeNumber(o.createdAt)
    }
}