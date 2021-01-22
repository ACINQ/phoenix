package fr.acinq.phoenix.db

import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.eclair.WalletParams
import fr.acinq.phoenix.data.ApiWalletParams
import fr.acinq.phoenix.data.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class SqliteAppDb(driver: SqlDriver) {

    private val database = AppDatabase(driver = driver)
    private val queries = database.walletParamsQueries
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun setWalletParams(version: ApiWalletParams.Version, rawData: String): WalletParams? {
        withContext(Dispatchers.Default) {
            queries.transaction {
                queries.get(version.name).executeAsOneOrNull()?.run {
                    queries.update(
                        version = this.version,
                        data = rawData,
                        updated_at = Clock.System.now().epochSeconds
                    )
                } ?: run {
                    queries.insert(
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
            queries.get(version.name, ::mapWalletParams).executeAsOneOrNull()
        } ?: Instant.DISTANT_PAST to null

    private fun mapWalletParams(
         version: String,
         data: String,
         updated_at: Long
    ): Pair<Instant, WalletParams> {
        val walletParams = when(ApiWalletParams.Version.valueOf(version)) {
            ApiWalletParams.Version.V0 -> json.decodeFromString(ApiWalletParams.V0.serializer(), data).export(Chain.TESTNET)
        }

        return Instant.fromEpochSeconds(updated_at) to walletParams
    }
}