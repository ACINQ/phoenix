package fr.acinq.phoenix.data

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class ExchangeRate {

    abstract val fiatCurrency: FiatCurrency

    /** An exchange rate may be between a fiat currency and Bitcoin, or between a fiat currency and the US Dollar. */
    enum class Type {
        BTC,
        USD
    }

    data class Row(
        val fiat: String,
        val price: Double,
        val type: Type,
        val source: String,
        val updated_at: Long
    )

    /**
     * Price: 1 BTC = $price FIAT
     */
    data class BitcoinPriceRate(
        override val fiatCurrency: FiatCurrency,
        /** The price of 1 BTC in this currency */
        val price: Double,
        val source: String,
        val timestampMillis: Long
    ): ExchangeRate() {
        fun toRow() = Row(
            fiat = fiatCurrency.name,
            price = price,
            type = Type.BTC,
            source = source,
            updated_at = timestampMillis
        )
    }

    /**
     * Price: 1 USD = $price FIAT
     */
    data class UsdPriceRate(
        override val fiatCurrency: FiatCurrency,
        /** The price of one US Dollar in this currency */
        val price: Double,
        val source: String,
        val timestampMillis: Long
    ): ExchangeRate() {
        fun toRow() = Row(
            fiat = fiatCurrency.name,
            price = price,
            type = Type.USD,
            source = source,
            updated_at = timestampMillis
        )
    }
}

@Serializable data class BlockchainInfoPriceObject(val last: Double)

/**
 * Coindesk example:
 * {
 *   "time":{
 *     "updated":"Oct 21, 2021 19:47:00 UTC",
 *     "updatedISO":"2021-10-21T19:47:00+00:00",
 *     "updateduk":"Oct 21, 2021 at 20:47 BST"
 *   },
 *   "disclaimer":"...",
 *   "bpi":{
 *     "USD":{
 *       "code":"USD",
 *       "rate":"62,980.0572",
 *       "description":"United States Dollar",
 *       "rate_float":62980.0572
 *     },
 *     "ILS":{
 *       "code":"ILS",
 *       "rate":"202,056.4047",
 *       "description":"Israeli New Sheqel",
 *       "rate_float":202056.4047
 *     }
 *   }
 * }
 */

@Serializable
data class CoinDeskResponse(
    val time: Time,
    val bpi: Map<String, Rate>
) {
    @Serializable
    data class Time(
        val updatedISO: Instant
    )

    @Serializable
    data class Rate(
        @SerialName("rate_float")
        val rate: Double
    )
}
