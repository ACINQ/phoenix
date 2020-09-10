package fr.acinq.phoenix.data

import kotlinx.serialization.Serializable
import org.kodein.db.model.orm.Metadata

@Serializable
data class BitcoinPriceRate(
    val fiatCurrency: FiatCurrency,
    val price: Double
) : Metadata {
    override val id: Any get() = fiatCurrency.name
}

@Serializable data class PriceRate(val last: Double)
@Serializable data class MxnApiResponse(val success: Boolean, val payload: MxnPriceRate)
@Serializable data class MxnPriceRate(val last: Double)
