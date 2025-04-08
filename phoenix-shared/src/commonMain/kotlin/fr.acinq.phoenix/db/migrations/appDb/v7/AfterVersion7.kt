package fr.acinq.phoenix.db.migrations.appDb.v7

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.byteVector32
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.logging.debug
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ContactOffer
import fr.acinq.phoenix.db.serialization.contacts.Serialization
import fr.acinq.phoenix.utils.extensions.toByteArray

enum class AfterVersion7Result {
    MigrationWasAlreadyCompleted,
    MigrationNowCompleted
}

fun AfterVersion7(
    appDbDriver: SqlDriver,
    paymentsDbDriver: SqlDriver,
    loggerFactory: LoggerFactory
): AfterVersion7Result {

    data class MetadataRow(
        val id: String,
        val recordCreation: Long,
        val recordBlob: ByteArray
    )

    fun fetchMetadataBatch(): List<MetadataRow> {

        return appDbDriver.executeQuery(
            identifier = null,
            sql = "SELECT * FROM cloudkit_contacts_metadata_old LIMIT 10;",
            mapper = { cursor ->
                val result = buildList {
                    while (cursor.next().value) {
                        val o = MetadataRow(
                            id = cursor.getString(0)!!,
                            recordCreation = cursor.getLong(1)!!,
                            recordBlob = cursor.getBytes(2)!!
                        )
                        add(o)
                    }
                }
                QueryResult.Value(result)
            },
            parameters = 0
        ).value
    }

    fun insertMetadataBatch(list: List<MetadataRow>) {

        val driver: SqlDriver = paymentsDbDriver // avoid typos; always refer to correct driver
        val transacter = object : TransacterImpl(driver) {}
        transacter.transaction {

            list.forEach { metadata ->
                val exists = driver.executeQuery(
                    identifier = null,
                    sql = "SELECT COUNT(*) FROM cloudkit_contacts_metadata WHERE id = ?;",
                    mapper = { cursor ->
                        val count: Long = if (cursor.next().value) {
                            cursor.getLong(0) ?: 0
                        } else {
                            0
                        }
                        QueryResult.Value(count)
                    },
                    parameters = 1,
                    binders = {
                        bindString(0, metadata.id)
                    }
                ).value > 0
                if (!exists) {
                    driver.execute(
                        identifier = null,
                        sql = "INSERT INTO cloudkit_contacts_metadata(id, record_creation, record_blob)\n" +
                                " VALUES (?, ?, ?);",
                        parameters = 4,
                        binders = {
                            bindString(0, metadata.id)
                            bindLong(1, metadata.recordCreation)
                            bindBytes(2, metadata.recordBlob)
                        }
                    )
                }
            }
        }
    }

    fun deleteMetadataBatch(list: List<MetadataRow>) {

        val driver: SqlDriver = appDbDriver // avoid typos; always refer to correct driver
        val transacter = object : TransacterImpl(driver) {}
        transacter.transaction {

            list.forEach { metadata ->
                driver.execute(
                    identifier = null,
                    sql = "DELETE FROM cloudkit_contacts_metadata_old WHERE id = ?;",
                    parameters = 1,
                    binders = {
                        bindString(0, metadata.id)
                    }
                )
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun mapContact(
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

    @Suppress("UNUSED_PARAMETER")
    fun mapOffer(
        offer_id: ByteArray,
        contact_id: String,
        offer: String,
        created_at: Long
    ): ContactOffer? {
        return when (val result = OfferTypes.Offer.decode(offer)) {
            is Try.Success -> ContactOffer(
                id = offer_id.byteVector32(),
                offer = result.get(),
                label = null,
                createdAt = created_at
            )
            is Try.Failure -> null
        }
    }

    fun fetchContactsBatch(): List<ContactInfo> {

        val driver: SqlDriver = appDbDriver // avoid typos; always refer to correct driver
        val transacter = object : TransacterImpl(driver) {}
        return transacter.transactionWithResult {

            val batch = driver.executeQuery(
                identifier = null,
                sql = "SELECT * FROM contacts_old LIMIT 10;",
                parameters = 0,
                mapper = { cursor ->
                    val result = buildList {
                        while (cursor.next().value) {
                            val o = mapContact(
                                id = cursor.getString(0)!!,
                                name = cursor.getString(1)!!,
                                photo_uri = cursor.getString(2),
                                use_offer_key = cursor.getBoolean(3)!!,
                                created_at = cursor.getLong(4)!!,
                                updated_at = cursor.getLong(5)
                            )
                            add(o)
                        }
                    }
                    QueryResult.Value(result)
                }
            ).value

            batch.map { contact ->
                val offers = driver.executeQuery(
                    identifier = null,
                    sql = "SELECT * FROM contact_offers_old WHERE contact_id = ?;",
                    mapper = { cursor ->
                        val result = buildList {
                            while (cursor.next().value) {
                                val o = mapOffer(
                                    offer_id = cursor.getBytes(0)!!,
                                    contact_id = cursor.getString(1)!!,
                                    offer = cursor.getString(2)!!,
                                    created_at = cursor.getLong(3)!!
                                )
                                if (o != null) {
                                    add(o)
                                }
                            }
                        }
                        QueryResult.Value(result)
                    },
                    parameters = 1,
                    binders = {
                        bindString(0, contact.id.toString())
                    }
                ).value
                contact.copy(offers = offers)
            }
        }
    }

    fun insertContactsBatch(contacts: List<ContactInfo>) {

        val driver: SqlDriver = paymentsDbDriver // avoid typos; always refer to correct driver
        val transacter = object : TransacterImpl(driver) {}
        transacter.transaction {

            contacts.forEach { contact ->
                val exists = driver.executeQuery(
                    identifier = null,
                    sql = "SELECT COUNT(*) FROM contacts WHERE id = ?;",
                    mapper = { cursor ->
                       val count: Long = if (cursor.next().value) {
                           cursor.getLong(0) ?: 0
                       } else {
                           0
                       }
                       QueryResult.Value(count)
                    },
                    parameters = 1,
                    binders = {
                        bindBytes(0, contact.id.toByteArray())
                    }
                ).value > 0
                if (!exists) {
                    driver.execute(
                        identifier = null,
                        sql = "INSERT INTO contacts(id, data, created_at, updated_at)\n" +
                              " VALUES (?, ?, ?, ?);",
                        parameters = 4,
                        binders = {
                            bindBytes(0, contact.id.toByteArray())
                            bindBytes(1, Serialization.serialize(contact))
                            bindLong(2, currentTimestampMillis())
                        //  bindNull(3)
                        }
                    )
                }
            }
        }
    }

    fun deleteContactsBatch(contacts: List<ContactInfo>) {

        val driver: SqlDriver = appDbDriver // avoid typos; always refer to correct driver
        val transacter = object : TransacterImpl(driver) {}
        transacter.transaction {

            contacts.forEach { contact ->
                driver.execute(
                    identifier = null,
                    sql = "DELETE FROM contacts_old WHERE id = ?;",
                    parameters = 1,
                    binders = {
                        bindString(0, contact.id.toString())
                    }
                )
            }
        }
    }

    fun existsTables(): Boolean {

        return appDbDriver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='contacts_old';",
            mapper = { cursor ->
                val exists = cursor.next().value
                QueryResult.Value(exists)
            },
            parameters = 0
        ).value
    }

    fun dropTables() {

        val driver: SqlDriver = appDbDriver // avoid typos; always refer to correct driver
        val transacter = object : TransacterImpl(driver) {}
        return transacter.transaction {

            driver.execute(
                identifier = null,
                sql = "DROP TABLE IF EXISTS cloudkit_contacts_metadata_old;",
                parameters = 0
            )
            driver.execute(
                identifier = null,
                sql = "DROP TABLE IF EXISTS contact_offers_old;",
                parameters = 0
            )
            driver.execute(
                identifier = null,
                sql = "DROP TABLE IF EXISTS contacts_old;",
                parameters = 0
            )
        }
    }

    val log = loggerFactory.newLogger("migrations.appDb.AfterVersion7")

    log.debug { "Checking tables..." }
    if (!existsTables()) {
        log.debug { "Tables already dropped. Migration must have previously completed." }
        return AfterVersion7Result.MigrationWasAlreadyCompleted
    }

    while (true) {
        log.debug { "Fetching metadata batch..." }
        val metadataBatch = fetchMetadataBatch()

        if (metadataBatch.isEmpty()) {
            break
        }

        log.debug { "Migrating metadata batch of ${metadataBatch.size}..." }
        insertMetadataBatch(metadataBatch)

        log.debug { "Deleting metadata batch of ${metadataBatch.size}..." }
        deleteMetadataBatch(metadataBatch)
    }

    while (true) {
        log.debug { "Fetching contacts batch..." }
        val contactsBatch = fetchContactsBatch()

        if (contactsBatch.isEmpty()) {
            break
        }

        log.debug { "Migrating contacts batch of ${contactsBatch.size}..." }
        insertContactsBatch(contactsBatch)

        log.debug { "Deleting contacts batch of ${contactsBatch.size}..." }
        deleteContactsBatch(contactsBatch)
    }

    log.debug { "Dropping tables..." }
    dropTables()
    log.debug { "Done" }

    return AfterVersion7Result.MigrationNowCompleted
}