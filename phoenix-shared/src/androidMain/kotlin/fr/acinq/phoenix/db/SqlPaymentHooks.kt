package fr.acinq.phoenix.db

import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.payments.CloudKitInterface

actual fun didCompleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase) {}

actual fun makeCloudKitDb(database: PaymentsDatabase): CloudKitInterface? {
    return null
}