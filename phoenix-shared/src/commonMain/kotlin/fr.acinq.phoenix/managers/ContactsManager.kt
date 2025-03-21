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
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata
import fr.acinq.phoenix.utils.extensions.outgoingInvoiceRequest
import kotlin.collections.List
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

    private val _contactsMap = MutableStateFlow<Map<UUID, ContactInfo>>(emptyMap())
    val contactsMap = _contactsMap.asStateFlow()

    // Key(Offer.OfferId), Value(ContactId)
    private val _offerMap = MutableStateFlow<Map<ByteVector32, UUID>>(emptyMap())
    val offerMap = _offerMap.asStateFlow()

    // Key(Offer.contactNodeId), Value(ContactId)
    private val _publicKeyMap = MutableStateFlow<Map<PublicKey, UUID>>(emptyMap())
    val publicKeyMap = _publicKeyMap.asStateFlow()

    // Key(lightningAddress.hash), Value(ContactId)
    private val _addressMap = MutableStateFlow<Map<ByteVector32, UUID>>(emptyMap())
    val addressMap = _addressMap.asStateFlow()

    init {
        launch {
            appDb.monitorContactsFlow().collect { list ->
                val newMap = list.associateBy { it.id }
                val newOfferMap = list.flatMap { contact ->
                    contact.offers.map { row ->
                        row.id to contact.id
                    }
                }.toMap()
                val newPublicKeyMap = list.flatMap { contact ->
                    contact.publicKeys.map { pubKey ->
                        pubKey to contact.id
                    }
                }.toMap()
                val newAddressMap = list.flatMap { contact ->
                    contact.addresses.map { row ->
                        row.id to contact.id
                    }
                }.toMap()
                _contactsList.value = list
                _contactsMap.value = newMap
                _offerMap.value = newOfferMap
                _publicKeyMap.value = newPublicKeyMap
                _addressMap.value = newAddressMap
            }
        }
    }

    /**
     * This method will:
     * - insert or update the contact in the database (depending on whether it already exists)
     * - insert any new offers
     * - update any offers that have been changed (i.e. label changed)
     * - delete offers that have been removed from the list
     * - insert any new addresses
     * - update any addresses that have been changed (i.e. label changed)
     * - delete any addresses that have been removed
     *
     * In other words, the UI doesn't have to track which changes have been made.
     * It can simply call this method, and the database will be properly updated.
     */
    suspend fun saveContact(contact: ContactInfo) {
        appDb.saveContact(contact)
    }

    suspend fun deleteContact(contactId: UUID) {
        appDb.deleteContact(contactId)
    }

    /**
     * In most cases there's no need to query the database since we have everything in memory.
     */

    fun contactForId(contactId: UUID): ContactInfo? {
        return contactsMap.value[contactId]
    }

    fun contactIdForOfferId(offerId: ByteVector32): UUID? {
        return offerMap.value[offerId]
    }

    fun contactForOfferId(offerId: ByteVector32): ContactInfo? {
        return contactIdForOfferId(offerId)?.let { contactId ->
            contactForId(contactId)
        }
    }

    fun contactIdForOffer(offer: OfferTypes.Offer): UUID? {
        return contactIdForOfferId(offer.offerId)
    }

    fun contactForOffer(offer: OfferTypes.Offer): ContactInfo? {
        return contactForOfferId(offer.offerId)
    }

    fun contactIdForPayerPubKey(payerPubKey: PublicKey): UUID? {
        return publicKeyMap.value[payerPubKey]
    }

    fun contactForPayerPubKey(payerPubKey: PublicKey): ContactInfo? {
        return contactIdForPayerPubKey(payerPubKey)?.let { contactId ->
            contactForId(contactId)
        }
    }

    fun contactIdForLightningAddress(address: String): UUID? {
        return addressMap.value[ContactAddress.hash(address)]
    }

    fun contactForLightningAddress(address: String): ContactInfo? {
        return contactIdForLightningAddress(address)?.let { contactId ->
            contactForId(contactId)
        }
    }

    fun contactIdForPaymentInfo(paymentInfo: WalletPaymentInfo): UUID? {
        return if (paymentInfo.payment is IncomingPayment) {
            paymentInfo.payment.incomingOfferMetadata()?.let { offerMetadata ->
                contactIdForPayerPubKey(offerMetadata.payerKey)
            }
        } else {
            paymentInfo.metadata.lightningAddress?.let { address ->
                contactIdForLightningAddress(address)
            } ?: paymentInfo.payment.outgoingInvoiceRequest()?.let { invoiceRequest ->
                contactIdForOfferId(invoiceRequest.offer.offerId)
            }
        }
    }

    fun contactForPaymentInfo(paymentInfo: WalletPaymentInfo): ContactInfo? {
        return contactIdForPaymentInfo(paymentInfo)?.let { contactId ->
            contactForId(contactId)
        }
    }
}