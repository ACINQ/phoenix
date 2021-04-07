package fr.acinq.phoenix.db

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import fr.acinq.lightning.WalletParams
import fr.acinq.phoenix.data.ApiWalletParams
import fr.acinq.phoenix.data.BitcoinPriceRate
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.FiatCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class SqliteAppDb(driver: SqlDriver) {

    private val database = AppDatabase(driver = driver)
    private val paramsQueries = database.walletParamsQueries
    private val priceQueries = database.bitcoinPriceRatesQueries
    private val json = Json { ignoreUnknownKeys = true }

    /** Save a [BitcoinPriceRate] to the database (update or insert if does not exist). */
    suspend fun saveBitcoinRate(rate: BitcoinPriceRate) {
        withContext(Dispatchers.Default) {
            priceQueries.transaction {
                priceQueries.get(rate.fiatCurrency.name).executeAsOneOrNull()?.run {
                    priceQueries.update(rate.price, rate.source, rate.timestampMillis, rate.fiatCurrency.name)
                } ?: run {
                    priceQueries.insert(rate.fiatCurrency.name, rate.price, rate.source, rate.timestampMillis)
                }
            }
        }
    }

    /** Emits the list of bitcoin price rate as a flow, and emits a new result every time the data changes in db. */
    fun listBitcoinRates(): Flow<List<BitcoinPriceRate>> {
        return priceQueries.list(mapper = { fiat_code, price_per_btc, source_url, updated_at ->
            BitcoinPriceRate(FiatCurrency.valueOf(fiat_code), price_per_btc, source_url, updated_at)
        }).asFlow().mapToList()

    }

    suspend fun setWalletParams(version: ApiWalletParams.Version, rawData: String): WalletParams? {
        withContext(Dispatchers.Default) {
            paramsQueries.transaction {
                paramsQueries.get(version.name).executeAsOneOrNull()?.run {
                    paramsQueries.update(
                        version = this.version,
                        data = rawData,
                        updated_at = Clock.System.now().epochSeconds
                    )
                } ?: run {
                    paramsQueries.insert(
                        version = version.name,
                        data = rawData,
                        updated_at = Clock.System.now().epochSeconds
                    )
                }
            }
        }

        return getWalletParamsOrNull(version).second
    }

    suspend fun getWalletParamsOrNull(version: ApiWalletParams.Version): Pair<Instant, WalletParams?> =
        withContext(Dispatchers.Default) {
            paramsQueries.get(version.name, ::mapWalletParams).executeAsOneOrNull()
        } ?: Instant.DISTANT_PAST to null

    private fun mapWalletParams(
        version: String,
        data: String,
        updated_at: Long
    ): Pair<Instant, WalletParams?> {
        val walletParams = when (ApiWalletParams.Version.valueOf(version)) {
            ApiWalletParams.Version.V0 -> try {
                json.decodeFromString(
                    ApiWalletParams.V0.serializer(),
                    data
                ).export(Chain.Testnet)
            } catch (e: Exception) {
                null
            }
        }

        return Instant.fromEpochSeconds(updated_at) to walletParams
    }
}