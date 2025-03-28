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
import fr.acinq.bitcoin.io.ByteArrayInput
import fr.acinq.bitcoin.io.ByteArrayOutput
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.serialization.InputExtensions.readBoolean
import fr.acinq.lightning.serialization.OutputExtensions.writeBoolean
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.contacts.ContactQueries
import fr.acinq.phoenix.db.contacts.OldContactQueries
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata
import fr.acinq.phoenix.utils.extensions.outgoingInvoiceRequest
import kotlin.collections.List
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsManager(
    private val loggerFactory: LoggerFactory,
    private val appDb: SqliteAppDb
) : CoroutineScope by MainScope() {

    constructor(business: PhoenixBusiness) : this(
        loggerFactory = business.loggerFactory,
        appDb = business.appDb,
    ) {
        launch {
            databaseManagerFlow.value = business.databaseManager
        }
    }

    private val log = loggerFactory.newLogger(this::class)

    /**
     * DatabaseManager retains a reference to ContactsManager.
     * So if ContactsManager also retains a reference to the DatabaseManager,
     * then we end up crashing with a StackOverflow during init.
     * One way to solve that would be a WeakReference, but it's not supported in KMP.
     * So we use another workaround.
     */
    private val databaseManagerFlow = MutableStateFlow<DatabaseManager?>(null)
    private suspend fun paymentsDb() = databaseManagerFlow.filterNotNull().first().paymentsDb()

    private val _contactsList = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contactsList = _contactsList.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val contactsMap = _contactsList.mapLatest { list ->
        list.associateBy { it.id }
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = mapOf()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val offerMap = _contactsList.mapLatest { list ->
        list.flatMap { contact ->
            contact.offers.map { it.id to contact.id }
        }.toMap()
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = mapOf()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val publicKeyMap = _contactsList.mapLatest { list ->
        list.flatMap { contact ->
            contact.publicKeys.map { it to contact.id }
        }.toMap()
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = mapOf()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val addressMap = _contactsList.mapLatest { list ->
        list.flatMap { contact ->
            contact.addresses.map { it.id to contact.id }
        }.toMap()
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = mapOf()
    )

    init {
        launch {
            paymentsDb().monitorContactsFlow().collect { list ->
                _contactsList.value = list
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
        paymentsDb().saveContact(contact)
    }

    suspend fun deleteContact(contactId: UUID) {
        paymentsDb().deleteContact(contactId)
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

    /**
     * Run this to migrate the contacts from the appDb to the paymentsDb.
     * This function can be run everytime the app is launched.
     */
    suspend fun migrateContactsIfNeeded() {

        val KEY_MIGRATION_DONE = "contacts_migration"

        log.debug { "Checking KEY_MIGRATION_DONE ..." }
        val migrationDone = appDb.getValue(KEY_MIGRATION_DONE) { Boolean.fromByteArray(it) }?.first ?: false
        if (migrationDone) {
            log.debug { "Migration already complete" }
            return
        }

        log.debug { "Starting migration..." }

        val paymentsDb = paymentsDb()

        val oldQueries = OldContactQueries(appDb.database)
        val newQueries = ContactQueries(paymentsDb.database)

        withContext(Dispatchers.Default) {
            while (true) {
                log.debug { "Fetching batch..." }
                val batch = oldQueries.fetchContactsBatch(limit = 10)
                log.debug { "Migrating batch of ${batch.size}..." }

                if (batch.isEmpty()) {
                    break
                }

                paymentsDb.database.transaction {
                    batch.forEach { contact ->
                        if (!newQueries.existsContact(contact.id)) {
                            newQueries.saveContact(contact, notify = false)
                        }
                    }
                }

                log.debug { "Deleting batch of ${batch.size}..." }
                oldQueries.deleteContacts(batch)
            }
        }

        log.debug { "Migration now complete" }
        appDb.setValue(true.toByteArray(), KEY_MIGRATION_DONE)
    }
}

fun Boolean.toByteArray(): ByteArray {
    val out = ByteArrayOutput()
    out.writeBoolean(this)
    return out.toByteArray()
}

fun Boolean.Companion.fromByteArray(bin: ByteArray): Boolean? {
    val input = ByteArrayInput(bin)
    return try {
        input.readBoolean()
    } catch (e: Exception) { null }
}