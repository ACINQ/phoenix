package fr.acinq.phoenix.db.contacts

import fr.acinq.bitcoin.byteVector32
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer
import fr.acinq.phoenix.db.sqldelight.AppDatabase
import fr.acinq.phoenix.db.sqldelight.Contact_offers_old

class OldContactQueries(val database: AppDatabase) {

    val queries = database.contactsOldQueries

    fun fetchContactsBatch(limit: Long): List<ContactInfo> {
        return database.transactionWithResult {
            queries.fetchContactsBatch(limit, ::mapContact).executeAsList().map { contact ->
                val offers = queries.listOffersForContact(
                    contactId = contact.id.toString()
                ).executeAsList().mapNotNull {
                    parseOfferRow(it)
                }
                contact.copy(offers = offers)
            }
        }
    }

    fun deleteContacts(contacts: List<ContactInfo>) {
        database.transaction {
            contacts.forEach { contact ->
                queries.deleteContact(contactId = contact.id.toString())
            }
        }
    }

    private fun parseOfferRow(row: Contact_offers_old): ContactOffer? {
        return when (val result = OfferTypes.Offer.decode(row.offer)) {
            is Try.Success -> ContactOffer(
                id = row.offer_id.byteVector32(),
                offer = result.get(),
                label = null,
                createdAt = row.created_at
            )
            is Try.Failure -> null
        }
    }

    companion object {

        @Suppress("UNUSED_PARAMETER")
        private fun mapContact(
            id: String,
            name: String,
            photo_uri: String?,
            use_offer_key: Boolean,
            created_at: Long,
            updated_at: Long?
        ): ContactInfo {
            val contactId = UUID.fromString(id)
            return ContactInfo(
                id = contactId,
                name = name,
                photoUri = photo_uri,
                useOfferKey = use_offer_key,
                offers = listOf(),
                addresses = listOf()
            )
        }
    }
}