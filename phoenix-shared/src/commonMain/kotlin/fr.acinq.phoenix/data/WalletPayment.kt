package fr.acinq.phoenix.data

import fr.acinq.lightning.db.WalletPayment
import fr.acinq.phoenix.data.lnurl.LnurlPay
import fr.acinq.phoenix.managers.AppConfigurationManager
import fr.acinq.phoenix.managers.global.CurrencyManager

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
) {
    /**
     * Returns this object if the fiat rate is already set, or updates its fiat rate to the current primary rate provided by the currency manager.
     * Ensures as much as possible that the payment we store has a fiat exchange rate attached to it.
     */
    fun withFiatRate(appConfigurationManager: AppConfigurationManager, currencyManager: CurrencyManager): WalletPaymentMetadata {
        return if (this.originalFiat == null) {
            val currentFiatRate = appConfigurationManager.preferredFiatCurrencies.value?.let { currencyManager.calculateOriginalFiat(it.primary) }
            this.copy(originalFiat = currentFiatRate)
        } else this
    }
}

data class LnurlPayMetadata(
    val pay: LnurlPay.Intent,
    val description: String,
    val successAction: LnurlPay.Invoice.SuccessAction?
) {
    companion object { /* allow companion extensions */ }
}
