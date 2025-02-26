package fr.acinq.phoenix.db

import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.payments.CloudKitInterface
import fr.acinq.phoenix.db.sqldelight.AppDatabase
import fr.acinq.phoenix.db.sqldelight.PaymentsDatabase

actual fun didSaveWalletPayment(id: UUID, database: PaymentsDatabase) {}
actual fun didDeleteWalletPayment(id: UUID, database: PaymentsDatabase) {}
actual fun didUpdateWalletPaymentMetadata(id: UUID, database: PaymentsDatabase) {}

actual fun didSaveContact(contactId: UUID, database: AppDatabase) {}
actual fun didDeleteContact(contactId: UUID, database: AppDatabase) {}

actual fun makeCloudKitDb(appDb: SqliteAppDb, paymentsDb: SqlitePaymentsDb): CloudKitInterface? {
    return null
}