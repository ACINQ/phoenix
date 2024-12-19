package fr.acinq.phoenix.db

import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.db.payments.CloudKitInterface


actual fun didSaveWalletPayment(id: UUID, database: PaymentsDatabase) {
    database.cloudKitPaymentsQueries.addToQueue(id = id, date_added = currentTimestampMillis())
}

actual fun didDeleteWalletPayment(id: UUID, database: PaymentsDatabase) {
    database.cloudKitPaymentsQueries.addToQueue(id = id, date_added = currentTimestampMillis())
}

actual fun didUpdateWalletPaymentMetadata(id: UUID, database: PaymentsDatabase) {
    database.cloudKitPaymentsQueries.addToQueue(id = id, date_added = currentTimestampMillis())
}

actual fun didSaveContact(contactId: UUID, database: AppDatabase) {
    database.cloudKitContactsQueries.addToQueue(
        id = contactId.toString(),
        date_added = currentTimestampMillis()
    )
}

actual fun didDeleteContact(contactId: UUID, database: AppDatabase) {
    database.cloudKitContactsQueries.addToQueue(
        id = contactId.toString(),
        date_added = currentTimestampMillis()
    )
}

actual fun makeCloudKitDb(appDb: SqliteAppDb, paymentsDb: SqlitePaymentsDb): CloudKitInterface? {
    return CloudKitDb(appDb, paymentsDb)
}