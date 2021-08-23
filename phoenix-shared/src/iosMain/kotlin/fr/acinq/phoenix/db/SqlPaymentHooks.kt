package fr.acinq.phoenix.db

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.db.payments.CloudKitInterface
import fracinqphoenixdb.Cloudkit_payments_queue

fun Cloudkit_payments_queue.asPaymentRowId(): PaymentRowId? = when (type) {
    CloudKitRowType.INCOMING_PAYMENT.value -> {
        PaymentRowId.IncomingPaymentId(paymentHash = ByteVector32(id))
    }
    CloudKitRowType.OUTGOING_PAYMENT.value -> {
        PaymentRowId.OutgoingPaymentId(id = UUID.fromString(id))
    }
    else -> null
}

actual fun didCompletePaymentRow(id: PaymentRowId, database: PaymentsDatabase) {
    val now = currentTimestampMillis()
    val ckq = database.cloudKitPaymentsQueries
    ckq.addToQueue(type = id.db_type.value, id = id.db_id, date_added = now)
}

actual fun makeCloudKitDb(database: PaymentsDatabase): CloudKitInterface? {
    return CloudKitDb(database)
}