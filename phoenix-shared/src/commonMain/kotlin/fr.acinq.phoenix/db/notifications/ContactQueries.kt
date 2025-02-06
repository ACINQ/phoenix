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
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.db.didDeleteContact
import fr.acinq.phoenix.db.didSaveContact
import fr.acinq.phoenix.db.sqldelight.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContactQueries(val database: AppDatabase) {

    val queries = database.contactsQueries

    fun saveContact(contact: ContactInfo, notify: Boolean = true) {
        database.transaction {
            queries.insertContact(
                id = contact.id.toString(),
                name = contact.name,
                photoUri = contact.photoUri,
                useOfferKey = contact.useOfferKey,
                createdAt = currentTimestampMillis(),
                updatedAt = null
            )
            contact.offers.forEach { offer ->
                queries.insertOfferForContact(
                    offerId = offer.offerId.toByteArray(),
                    contactId = contact.id.toString(),
                    offer = offer.encode(),
                    createdAt = currentTimestampMillis(),
                )
            }
            if (notify) {
                didSaveContact(contact.id, database)
            }
        }
    }

    fun updateContact(contact: ContactInfo) {
        database.transaction {
            queries.updateContact(
                name = contact.name,
                photoUri = contact.photoUri,
                useOfferKey = contact.useOfferKey,
                updatedAt = currentTimestampMillis(),
                contactId = contact.id.toString()
            )
            didSaveContact(contact.id, database)
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
            queries.getContact(contactId = contactId.toString()).executeAsOneOrNull()?.let {
                val offers = it.offers.split(",").map {
                    OfferTypes.Offer.decode(it)
                }.filterIsInstance<Try.Success<OfferTypes.Offer>>().map {
                    it.get()
                }
                ContactInfo(contactId, it.name, it.photo_uri, it.use_offer_key, offers)
            }
        }
    }

    /** Retrieve a contact from a transaction ID - should be done in a transaction. */
    fun getContactForOffer(offerId: ByteVector32): ContactInfo? {
        return database.transactionWithResult {
            queries.getContactIdForOffer(offerId = offerId.toByteArray()).executeAsOneOrNull()?.let {
                val contactId = it.contact_id
                queries.getContact(contactId = contactId).executeAsOneOrNull()
            }?.let {
                val offers = it.offers.split(",").map {
                    OfferTypes.Offer.decode(it)
                }.filterIsInstance<Try.Success<OfferTypes.Offer>>().map {
                    it.get()
                }
                if (offers.isEmpty()) {
                    null
                } else {
                    ContactInfo(UUID.fromString(it.id), it.name, it.photo_uri, it.use_offer_key, offers)
                }
            }
        }
    }

    fun listContacts(): List<ContactInfo> {
        return queries.listContacts().executeAsList().map {
            val offers = it.offers?.split(",")?.map {
                OfferTypes.Offer.decode(it)
            }?.filterIsInstance<Try.Success<OfferTypes.Offer>>()?.map {
                it.get()
            } ?: emptyList()
            ContactInfo(UUID.fromString(it.id), it.name, it.photo_uri, it.use_offer_key, offers)
        }
    }

    fun monitorContactsFlow(): Flow<List<ContactInfo>> {
        return queries.listContacts().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map {
                val offers = it.offers?.split(",")?.map {
                    OfferTypes.Offer.decode(it)
                }?.filterIsInstance<Try.Success<OfferTypes.Offer>>()?.map {
                    it.get()
                } ?: emptyList()
                ContactInfo(UUID.fromString(it.id), it.name, it.photo_uri, it.use_offer_key, offers)
            }
        }
    }

    fun deleteContact(contactId: UUID) {
        database.transaction {
            queries.deleteContactOfferForContactId(contactId = contactId.toString())
            queries.deleteContact(contactId = contactId.toString())
            didDeleteContact(contactId, database)
        }
    }

    fun deleteOfferContactLink(offerId: ByteVector32) {
        queries.deleteContactOfferForOfferId(offerId.toByteArray())
    }

}