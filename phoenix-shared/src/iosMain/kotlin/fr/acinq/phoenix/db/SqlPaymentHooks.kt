package fr.acinq.phoenix.db

import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.WalletPaymentId
import fr.acinq.phoenix.db.payments.CloudKitInterface
import fracinqphoenixdb.Cloudkit_payments_queue


actual fun didCompleteWalletPayment(id: WalletPaymentId, database: PaymentsDatabase) {
    val now = currentTimestampMillis()
    val ckq = database.cloudKitPaymentsQueries
    ckq.addToQueue(type = id.dbType.value, id = id.dbId, date_added = now)
}

actual fun makeCloudKitDb(database: PaymentsDatabase): CloudKitInterface? {
    return CloudKitDb(database)
}