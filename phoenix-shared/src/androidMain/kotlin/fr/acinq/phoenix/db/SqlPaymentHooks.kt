package fr.acinq.phoenix.db

import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.payments.CloudKitInterface

actual fun didSaveWalletPayment(id: WalletPaymentId, database: PaymentsDatabase) {}
actual fun didDeleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase) {}
actual fun didUpdateWalletPaymentMetadata(id: WalletPaymentId, database: PaymentsDatabase) {}

actual fun didSaveContact(contactId: UUID, database: AppDatabase) {}
actual fun didDeleteContact(contactId: UUID, database: AppDatabase) {}

actual fun makeCloudKitDb(appDb: SqliteAppDb, paymentsDb: SqlitePaymentsDb): CloudKitInterface? {
    return null
}