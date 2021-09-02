package fr.acinq.phoenix.db

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.db.cloud.CloudData

/* Both `cloudkit_payments_metadata` & `cloudkit_payments_queue` have a column:
 * - type INTEGER NOT NULL
 *
 * The column refers to the type of payment (incoming vs outgoing),
 * a this enum defines the mapping.
 */
enum class CloudKitRowType(val value: Long) {
    INCOMING_PAYMENT(1),
    OUTGOING_PAYMENT(2)
}

fun PaymentRowId.Companion.create(type: Long, id: String): PaymentRowId? {
    return when(type) {
        CloudKitRowType.INCOMING_PAYMENT.value -> {
            PaymentRowId.IncomingPaymentId.Companion.fromString(id)
        }
        CloudKitRowType.OUTGOING_PAYMENT.value -> {
            PaymentRowId.OutgoingPaymentId.Companion.fromString(id)
        }
        else -> null
    }
}

fun PaymentRowId.IncomingPaymentId.Companion.fromString(id: String) =
    PaymentRowId.IncomingPaymentId(paymentHash = ByteVector32(id))

fun PaymentRowId.IncomingPaymentId.Companion.fromByteArray(id: ByteArray) =
    PaymentRowId.IncomingPaymentId(paymentHash = ByteVector32(id))

fun PaymentRowId.OutgoingPaymentId.Companion.fromString(id: String) =
    PaymentRowId.OutgoingPaymentId(id = UUID.fromString(id))

/* Maps from PaymentRowId to CloudKitRowType.
 * Use paymentRowId.db_type.value to get the raw number.
 */
val PaymentRowId.db_type: CloudKitRowType get() = when (this) {
    is PaymentRowId.IncomingPaymentId -> CloudKitRowType.INCOMING_PAYMENT
    is PaymentRowId.OutgoingPaymentId -> CloudKitRowType.OUTGOING_PAYMENT
}

/* Both `cloudkit_payments_metadata` & `cloudkit_payments_queue` have a column:
 * - id TEXT NOT NULL
 *
 * Use this method to map to the proper database string.
 */
val PaymentRowId.db_id get() = when (this) {
    is PaymentRowId.IncomingPaymentId -> this.paymentHash.toHex()
    is PaymentRowId.OutgoingPaymentId -> this.id.toString()
}

/**
 * Wrapper for a payment (either Incoming or Outgoing).
 */
sealed class PaymentRow {
    data class Incoming(val row: IncomingPayment) : PaymentRow()
    data class Outgoing(val row: OutgoingPayment) : PaymentRow()

    fun paymentRowId(): PaymentRowId = when (this) {
        is Incoming -> PaymentRowId.IncomingPaymentId(paymentHash = row.paymentHash)
        is Outgoing -> PaymentRowId.OutgoingPaymentId(id = row.id)
    }
}

fun CloudData.paymentRow(): PaymentRow? = when {
    incoming != null -> PaymentRow.Incoming(row = incoming.unwrap())
    outgoing != null -> PaymentRow.Outgoing(row = outgoing.unwrap())
    else -> null
}
