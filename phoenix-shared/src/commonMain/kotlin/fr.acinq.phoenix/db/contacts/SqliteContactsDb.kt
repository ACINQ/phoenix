package fr.acinq.phoenix.db.contacts

import app.cash.sqldelight.db.SqlDriver
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
import fr.acinq.phoenix.data.ContactAddress
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.db.SqliteAppDb
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import fr.acinq.phoenix.utils.extensions.incomingOfferMetadata
import fr.acinq.phoenix.utils.extensions.outgoingInvoiceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SqliteContactsDb(
    val driver: SqlDriver,
    val database: PaymentsDatabase,
    val loggerFactory: LoggerFactory
): CoroutineScope by MainScope() {

    private val log = loggerFactory.newLogger(this::class)

    val contactQueries = ContactQueries(database)

    private val _contactsList = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contactsList = _contactsList.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val contactsMap = _contactsList.mapLatest { list ->
        list.associateBy { it.id }
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = mapOf()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val offerMap = _contactsList.mapLatest { list ->
        list.flatMap { contact ->
            contact.offers.map { it.id to contact.id }
        }.toMap()
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = mapOf()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val publicKeyMap = _contactsList.mapLatest { list ->
        list.flatMap { contact ->
            contact.publicKeys.map { it to contact.id }
        }.toMap()
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = mapOf()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val addressMap = _contactsList.mapLatest { list ->
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
    internal suspend fun migrateContactsIfNeeded(appDb: SqliteAppDb) {

        val KEY_MIGRATION_DONE = "contacts_migration"

        log.debug { "Checking KEY_MIGRATION_DONE ..." }
        val migrationDone = appDb.getValue(KEY_MIGRATION_DONE) { Boolean.fromByteArray(it) }?.first ?: false
        if (migrationDone) {
            log.debug { "Migration already complete" }
            return
        }

        log.debug { "Starting migration..." }

        withContext(Dispatchers.Default) {
            fr.acinq.phoenix.db.migrations.appDb.v7.AfterVersion7(
                appDbDriver = appDb.driver,
                paymentsDbDriver = driver,
                loggerFactory = loggerFactory
            )
        }

        log.debug { "Migration now complete" }
        appDb.setValue(true.toByteArray(), KEY_MIGRATION_DONE)

        // We updated the database directly, which skips the SqlDelight hooks.
        // Which means things like `monitorContactsFlow()` won't get triggered.
        // So we need to manually update the contactsList.
        launch {
            _contactsList.value = withContext(Dispatchers.Default) {
                contactQueries.listContacts()
            }
        }
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