package fr.acinq.phoenix.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.utils.UUID
import fr.acinq.lightning.utils.currentTimestampMillis
import fr.acinq.phoenix.data.ContactInfo
import fr.acinq.phoenix.data.ExchangeRate
import fr.acinq.phoenix.data.FiatCurrency
import fr.acinq.phoenix.data.Notification
import fr.acinq.phoenix.db.notifications.ContactQueries
import fr.acinq.phoenix.db.notifications.NotificationsQueries
import fracinqphoenixdb.Exchange_rates
import fracinqphoenixdb.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqliteAppDb(private val driver: SqlDriver) {

    internal val database = AppDatabase(
        driver = driver,
        exchange_ratesAdapter = Exchange_rates.Adapter(
            typeAdapter = EnumColumnAdapter()
        ),
        notificationsAdapter = Notifications.Adapter(
            type_versionAdapter = EnumColumnAdapter()
        )
    )

    private val priceQueries = database.exchangeRatesQueries
    private val keyValueStoreQueries = database.keyValueStoreQueries
    private val notificationsQueries = NotificationsQueries(database)
    internal val contactQueries = ContactQueries(database)

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
        })
        .asFlow()
        .map {
            withContext(Dispatchers.Default) {
                database.transactionWithResult {
                    it.executeAsList()
                }
            }
        }
        .map {
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

    suspend fun getContact(contactId: UUID): ContactInfo? = withContext(Dispatchers.Default) {
        contactQueries.getContact(contactId)
    }

    suspend fun getContactForOffer(offerId: ByteVector32): ContactInfo? = withContext(Dispatchers.Default) {
        contactQueries.getContactForOffer(offerId)
    }

    suspend fun monitorContacts(): Flow<List<ContactInfo>> = withContext(Dispatchers.Default) {
        contactQueries.monitorContactsFlow()
    }

    suspend fun listContacts(): List<ContactInfo> = withContext(Dispatchers.Default) {
        contactQueries.listContacts()
    }

    suspend fun saveContact(contact: ContactInfo) = withContext(Dispatchers.Default) {
        contactQueries.saveContact(contact)
    }

    suspend fun updateContact(contact: ContactInfo) = withContext(Dispatchers.Default) {
        contactQueries.updateContact(contact)
    }

    suspend fun deleteContact(contactId: UUID) = withContext(Dispatchers.Default) {
        contactQueries.deleteContact(contactId)
    }

    suspend fun deleteOfferContactLink(offerId: ByteVector32) = withContext(Dispatchers.Default) {
        contactQueries.deleteOfferContactLink(offerId)
    }

    fun close() {
        driver.close()
    }
}
