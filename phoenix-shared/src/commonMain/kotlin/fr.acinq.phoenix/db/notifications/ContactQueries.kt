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
import app.cash.sqldelight.coroutines.mapToList
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.byteVector
import fr.acinq.bitcoin.byteVector32
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer
import fr.acinq.phoenix.db.AppDatabase
import fr.acinq.phoenix.db.didDeleteContact
import fr.acinq.phoenix.db.didSaveContact
import fracinqphoenixdb.Contact_addresses
import fracinqphoenixdb.Contact_offers
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
                    createdAt = currentTimestampMillis(),
                )
            }
            contact.addresses.forEach { row ->
                queries.insertAddressForContact(
                    addressHash = ContactAddress.hash(row.address),
                    contactId = contact.id.toString(),
                    address = row.address,
                    createdAt = currentTimestampMillis()
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

            val existingOffers: Map<ByteVector32, ContactOffer> = queries.listOffersForContact(
                contactId = contact.id.toString()
            ).executeAsList().mapNotNull { offerRow ->
                parseOfferRow(offerRow)?.let { offer ->
                    offerRow.offer_id.byteVector32() to offer
                }
            }.toMap()
            contact.offers.forEach { row ->
                val offerId = row.offer.offerId
                val result: ComparisonResult = existingOffers[offerId]?.let { existingOffer ->
                    compareOffers(existingOffer, row)
                } ?: ComparisonResult.IsNew
                when (result) {
                    ComparisonResult.IsNew -> {
                        queries.insertOfferForContact(
                            offerId = offerId.toByteArray(),
                            contactId = contact.id.toString(),
                            offer = row.offer.encode(),
                            createdAt = currentTimestampMillis()
                        )
                    }
                    ComparisonResult.IsUpdated -> {
                        // Todo: update label
                    }
                    ComparisonResult.NoChanges -> {}
                }
            }

            val existingAddresses: Map<String, ContactAddress> = queries.listAddressesForContact(
                contactId = contact.id.toString()
            ).executeAsList().mapNotNull { addressRow ->
                addressRow.address_hash to parseAddressRow(addressRow)
            }.toMap()
            contact.addresses.forEach { row ->
                val addressHash = row.addressHash()
                val result: ComparisonResult = existingAddresses[addressHash]?.let { existing ->
                    compareAddresses(existing, row)
                } ?: ComparisonResult.IsNew
                when (result) {
                    ComparisonResult.IsNew -> {
                        queries.insertAddressForContact(
                            addressHash = addressHash,
                            contactId = contact.id.toString(),
                            address = row.address,
                            createdAt = currentTimestampMillis()
                        )
                    }
                    ComparisonResult.IsUpdated -> {
                        // Todo: update label & address
                    }
                    ComparisonResult.NoChanges -> {}
                }
            }
        }
    }

    fun getContact(contactId: UUID): ContactInfo? {
        return database.transactionWithResult {
            queries.getContact2(
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

    /** Retrieve a contact from a transaction ID - should be done in a transaction. */
    fun getContactForOffer(offerId: ByteVector32): ContactInfo? {
        return database.transactionWithResult {
            queries.getContactIdForOffer(
                offerId = offerId.toByteArray()
            ).executeAsOneOrNull()?.let {
                getContact(contactId = UUID.fromString(it.contact_id))
            }
        }
    }

    fun listContacts(): List<ContactInfo> {
        return database.transactionWithResult {

            val offers: MutableMap<UUID, MutableList<ContactOffer>> = mutableMapOf()
            queries.listContactOffers().executeAsList().forEach { offerRow ->
                parseOfferRow(offerRow)?.let { offer ->
                    val contactId = UUID.fromString(offerRow.contact_id)
                    offers[contactId]?.let {
                        it.add(offer)
                    } ?: run {
                        offers[contactId] = mutableListOf(offer)
                    }
                }
            }

            val addresses: MutableMap<UUID, MutableList<ContactAddress>> = mutableMapOf()
            queries.listContactAddresses().executeAsList().forEach { addressRow ->
                val contactId = UUID.fromString(addressRow.contact_id)
                val address = ContactAddress(
                    address = addressRow.address,
                    label = null,
                    createdAt = Instant.fromEpochMilliseconds(addressRow.created_at)
                )
                addresses[contactId]?.let {
                    it.add(address)
                } ?: run {
                    addresses[contactId] = mutableListOf(address)
                }
            }

            queries.listContacts2().executeAsList().map { contactRow ->
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
        return queries.listContacts2().asFlow().map {
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
                offer = result.get(),
                label = null,
                createdAt = Instant.fromEpochMilliseconds(row.created_at)
            )
            is Try.Failure -> null
        }
    }

    private fun parseAddressRow(row: Contact_addresses): ContactAddress {
        return ContactAddress(
            address = row.address,
            label = null,
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
        return if ((existing.label != current.label) || (existing.address != current.address)) {
            ComparisonResult.IsUpdated
        } else {
            ComparisonResult.NoChanges
        }
    }
}