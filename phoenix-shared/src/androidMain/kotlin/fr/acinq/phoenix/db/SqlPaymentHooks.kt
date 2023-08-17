package fr.acinq.phoenix.db

import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.payments.CloudKitInterface

actual fun didSaveWalletPayment(id: WalletPaymentId, database: PaymentsDatabase) {}
actual fun didDeleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase) {}
actual fun didUpdateWalletPaymentMetadata(id: WalletPaymentId, database: PaymentsDatabase) {}

actual fun makeCloudKitDb(database: PaymentsDatabase): CloudKitInterface? {
    return null
}