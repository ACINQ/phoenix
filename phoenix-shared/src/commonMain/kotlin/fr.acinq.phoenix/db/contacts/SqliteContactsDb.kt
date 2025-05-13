package fr.acinq.phoenix.db.contacts

import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.db.Bolt12IncomingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.WalletPaymentMetadata
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.migrations.appDb.v7.AfterVersion7Result
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata
import fr.acinq.phoenix.utils.extensions.outgoingInvoiceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds


class SqliteContactsDb(
    val driver: SqlDriver,
    val database: PaymentsDatabase,
    val loggerFactory: LoggerFactory
): CoroutineScope by MainScope() {

    private val log = loggerFactory.newLogger(this::class)

    val contactQueries = ContactQueries(database)

    private val _contactsList = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contactsList = _contactsList.asStateFlow()

    data class ContactIndexes(
        val contactsMap: Map<UUID, ContactInfo>,
        val offersMap: Map<ByteVector32, UUID>,
        val publicKeysMap: Map<PublicKey, UUID>,
        val addressesMap: Map<ByteVector32, UUID>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val indexesFlow = contactsList.mapLatest { list ->
        ContactIndexes(
            contactsMap = list.associateBy { it.id },
            offersMap = list.flatMap { contact ->
                contact.offers.map { it.id to contact.id }
            }.toMap(),
            publicKeysMap = list.flatMap { contact ->
                contact.publicKeys.map { it to contact.id }
            }.toMap(),
            addressesMap = list.flatMap { contact ->
                contact.addresses.map { it.id to contact.id }
            }.toMap()
        )
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = ContactIndexes(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    )

    init {
        launch {
            contactQueries.monitorContactsFlow(Dispatchers.Default).collect { list ->
                _contactsList.value = list
            }
        }
    }

    suspend fun saveContact(contact: ContactInfo) = withContext(Dispatchers.Default) {
        contactQueries.saveContact(contact)
    }

    suspend fun deleteContact(contactId: UUID) = withContext(Dispatchers.Default) {
        contactQueries.deleteContact(contactId)
    }

    /**
     * There's generally no need to query the database since we have everything in memory.
     */

    fun contactForPayment(payment: WalletPayment, metadata: WalletPaymentMetadata?): ContactInfo? {
        return contactIdForPayment(payment, metadata)?.let { contactId ->
            contactForId(contactId)
        }
    }

    fun contactForOffer(offer: OfferTypes.Offer): ContactInfo? {
        return contactForOfferId(offer.offerId)
    }

    fun contactForPayerPubKey(payerPubKey: PublicKey): ContactInfo? {
        return contactIdForPayerPubKey(payerPubKey)?.let { contactId ->
            contactForId(contactId)
        }
    }

    fun contactForLightningAddress(address: String): ContactInfo? {
        return contactIdForLightningAddress(address)?.let { contactId ->
            contactForId(contactId)
        }
    }

    private fun contactForId(contactId: UUID): ContactInfo? {
        return indexesFlow.value.contactsMap[contactId]
    }

    private fun contactForOfferId(offerId: ByteVector32): ContactInfo? {
        return contactIdForOfferId(offerId)?.let { contactId ->
            contactForId(contactId)
        }
    }

    private fun contactIdForOfferId(offerId: ByteVector32): UUID? {
        return indexesFlow.value.offersMap[offerId]
    }

    private fun contactIdForPayerPubKey(payerPubKey: PublicKey): UUID? {
        return indexesFlow.value.publicKeysMap[payerPubKey]
    }

    private fun contactIdForLightningAddress(address: String): UUID? {
        return indexesFlow.value.addressesMap[ContactAddress.hash(address)]
    }

    private fun contactIdForPayment(payment: WalletPayment, metadata: WalletPaymentMetadata?): UUID? {
        return if (payment is Bolt12IncomingPayment) {
            payment.incomingOfferMetadata()?.let { offerMetadata ->
                contactIdForPayerPubKey(offerMetadata.payerKey)
            }
        } else {
            metadata?.lightningAddress?.let { address ->
                contactIdForLightningAddress(address)
            } ?: payment.outgoingInvoiceRequest()?.let { invoiceRequest ->
                contactIdForOfferId(invoiceRequest.offer.offerId)
            }
        }
    }

    /**
     * Run this to migrate the contacts from the appDb to the paymentsDb.
     * This function can be run everytime the app is launched.
     */
    internal suspend fun migrateContactsIfNeeded(appDb: SqliteAppDb) = withContext(Dispatchers.Default) {

        val result = fr.acinq.phoenix.db.migrations.appDb.v7.AfterVersion7(
            appDbDriver = appDb.driver,
            paymentsDbDriver = driver,
            loggerFactory = loggerFactory
        )
        if (result == AfterVersion7Result.MigrationNowCompleted) {
            delay(5.seconds)
            // We updated the database directly, which skips the SqlDelight hooks.
            // Which means things like `monitorContactsFlow()` won't get triggered.
            // So we need to manually update the contactsList.
            _contactsList.value = contactQueries.listContacts()
        }
    }
}
