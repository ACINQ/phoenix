/*
 * Copyright 2024 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.db.notifications

import app.cash.sqldelight.coroutines.asFlow
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.byteVector32
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.db.didDeleteContact
import fr.acinq.phoenix.db.didSaveContact
import fr.acinq.phoenix.db.sqldelight.AppDatabase
import fr.acinq.phoenix.data.ContactOffer
import fr.acinq.phoenix.db.sqldelight.Contact_addresses
import fr.acinq.phoenix.db.sqldelight.Contact_offers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

class ContactQueries(val database: AppDatabase) {

    val queries = database.contactsQueries

    fun saveContact(contact: ContactInfo, notify: Boolean = true) {
        database.transaction {
            val contactExists = queries.existsContact(
                id = contact.id.toString()
            ).executeAsOne() > 0
            if (contactExists) {
                updateExistingContact(contact)
            } else {
                saveNewContact(contact)
            }
            if (notify) {
                didSaveContact(contact.id, database)
            }
        }
    }

    private fun saveNewContact(contact: ContactInfo) {
        database.transaction {
            queries.insertContact(
                id = contact.id.toString(),
                name = contact.name,
                photoUri = contact.photoUri,
                useOfferKey = contact.useOfferKey,
                createdAt = currentTimestampMillis(),
                updatedAt = null
            )
            contact.offers.forEach { row ->
                queries.insertOfferForContact(
                    offerId = row.offer.offerId.toByteArray(),
                    contactId = contact.id.toString(),
                    offer = row.offer.encode(),
                    label = row.label,
                    createdAt = row.createdAt.toEpochMilliseconds()
                )
            }
            contact.addresses.forEach { row ->
                queries.insertAddressForContact(
                    addressHash = row.id.toByteArray(),
                    contactId = contact.id.toString(),
                    address = row.address,
                    label = row.label,
                    createdAt = row.createdAt.toEpochMilliseconds()
                )
            }
        }
    }

    private fun updateExistingContact(contact: ContactInfo) {
        database.transaction {
            queries.updateContact(
                name = contact.name,
                photoUri = contact.photoUri,
                useOfferKey = contact.useOfferKey,
                updatedAt = currentTimestampMillis(),
                contactId = contact.id.toString()
            )

            val existingOffers: MutableMap<ByteVector32, ContactOffer> =
                queries.listOffersForContact(
                    contactId = contact.id.toString()
                ).executeAsList().mapNotNull { offerRow ->
                    parseOfferRow(offerRow)?.let { offer ->
                        offerRow.offer_id.byteVector32() to offer
                    }
                }.toMap().toMutableMap()
            contact.offers.forEach { row ->
                val result: ComparisonResult =
                    existingOffers.remove(row.id)?.let { existingOffer ->
                        compareOffers(existingOffer, row)
                    } ?: ComparisonResult.IsNew
                when (result) {
                    ComparisonResult.IsNew -> {
                        queries.insertOfferForContact(
                            offerId = row.id.toByteArray(),
                            contactId = contact.id.toString(),
                            offer = row.offer.encode(),
                            label = row.label,
                            createdAt = row.createdAt.toEpochMilliseconds()
                        )
                    }
                    ComparisonResult.IsUpdated -> {
                        queries.updateContactOffer(
                            label = row.label,
                            offerId = row.id.toByteArray()
                        )
                    }
                    ComparisonResult.NoChanges -> {}
                }
            }
            // In the loop above we removed every matching offer.
            // So any items leftover have been deleted from the contact.
            existingOffers.forEach { (key, _) ->
                queries.deleteContactOfferForOfferId(
                    offerId = key.toByteArray()
                )
            }

            val existingAddresses: MutableMap<ByteVector32, ContactAddress> =
                queries.listAddressesForContact(
                    contactId = contact.id.toString()
                ).executeAsList().map { addressRow ->
                    addressRow.address_hash.byteVector32() to parseAddressRow(addressRow)
                }.toMap().toMutableMap()
            contact.addresses.forEach { row ->
                val result: ComparisonResult =
                    existingAddresses.remove(row.id)?.let { existing ->
                        compareAddresses(existing, row)
                    } ?: ComparisonResult.IsNew
                when (result) {
                    ComparisonResult.IsNew -> {
                        queries.insertAddressForContact(
                            addressHash = row.id.toByteArray(),
                            contactId = contact.id.toString(),
                            address = row.address,
                            label = row.label,
                            createdAt = row.createdAt.toEpochMilliseconds()
                        )
                    }
                    ComparisonResult.IsUpdated -> {
                        queries.updateContactAddress(
                            label = row.label,
                            address = row.address,
                            addressHash = row.id.toByteArray()
                        )
                    }
                    ComparisonResult.NoChanges -> {}
                }
            }
            // In the loop above we removed every matching address.
            // So any items leftover have been deleted from the contact.
            existingAddresses.forEach { (key, _) ->
                queries.deleteContactAddressForAddressHash(
                    addressHash = key.toByteArray()
                )
            }
        }
    }

    fun existsContact(contactId: UUID): Boolean {
        return database.transactionWithResult {
            queries.existsContact(
                id = contactId.toString()
            ).executeAsOne() > 0
        }
    }

    fun getContact(contactId: UUID): ContactInfo? {
        return database.transactionWithResult {
            queries.getContact(
                contactId = contactId.toString()
            ).executeAsOneOrNull()?.let { contactRow ->
                val offers: List<ContactOffer> = queries.listOffersForContact(
                    contactId = contactId.toString()
                ).executeAsList().mapNotNull { offerRow ->
                    parseOfferRow(offerRow)
                }
                val addresses: List<ContactAddress> = queries.listAddressesForContact(
                    contactId = contactId.toString()
                ).executeAsList().map { addressRow ->
                    parseAddressRow(addressRow)
                }
                ContactInfo(
                    id = contactId,
                    name = contactRow.name,
                    photoUri = contactRow.photo_uri,
                    useOfferKey = contactRow.use_offer_key,
                    offers = offers,
                    addresses = addresses
                )
            }
        }
    }

    fun listContacts(): List<ContactInfo> {
        return database.transactionWithResult {

            val offers: MutableMap<UUID, MutableList<ContactOffer>> = mutableMapOf()
            queries.listContactOffers().executeAsList().forEach { offerRow ->
                parseOfferRow(offerRow)?.let { offer ->
                    val contactId = UUID.fromString(offerRow.contact_id)
                    offers[contactId]?.add(offer) ?: run {
                        offers[contactId] = mutableListOf(offer)
                    }
                }
            }

            val addresses: MutableMap<UUID, MutableList<ContactAddress>> = mutableMapOf()
            queries.listContactAddresses().executeAsList().forEach { addressRow ->
                parseAddressRow(addressRow).let { address ->
                    val contactId = UUID.fromString(addressRow.contact_id)
                    addresses[contactId]?.add(address) ?: run {
                        addresses[contactId] = mutableListOf(address)
                    }
                }
            }

            queries.listContacts().executeAsList().map { contactRow ->
                val contactId = UUID.fromString(contactRow.id)
                ContactInfo(
                    id = contactId,
                    name = contactRow.name,
                    photoUri = contactRow.photo_uri,
                    useOfferKey = contactRow.use_offer_key,
                    offers = offers[contactId]?.toList() ?: listOf(),
                    addresses = addresses[contactId]?.toList() ?: listOf()
                )
            }
        }
    }

    fun monitorContactsFlow(context: CoroutineContext): Flow<List<ContactInfo>> {
        return queries.listContacts().asFlow().map {
            withContext(context) {
                listContacts()
            }
        }
    }

    fun deleteContact(contactId: UUID) {
        database.transaction {
            queries.deleteContactOffersForContactId(contactId = contactId.toString())
            queries.deleteContactAddressesForContactId(contactId = contactId.toString())
            queries.deleteContact(contactId = contactId.toString())
            didDeleteContact(contactId, database)
        }
    }

    private fun parseOfferRow(row: Contact_offers): ContactOffer? {
        return when (val result = OfferTypes.Offer.decode(row.offer)) {
            is Try.Success -> ContactOffer(
                id = row.offer_id.byteVector32(),
                offer = result.get(),
                label = row.label ?: "",
                createdAt = Instant.fromEpochMilliseconds(row.created_at)
            )
            is Try.Failure -> null
        }
    }

    private fun parseAddressRow(row: Contact_addresses): ContactAddress {
        return ContactAddress(
            id = row.address_hash.byteVector32(),
            address = row.address,
            label = row.label ?: "",
            createdAt = Instant.fromEpochMilliseconds(row.created_at)
        )
    }

    private enum class ComparisonResult {
        IsNew,
        IsUpdated,
        NoChanges
    }

    private fun compareOffers(
        existing: ContactOffer,
        current: ContactOffer
    ): ComparisonResult {
        return if (existing.label != current.label) {
            ComparisonResult.IsUpdated
        } else {
            ComparisonResult.NoChanges
        }
    }

    private fun compareAddresses(
        existing: ContactAddress,
        current: ContactAddress
    ): ComparisonResult {
        // Note: The address can be changed without changing the hash.
        // For example: old("johndoe@foobar.co") -> new("JohnDoe@foobar.co")
        // Since the hash is case-insensitive it remains unchanged.
        // In other words, this is just a requested formatting change by the user.
        return if ((existing.label != current.label) || (existing.address != current.address)) {
            ComparisonResult.IsUpdated
        } else {
            ComparisonResult.NoChanges
        }
    }
}