package fr.acinq.phoenix.data

import fr.acinq.phoenix.controllers.MVI
import fr.acinq.phoenix.controllers.main.Home
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class ExchangeRate {

    abstract val fiatCurrency: FiatCurrency
    abstract val timestampMillis: Long

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
        override val timestampMillis: Long
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
        override val timestampMillis: Long
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

/**
 * Blockchain.info example:
 * {
 *   "ARS": {
 *     "15m": 5453151.01,
 *     "last": 5453151.01,
 *     "buy": 5453151.01,
 *     "sell": 5453151.01,
 *     "symbol": "ARS"
 *   },
 *   "AUD": {
 *     "15m": 24869.41,
 *     "last": 24869.41,
 *     "buy": 24869.41,
 *     "sell": 24869.41,
 *     "symbol": "AUD"
 *   },
 *   ...
 * }
 */

@Serializable
data class BlockchainInfoPriceObject(
    val last: Double
)

typealias BlockchainInfoResponse = Map<String, BlockchainInfoPriceObject>

/**
 * Coinbase example:
 * {
 *   "data": {
 *     "currency": "USD",
 *     "rates": {
 *       "AED": "3.6726399999999999",
 *       "AFN": "89.2213",
 *       ...
 *     }
 *   }
 * }
 */

@Serializable
data class CoinbaseResponse(
    val data: Data,
) {
    @Serializable
    data class Data(
        val rates: Map<String, String>
    )
}

/**
 * Bluelytics example:
 * {
 *   "oficial":{
 *     "value_avg":110.92,
 *     "value_sell":113.92,
 *     "value_buy":107.92
 *   },
 *   "blue":{
 *     "value_avg":198.50,
 *     "value_sell":200.50,
 *     "value_buy":196.50
 *   },
 *   "oficial_euro":{
 *     "value_avg":119.27,
 *     "value_sell":122.49,
 *     "value_buy":116.04
 *   },
 *   "blue_euro":{
 *     "value_avg":213.44,
 *     "value_sell":215.59,
 *     "value_buy":211.29
 *   },
 *   "last_update":"2022-03-07T15:25:32.816374-03:00"
 * }
 */

@Serializable
data class BluelyticsResponse(
    val blue: Rate,
    val blue_euro: Rate
) {
    @Serializable
    data class Rate(
        val value_avg: Double,
        val value_sell: Double,
        val value_buy: Double
    )
}

/**
 * Yadio example:
 * {
 *   "BTC": 16640.86,
 *   "USD": {
 *     "CUP": 170,
 *     "IRR": 4063500,
 *     ...
 *   },
 *   "base": "USD",
 *   "timestamp": 1672776301979
 * }
 */

@Serializable
data class YadioResponse(
    @SerialName("USD")
    val usdRates: Map<String, Double>
)