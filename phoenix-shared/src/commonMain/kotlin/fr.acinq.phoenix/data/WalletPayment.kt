package fr.acinq.phoenix.data

import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.db.WalletPaymentOrderRow

/**
 * Represents a payment & its associated metadata.
 *
 * Note that the metadata may have only partial values if the fetchOptions is
 * anything other than [WalletPaymentFetchOptions.All] or [WalletPaymentFetchOptions.Metadata].
 *
 * For example, if we fetch from the database using [WalletPaymentFetchOptions.Descriptions],
 * then the metadata will only have its description values filled in.
 */
data class WalletPaymentInfo(
    val payment: WalletPayment,
    val metadata: WalletPaymentMetadata,
    val contact: ContactInfo?,
    val fetchOptions: WalletPaymentFetchOptions
) {
    val id get() = payment.id

    /**
     * Converts the info to a `WalletPaymentOrderRow`, if possible.
     * This may be useful if you want to use the PaymentsFetcher
     * to take advantage of the in-memory cache.
     */
    fun toOrderRow(): WalletPaymentOrderRow {
        return WalletPaymentOrderRow(
            id = payment.id,
            createdAt = payment.createdAt,
            completedAt = payment.completedAt,
            metadataModifiedAt = metadata.modifiedAt
        )
    }
}

/**
 * Represents information from the `payments_metadata` table.
 */
data class WalletPaymentMetadata(
    val lnurl: LnurlPayMetadata? = null,
    val originalFiat: ExchangeRate.BitcoinPriceRate? = null,
    val userDescription: String? = null,
    val userNotes: String? = null,
    val modifiedAt: Long? = null
)

data class LnurlPayMetadata(
    val pay: LnurlPay.Intent,
    val description: String,
    val successAction: LnurlPay.Invoice.SuccessAction?
) {
    companion object { /* allow companion extensions */ }
}

/**
 * TODO : This should be removed altogether. Retrieving payments data should now be fast enough that we always retrieve everything.
 *
 *
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

    /* The `-` operator is implemented, so it can be used like so:
     * `val options = WalletPaymentFetchOptions.All - WalletPaymentFetchOptions.UserNotes`
     */
    operator fun minus(other: WalletPaymentFetchOptions): WalletPaymentFetchOptions {
        return WalletPaymentFetchOptions(this.flags and other.flags.inv())
    }

    fun contains(options: WalletPaymentFetchOptions): Boolean {
        return (this.flags and options.flags) != 0
    }

    companion object {
        val None = WalletPaymentFetchOptions(0)
        val Descriptions = WalletPaymentFetchOptions(1 shl 0)
        val Lnurl = WalletPaymentFetchOptions(1 shl 1)
        val UserNotes = WalletPaymentFetchOptions(1 shl 2)
        val OriginalFiat = WalletPaymentFetchOptions(1 shl 3)
        val Contact = WalletPaymentFetchOptions(1 shl 4)

        val Metadata = Descriptions + Lnurl + UserNotes + OriginalFiat
        val All = Descriptions + Lnurl + UserNotes + OriginalFiat + Contact
    }
}