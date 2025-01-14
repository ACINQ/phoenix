/*
 * Copyright 2025 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.csv

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.phoenix.data.ExchangeRate
import kotlin.math.absoluteValue

open class CsvWriter {
    private val sb: StringBuilder = StringBuilder()

    fun addRow(vararg fields: String) {
        val cleanFields = fields.map { processField(it) }
        sb.append(cleanFields.joinToString(separator = ",", postfix = "\n"))
    }

    fun addRow(fields: List<String>) {
        addRow(*fields.toTypedArray())
    }

    fun getContent(): String {
        return sb.toString()
    }

    private fun processField(str: String): String {
        return str.findAnyOf(listOf(",", "\"", "\n"))?.let {
            // - field must be enclosed in double-quotes
            // - a double-quote appearing inside the field must be
            //   escaped by preceding it with another double quote
            "\"${str.replace("\"", "\"\"")}\""
        } ?: str
    }

    /**
     * Convert and format an amount to fiat using the provided exchange rate. The amount is displayed like this: "1.2345 EUR".
     * Note, the result will not be negative, even if [amount] is (which happens for outgoing payments, as a way to represent in the CSV money leaving the wallet).
     */
    fun convertToFiat(amount: MilliSatoshi?, exchangeRate: ExchangeRate.BitcoinPriceRate?): String {
        if (amount == null || exchangeRate == null) return ""

        val msatsPerBitcoin = 100_000_000_000.toDouble()
        val amtFiat = (amount.msat.absoluteValue / msatsPerBitcoin) * exchangeRate.price

        val currencyName = exchangeRate.fiatCurrency.name

        return "${formatFiatValue(amtFiat)} $currencyName"
    }

    /**
     * Format a Double amount as a String. We always display 4 decimal places.
     *
     * The extra precision can be truncated / rounded / ignored by the reader, who has more insight into how they wish
     * to use the exported information.
     *
     * Note: we can't use Java's String.format function on KMM. Also, Double.toString() might produce something like this: "7.900441605000001E-4".
     * So we're stuck rolling our own solution.
     */
    private fun formatFiatValue(amt: Double): String {
        val integerPart = amt.toLong().toString()
        val fractionPart = ((amt % 1) * 10_000).toLong().toString()
            .take(4).padStart(4, '0')

        val formattedStr = "${integerPart}.${fractionPart}"
        return formattedStr
    }
}