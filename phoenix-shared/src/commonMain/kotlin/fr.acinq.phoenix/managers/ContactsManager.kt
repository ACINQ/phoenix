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

package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.db.SqliteAppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class ContactsManager(
    private val loggerFactory: LoggerFactory,
    private val appDb: SqliteAppDb,
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        appDb = business.appDb,
    )

    private val log = loggerFactory.newLogger(this::class)

    private val _contactsList = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contactsList = _contactsList.asStateFlow()

    init {
        launch { appDb.listContacts().collect { _contactsList.value = it } }
    }

    suspend fun getContactForOffer(offer: OfferTypes.Offer): ContactInfo? {
        return appDb.getContactForOffer(offer.offerId)
    }

    suspend fun saveNewContact(
        name: String,
        photo: ByteArray?,
        offer: OfferTypes.Offer
    ): ContactInfo {
        val contact = ContactInfo(id = UUID.randomUUID(), name = name, photo = photo?.toByteVector(), offers = listOf(offer))
        appDb.saveContact(contact)
        return contact
    }

    suspend fun updateContact(
        contactId: UUID,
        name: String,
        photo: ByteArray?,
        offers: List<OfferTypes.Offer>
    ): ContactInfo {
        val contact = ContactInfo(id = contactId, name = name, photo = photo?.toByteVector(), offers = offers)
        appDb.updateContact(contact)
        return contact
    }

    suspend fun deleteContact(contactId: UUID) {
        appDb.deleteContact(contactId)
    }

    suspend fun detachOfferFromContact(offerId: ByteVector32) {
        appDb.deleteOfferContactLink(offerId)
    }
}