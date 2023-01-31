package fr.acinq.phoenix.utils

import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.extensions.createdAt
import kotlinx.datetime.Instant

class CsvWriter {
    
    data class Configuration(
        val includesFiat: Boolean,
        val includesDescription: Boolean,
        val includesNotes: Boolean
    )

    companion object {
        private const val FIELD_DATE        = "Date"
        private const val FIELD_AMOUNT_MSAT = "Amount Millisatoshi"
        private const val FIELD_AMOUNT_FIAT = "Amount Fiat"
        private const val FIELD_FEES_MSAT   = "Fees Millisatoshi"
        private const val FIELD_FEES_FIAT   = "Fees Fiat"
        private const val FIELD_DESCRIPTION = "Description"
        private const val FIELD_NOTES       = "Notes"

        /**
         * Creates and returns the header row for the CSV file.
         * This includes the CRLF that terminates the row.
         */
        fun makeHeaderRow(config: Configuration): String {
            var header = "$FIELD_DATE,$FIELD_AMOUNT_MSAT,$FIELD_FEES_MSAT"
            if (config.includesFiat) {
                header += ",$FIELD_AMOUNT_FIAT,$FIELD_FEES_FIAT"
            }
            if (config.includesDescription) {
                header += ",$FIELD_DESCRIPTION"
            }
            if (config.includesNotes) {
                header += ",$FIELD_NOTES"
            }

            header += "\r\n"
            return header
        }

        /**
         * Creates and returns the row for the given payment.
         * This includes the CRLF that terminates the row.
         *
         * @param info Payment fetched via PaymentsFetcher
         * @param localizedDescription As displayed in app (might be a localized default value)
         * @param config The configuration for the CSV file
         */
        fun makeRow(
            info: WalletPaymentInfo,
            localizedDescription: String,
            config: Configuration
        ): String {

            var row = ""

            val completedAt = info.payment.completedAt()
            val date = if (completedAt > 0) completedAt else info.payment.createdAt
            val dateStr = Instant.fromEpochMilliseconds(date).toString() // ISO-8601 format
            row += ",${processField(dateStr)}"

            val amtMsat = info.payment.amount.msat
            val feesMsat = info.payment.fees.msat
            val isOutgoing = info.payment is OutgoingPayment

            val amtMsatStr = if (isOutgoing) "-$amtMsat" else "$amtMsat"
            row += ",${processField(amtMsatStr)}"

            val feesMsatStr = "-$feesMsat"
            row += ",${processField(feesMsatStr)}"

            if (config.includesFiat) {
                /**
                 * Developer notes:
                 *
                 * - The fiat amount may not always be in the same currency.
                 *   That is, the user can configure their preferred fiat currency in the app.
                 *   For example:
                 *   * user lives in USA, has currency set to USD
                 *     * payments will record USD/BTC exchange rate at time of payment
                 *     * exported payments will read "X.Y USD"
                 *   * user goes on vacation in Mexico, changes currency to MXN
                 *     * payments will record MXN/BTC exchange rate at time of payment
                 *     * exported payments will read "X.Y MXN"
                 *   * user moves to Spain, changes currency to EUR
                 *     * payments will record EUR/BTC exchange rate at time of payment
                 *     * exported payments will read "X.Y EUR"
                 *
                 * - Prior to February 2023, the exchange rates for fiat currencies other
                 *   than USD & EUR may have been unreliable. So if you're parsing,
                 *   for example COP (Colombian Pesos), and you have an alternative
                 *   source for fetching historical exchange rates, then you may
                 *   prefer that source over the CSV values.
                 */

                info.metadata.originalFiat?.let { exchangeRate ->
                    val msatsPerBitcoin = 100_000_000_000.toDouble()

                    val amtFiat = (amtMsat / msatsPerBitcoin) * exchangeRate.price
                    val feesFiat = (feesMsat / msatsPerBitcoin) * exchangeRate.price

                    val currencyName = exchangeRate.fiatCurrency.name

                    val amtPrefix = if (isOutgoing) "-" else ""
                    val amtFiatStr = "${amtPrefix}${formatFiatValue(amtFiat)} $currencyName"

                    val feesFiatStr = "-${formatFiatValue(feesFiat)} $currencyName"

                    row += ",${processField(amtFiatStr)}"
                    row += ",${processField(feesFiatStr)}"
                } ?: run {
                    row += ",,"
                }
            }

            if (config.includesDescription) {
                row += ",${processField(localizedDescription)}"
            }

            if (config.includesNotes) {
                info.metadata.userNotes?.let {
                    row += ",${processField(it)}"
                } ?: run {
                    row += ","
                }
            }

            row += "\r\n"
            return row
        }

        private fun formatFiatValue(amt: Double): String {

            // How do we display the fiat currency value ?
            // Some currencies use 2 decimal places, e.g. "4.26 EUR".
            // But there are also zero-decimal currencies, such as JPY.
            //
            // Since it's common to have micropayments on the lightning network,
            // we're simply going to always display 4 decimal places.
            //
            // The extra precision can be truncated / rounded / ignored
            // by the reader, who has more insight into how they wish
            // to use the exported information.
            //
            // Note: String.companion.format isn't available until Kotlin v1.7.2

            val components = amt.toString().split(".")
            val comp0 = if (components.size > 0) components[0] else "0"
            val comp1 = if (components.size > 1) components[1] else "0"

            return "${comp0}.${comp1.take(4).padEnd(4, '0')}"
        }

        private fun processField(str: String): String {
            return str.findAnyOf(listOf(",", "\"", "\n"))?.let {
                // - field must be enclosed in double-quotes
                // - a double-quote appearing inside the field must be
                //   escaped by preceding it with another double quote
                "\"${str.replace("\"", "\"\"")}\""
            } ?: str
        }
    }
}