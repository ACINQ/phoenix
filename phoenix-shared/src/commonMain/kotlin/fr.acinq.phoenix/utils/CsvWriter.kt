package fr.acinq.phoenix.utils

import fr.acinq.lightning.db.*
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.phoenix.data.WalletPaymentInfo
import fr.acinq.phoenix.utils.extensions.nodeId
import kotlinx.datetime.Instant

class CsvWriter {

    data class Configuration(
        val includesFiat: Boolean,
        val includesDescription: Boolean,
        val includesNotes: Boolean,
        val includesOriginDestination: Boolean,
    )

    companion object {
        private const val FIELD_DATE = "Date"
        private const val FIELD_AMOUNT_MSAT = "Amount Millisatoshi"
        private const val FIELD_AMOUNT_FIAT = "Amount Fiat"
        private const val FIELD_FEES_MSAT = "Fees Millisatoshi"
        private const val FIELD_CONTEXT = "Context"
        private const val FIELD_FEES_FIAT = "Fees Fiat"
        private const val FIELD_DESCRIPTION = "Description"
        private const val FIELD_NOTES = "Notes"

        /**
         * Creates and returns the header row for the CSV file.
         * This includes the CRLF that terminates the row.
         */
        fun makeHeaderRow(config: Configuration): String {
            var header = "$FIELD_DATE,$FIELD_AMOUNT_MSAT,$FIELD_FEES_MSAT"
            if (config.includesFiat) {
                header += ",$FIELD_AMOUNT_FIAT,$FIELD_FEES_FIAT"
            }
            if (config.includesOriginDestination) {
                header += ",$FIELD_CONTEXT"
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

            val date = info.payment.completedAt ?: info.payment.createdAt
            val dateStr = Instant.fromEpochMilliseconds(date).toString() // ISO-8601 format
            var row = processField(dateStr)

            val amtMsat = info.payment.amount.msat
            val feesMsat = info.payment.fees.msat
            val isOutgoing = info.payment is OutgoingPayment

            val amtMsatStr = if (isOutgoing) "-$amtMsat" else "$amtMsat"
            row += ",${processField(amtMsatStr)}"

            val feesMsatStr = if (feesMsat > 0) "-$feesMsat" else "$feesMsat"
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

                    val amtFiatStr = "${formatFiatValue(amtFiat, isOutgoing)} $currencyName"
                    val feesFiatStr = "${formatFiatValue(feesFiat, true)} $currencyName"

                    row += ",${processField(amtFiatStr)}"
                    row += ",${processField(feesFiatStr)}"
                } ?: run {
                    row += ",,"
                }
            }

            if (config.includesOriginDestination) {
                val details = when (val payment = info.payment) {
                    is IncomingPayment -> when (val origin = payment.origin) {
                        is IncomingPayment.Origin.Invoice -> "Incoming LN payment"
                        is IncomingPayment.Origin.SwapIn -> "Swap-in to ${origin.address ?: "N/A"}"
                        is IncomingPayment.Origin.OnChain -> {
                            "Swap-in with inputs: ${origin.localInputs.map { it.txid.toString() } }"
                        }
                        is IncomingPayment.Origin.Offer -> {
                            "Incoming offer ${origin.metadata.offerId}"
                        }
                    }
                    is LightningOutgoingPayment -> when (val details = payment.details) {
                        is LightningOutgoingPayment.Details.Normal -> "Outgoing LN payment to ${details.paymentRequest.nodeId.toHex()}"
                        is LightningOutgoingPayment.Details.SwapOut -> "Swap-out to ${details.address}"
                        is LightningOutgoingPayment.Details.Blinded -> "Offer to ${details.payerKey.publicKey()}"
                    }
                    is SpliceOutgoingPayment -> "Outgoing splice to ${payment.address}"
                    is ChannelCloseOutgoingPayment -> "Channel closing to ${payment.address}"
                    is SpliceCpfpOutgoingPayment -> "Accelerate transactions with CPFP"
                    is InboundLiquidityOutgoingPayment -> "+${payment.lease.amount.sat} sat inbound liquidity"
                }
                row += ",${processField(details)}"
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

        private fun formatFiatValue(amt: Double, negative: Boolean): String {

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
            // Implementation Notes:
            //
            // We can't use Java's String.format function on KMM.
            // And Kotlin's String.companion.format isn't available until Kotlin v1.7.2
            //
            // So we're stuck rolling our own solution.
            // And the biggest problem we have is that Double.toString() might
            // produce something like this: "7.900441605000001E-4"

            val integerPart = amt.toLong().toString()
            val fractionPart = ((amt % 1) * 10_000).toLong().toString()
                .take(4).padStart(4, '0')

            val formattedStr = "${integerPart}.${fractionPart}"
            return if (negative && formattedStr != "0.0000") "-$formattedStr" else formattedStr
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