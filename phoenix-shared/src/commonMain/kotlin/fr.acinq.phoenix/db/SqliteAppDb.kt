package fr.acinq.phoenix.db

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import fr.acinq.phoenix.data.WalletContext
import fr.acinq.phoenix.data.BitcoinPriceRate
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

    suspend fun setWalletContext(version: WalletContext.Version, rawData: String): WalletContext.V0? {
        withContext(Dispatchers.Default) {
            paramsQueries.transaction {
                paramsQueries.get(version.name).executeAsOneOrNull()?.run {
                    paramsQueries.update(
                        version = this.version,
                        data_ = rawData,
                        updated_at = Clock.System.now().toEpochMilliseconds()
                    )
                } ?: run {
                    paramsQueries.insert(
                        version = version.name,
                        data_ = rawData,
                        updated_at = Clock.System.now().toEpochMilliseconds()
                    )
                }
            }
        }

        return getWalletContextOrNull(version).second
    }

    suspend fun getWalletContextOrNull(version: WalletContext.Version): Pair<Long, WalletContext.V0?> =
        withContext(Dispatchers.Default) {
            paramsQueries.get(version.name, ::mapWalletContext).executeAsOneOrNull()
        } ?: Instant.DISTANT_PAST.toEpochMilliseconds() to null

    private fun mapWalletContext(
        version: String,
        data: String,
        updated_at: Long
    ): Pair<Long, WalletContext.V0?> {
        val walletContext = when (WalletContext.Version.valueOf(version)) {
            WalletContext.Version.V0 -> try {
                json.decodeFromString(
                    WalletContext.V0.serializer(),
                    data
                )
            } catch (e: Exception) {
                null
            }
        }

        return updated_at to walletContext
    }
}