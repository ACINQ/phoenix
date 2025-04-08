package fr.acinq.phoenix.db.contacts

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.db.didDeleteContact
import fr.acinq.phoenix.db.didSaveContact
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ContactQueries(val database: PaymentsDatabase) {

    val queries = database.contactsQueries

    fun saveContact(contact: ContactInfo, notify: Boolean = true) {
        database.transaction {
            val contactExists = queries.existsContact(
                id = contact.id
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
                id = contact.id,
                data = contact,
                createdAt = currentTimestampMillis(),
                updatedAt = null
            )
        }
    }

    private fun updateExistingContact(contact: ContactInfo) {
        database.transaction {
            queries.updateContact(
                data = contact,
                updatedAt = currentTimestampMillis(),
                contactId = contact.id
            )
        }
    }

    fun existsContact(contactId: UUID): Boolean {
        return database.transactionWithResult {
            queries.existsContact(
                id = contactId
            ).executeAsOne() > 0
        }
    }

    fun getContact(contactId: UUID): ContactInfo? {
        return database.transactionWithResult {
            queries.getContact(contactId).executeAsOneOrNull()
        }
    }

    fun listContacts(): List<ContactInfo> {
        return database.transactionWithResult {
            queries.listContacts().executeAsList()
        }
    }

    fun monitorContactsFlow(context: CoroutineContext): Flow<List<ContactInfo>> {
        return queries.listContacts().asFlow().mapToList(context)
    }

    fun deleteContact(contactId: UUID) {
        database.transaction {
            queries.deleteContact(contactId)
            didDeleteContact(contactId, database)
        }
    }
}