package fr.acinq.phoenix.data

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.db.WalletPayment
import fr.acinq.lightning.utils.UUID

/* Represents a unique WalletPayment row in the database,
 * which exists in either the `incoming_payments` table,
 * or the `outgoing_payments` table.
 *
 * It is common to reference these rows in other database tables via:
 * - type: Long (representing either incoming or outgoing)
 * - id: String (representing the appropriate id for either table)
 *
 * The WalletPaymentId class assists in this conversion.
 */
sealed class WalletPaymentId {

    data class IncomingPaymentId(val paymentHash: ByteVector32): WalletPaymentId() {
        companion object {
            fun fromString(id: String) =
                IncomingPaymentId(paymentHash = ByteVector32(id))

            fun fromByteArray(id: ByteArray) =
                IncomingPaymentId(paymentHash = ByteVector32(id))
        }
    }

    data class OutgoingPaymentId(val id: UUID): WalletPaymentId() {
        companion object {
            fun fromString(id: String) =
                OutgoingPaymentId(id = UUID.fromString(id))
        }
    }

    enum class DbType(val value: Long) {
        INCOMING(1),
        OUTGOING(2)
    }

    /* Maps to database column:
     * - type: INTEGER NOT NULL (mapped to Long via SqlDelight)
     */
    val dbType: DbType get() = when (this) {
        is IncomingPaymentId -> DbType.INCOMING
        is OutgoingPaymentId -> DbType.OUTGOING
    }

    /* Maps to database column:
     * - id: TEXT NOT NULL (mapped to String via SqlDelight)
     */
    val dbId get() = when (this) {
        is IncomingPaymentId -> this.paymentHash.toHex()
        is OutgoingPaymentId -> this.id.toString()
    }

    /* Use `identifier` if the code requires a single (hashable) identifier for the row.
     * For example, for use within a hashmap or Cache.
     */
    val identifier: String get() = when(this) {
        is OutgoingPaymentId -> "outgoing|${this.id}"
        is IncomingPaymentId -> "incoming|${this.paymentHash.toHex()}"
    }

    companion object {

        fun create(type: Long, id: String): WalletPaymentId? {
            return when(type) {
                DbType.INCOMING.value -> {
                    IncomingPaymentId.fromString(id)
                }
                DbType.OUTGOING.value -> {
                    OutgoingPaymentId.fromString(id)
                }
                else -> null
            }
        }
    }
}

fun WalletPayment.walletPaymentId(): WalletPaymentId = when (this) {
    is IncomingPayment -> WalletPaymentId.IncomingPaymentId(paymentHash = this.paymentHash)
    is OutgoingPayment -> WalletPaymentId.OutgoingPaymentId(id = this.id)
}

/**
 * Represents a payment & its associated metadata.
 */
data class WalletPaymentInfo(
    val payment: WalletPayment,
    val metadata: WalletPaymentMetadata
) {
    fun id() = payment.walletPaymentId()
}

/**
 * Represents information from the `payments_metadata` table.
 */
data class WalletPaymentMetadata(
    val userDescription: String? = null,
    val lnurl: LnurlPayMetadata? = null,
)

data class LnurlPayMetadata(
    val pay: LNUrl.Pay,
    val description: String,
    val successAction: LNUrl.PayInvoice.SuccessAction?
) {
    companion object {/* allow companion extensions */}
}

/**
 * Represents options when fetching data from the `payments_metadata` table.
 *
 * Since there may be a lot of data in this table, options allow queries to be optimized.
 * For example, when fetching general payment information (for the list of payments
 * on the Home screen), we may only care about the payment description.
 */
data class WalletPaymentFetchOptions(val flags: Int) { // <- bitmask

    /* The `+` operator is implemented, so it can be used like so:
     * `val options = WalletPaymentFetchOptions.Descriptions + WalletPaymentFetchOptions.UserNotes`
     */
    operator fun plus(other: WalletPaymentFetchOptions): WalletPaymentFetchOptions {
        return WalletPaymentFetchOptions(this.flags or other.flags)
    }

    fun contains(options: WalletPaymentFetchOptions): Boolean {
        return (this.flags and options.flags) != 0
    }

    companion object {
        val None = WalletPaymentFetchOptions(0)
        val Descriptions = WalletPaymentFetchOptions(1 shl 0)
        val Lnurl = WalletPaymentFetchOptions(1 shl 1)
        val UserNotes = WalletPaymentFetchOptions(1 shl 2)

        val All = Descriptions + Lnurl + UserNotes
    }
}