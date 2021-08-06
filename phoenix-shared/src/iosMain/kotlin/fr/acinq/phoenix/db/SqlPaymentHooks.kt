package fr.acinq.phoenix.db

import fr.acinq.phoenix.db.payments.CloudKitInterface

actual fun didCompletePaymentRow(id: PaymentRowId, database: PaymentsDatabase) {}

actual fun makeCloudKitDb(database: PaymentsDatabase): CloudKitInterface? {
    return null
}