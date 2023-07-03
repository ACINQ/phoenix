package fr.acinq.phoenix.db

import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.data.WalletContext
import fr.acinq.phoenix.db.notifications.NotificationsQueries
import fracinqphoenixdb.Exchange_rates
import fracinqphoenixdb.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class SqliteAppDb(private val driver: SqlDriver) {

    private val database = AppDatabase(
        driver = driver,
        exchange_ratesAdapter = Exchange_rates.Adapter(
            typeAdapter = EnumColumnAdapter()
        ),
        notificationsAdapter = Notifications.Adapter(
            type_versionAdapter = EnumColumnAdapter()
        )
    )

    private val paramsQueries = database.walletParamsQueries
    private val priceQueries = database.exchangeRatesQueries
    private val keyValueStoreQueries = database.keyValueStoreQueries
    private val notificationsQueries = NotificationsQueries(database)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Save a list of [ExchangeRate] items to the database.
     * Inserts new items, and updates existing items.
     */
    suspend fun saveExchangeRates(rates: List<ExchangeRate>) {
        if (rates.isEmpty()) return
        withContext(Dispatchers.Default) {
            database.transaction {
                for (rate in rates) {
                    val row = when (rate) {
                        is ExchangeRate.BitcoinPriceRate -> rate.toRow()
                        is ExchangeRate.UsdPriceRate -> rate.toRow()
                    }
                    priceQueries.get(row.fiat).executeAsOneOrNull()?.run {
                        priceQueries.update(
                            price = row.price,
                            type = row.type,
                            source = row.source,
                            updated_at = row.updated_at,
                            fiat = row.fiat
                        )
                    } ?: run {
                        priceQueries.insert(
                            fiat = row.fiat,
                            price = row.price,
                            type = row.type,
                            source = row.source,
                            updated_at = row.updated_at
                        )
                    }
                }
            }
        }
    }

    /**
     * Emits the list of exchange rates as a flow,
     * and emits a new result everytime the data changes in the database.
     */
    fun listBitcoinRates(): Flow<List<ExchangeRate>> {
        // Here's what we want:
        // - we should be able to **REMOVE** fiat currencies from the codebase in the future
        //   (e.g. after it collapses due to hyperinflation)
        // - however, after we do, the corresponding row will remain in the user's database
        // - attempting to force-decode it via FiatCurrency.valueOf(code) will throw an exception
        // - so we use FiatCurrency.valueOfOrNull, and workaround potential null values
        //
        return priceQueries.list(mapper = { fiat, price, type, source, updated_at ->
            ExchangeRate.Row(fiat, price, type, source, updated_at)
        }).asFlow().mapToList().map {
            it.mapNotNull { row ->
                FiatCurrency.valueOfOrNull(row.fiat)?.let { fiatCurrency ->
                    when (row.type) {
                        ExchangeRate.Type.BTC -> {
                            ExchangeRate.BitcoinPriceRate(
                                fiatCurrency = fiatCurrency,
                                price = row.price,
                                source = row.source,
                                timestampMillis = row.updated_at
                            )
                        }
                        ExchangeRate.Type.USD -> {
                            ExchangeRate.UsdPriceRate(
                                fiatCurrency = fiatCurrency,
                                price = row.price,
                                source = row.source,
                                timestampMillis = row.updated_at
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun deleteBitcoinRate(fiat: String) {
        withContext(Dispatchers.Default) {
            priceQueries.delete(fiat)
        }
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
        } ?: (Instant.DISTANT_PAST.toEpochMilliseconds() to null)

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

    suspend fun getValue(key: String): Pair<ByteArray, Long>? {
        return keyValueStoreQueries.get(key).executeAsOneOrNull()?.let {
            Pair(it.value_, it.updated_at)
        }
    }

    suspend fun <T> getValue(key: String, transform: (ByteArray) -> T): Pair<T, Long>? {
        return keyValueStoreQueries.get(key).executeAsOneOrNull()?.let {
            val tValue = transform(it.value_)
            Pair(tValue, it.updated_at)
        }
    }

    suspend fun setValue(value: ByteArray, key: String): Long {
        return database.transactionWithResult {
            val exists = keyValueStoreQueries.exists(key).executeAsOne() > 0
            val now = currentTimestampMillis()
            if (exists) {
                keyValueStoreQueries.update(key = key, value_ = value, updated_at = now)
            } else {
                keyValueStoreQueries.insert(key = key, value_ = value, updated_at = now)
            }
            now
        }
    }

    suspend fun getNotification(id: UUID): Notification? = withContext(Dispatchers.Default) {
        notificationsQueries.get(id)
    }

    suspend fun saveNotification(notification: Notification) = withContext(Dispatchers.Default) {
        notificationsQueries.save(notification)
    }

    suspend fun dismissNotifications(ids: Set<UUID>) = withContext(Dispatchers.Default) {
        notificationsQueries.markAsRead(ids)
    }

    suspend fun dismissAllNotifications() {
        notificationsQueries.markAllAsRead()
    }

    suspend fun listUnreadNotification(): Flow<List<Pair<Set<UUID>, Notification>>> = withContext(Dispatchers.Default) {
        notificationsQueries.listUnread()
    }

    fun close() {
        driver.close()
    }
}