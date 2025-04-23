package fr.acinq.phoenix.db.serialization.contacts

import fr.acinq.phoenix.data.ContactInfo

object Serialization {

    fun serialize(contact: ContactInfo): ByteArray {
        return fr.acinq.phoenix.db.serialization.contacts.v1.Serialization.serialize(contact)
    }

    fun deserialize(bin: ByteArray): Result<ContactInfo> {
        return runCatching {
            when (val version = bin.first().toInt()) {
                1 -> fr.acinq.phoenix.db.serialization.contacts.v1.Deserialization.deserialize(bin)
                else -> error("unknown version $version")
            }
        }
    }
}