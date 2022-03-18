package fr.acinq.phoenix.db

import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.payments.CloudKitInterface
import fracinqphoenixdb.Cloudkit_payments_queue


actual fun didCompleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase) {
    database.cloudKitPaymentsQueries.addToQueue(
        type = id.dbType.value,
        id = id.dbId,
        date_added = currentTimestampMillis()
    )
}

actual fun didDeleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase) {
    database.cloudKitPaymentsQueries.addToQueue(
        type = id.dbType.value,
        id = id.dbId,
        date_added = currentTimestampMillis()
    )
}

actual fun didUpdateWalletPaymentMetadata(id: WalletPaymentId, database: PaymentsDatabase) {
    database.cloudKitPaymentsQueries.addToQueue(
        type = id.dbType.value,
        id = id.dbId,
        date_added = currentTimestampMillis()
    )
}

actual fun makeCloudKitDb(database: PaymentsDatabase): CloudKitInterface? {
    return CloudKitDb(database)
}