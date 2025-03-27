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
                    createdAt = row.createdAt
                )
            }
            contact.addresses.forEach { row ->
                queries.insertAddressForContact(
                    addressHash = row.id.toByteArray(),
                    contactId = contact.id.toString(),
                    address = row.address,
                    label = row.label,
                    createdAt = row.createdAt
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

            val contactIdString = contact.id.toString()

            // Update offers

            val existingOffers = queries.listOffersForContact(contactIdString)
                .executeAsList().mapNotNull { parseOfferRow(it) }

            val newOfferIds = contact.offers.map { it.id }.toSet()
            val existingOfferIds = existingOffers.map { it.id }.toSet()

            val (offerIdsToKeep, offerIdsToDelete) = existingOfferIds.partition { newOfferIds.contains(it) }
            val (offersToCompare, offersToInsert) = contact.offers.partition { offerIdsToKeep.contains(it.id) }

            val existingOffersMap = existingOffers.associateBy { it.id }
            val offersToUpdate = offersToCompare.filterNot { it == existingOffersMap[it.id] }

            offerIdsToDelete.forEach {
                queries.deleteContactOfferForOfferId(it.toByteArray())
            }
            offersToUpdate.forEach {
                queries.updateContactOffer(
                    offerId = it.id.toByteArray(),
                    label = it.label,
                )
            }
            offersToInsert.forEach {
                queries.insertOfferForContact(
                    offerId = it.id.toByteArray(),
                    contactId = contactIdString,
                    offer = it.offer.encode(),
                    label = it.label,
                    createdAt = it.createdAt
                )
            }

            // Update addresses
            //
            // Note: The address can be changed without changing the hash.
            // For example: old("johndoe@foobar.co") -> new("JohnDoe@foobar.co")
            // Since the hash is case-insensitive it remains unchanged.
            // In other words, this is just a requested formatting change by the user.

            val existingAddresses = queries.listAddressesForContact(contactIdString)
                .executeAsList().map { parseAddressRow(it) }

            val newAddressIds = contact.addresses.map { it.id }.toSet()
            val existingAddressIds = existingAddresses.map { it.id }.toSet()

            val (addressIdsToKeep, addressIdsToDelete) = existingAddressIds.partition { newAddressIds.contains(it) }
            val (addressesToCompare, addressesToInsert) = contact.addresses.partition { addressIdsToKeep.contains(it.id) }

            val existingAddressesMap = existingAddresses.associateBy { it.id }
            val addressesToUpdate = addressesToCompare.filterNot { it == existingAddressesMap[it.id] }

            addressIdsToDelete.forEach {
                queries.deleteContactAddressForAddressHash(it.toByteArray())
            }
            addressesToUpdate.forEach {
                queries.updateContactAddress(
                    label = it.label,
                    address = it.address,
                    addressHash = it.id.toByteArray()
                )
            }
            addressesToInsert.forEach {
                queries.insertAddressForContact(
                    addressHash = it.id.toByteArray(),
                    contactId = contactIdString,
                    address = it.address,
                    label = it.label,
                    createdAt = it.createdAt
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
                label = row.label,
                createdAt = row.created_at
            )
            is Try.Failure -> null
        }
    }

    private fun parseAddressRow(row: Contact_addresses): ContactAddress {
        return ContactAddress(
            id = row.address_hash.byteVector32(),
            address = row.address,
            label = row.label,
            createdAt = row.created_at
        )
    }
}