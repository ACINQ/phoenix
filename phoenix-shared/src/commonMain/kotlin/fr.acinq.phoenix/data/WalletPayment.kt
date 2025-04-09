package fr.acinq.phoenix.data

import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.data.lnurl.LnurlPay

/**
 * Represents a payment & its associated metadata.
 */
data class WalletPaymentInfo(
    val payment: WalletPayment,
    val metadata: WalletPaymentMetadata,
    val contact: ContactInfo?
) {
    val id get() = payment.id
}

/**
 * Represents information from the `payments_metadata` table.
 */
data class WalletPaymentMetadata(
    val lnurl: LnurlPayMetadata? = null,
    val originalFiat: ExchangeRate.BitcoinPriceRate? = null,
    val userDescription: String? = null,
    val userNotes: String? = null,
    val lightningAddress: String? = null,
    val modifiedAt: Long? = null
)

data class LnurlPayMetadata(
    val pay: LnurlPay.Intent,
    val description: String,
    val successAction: LnurlPay.Invoice.SuccessAction?
) {
    companion object { /* allow companion extensions */ }
}
