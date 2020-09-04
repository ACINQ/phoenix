package fr.acinq.phoenix.app

import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.utils.TAG_APPLICATION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import org.kodein.db.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

@OptIn(ExperimentalCoroutinesApi::class)
class AppConfigurationManager (override val di: DI) : DIAware, CoroutineScope by MainScope() {
    private val db: DB by instance(tag = TAG_APPLICATION)
    private val key = db.key<AppConfiguration>(0)

    /*
        TODO Manage updates for connection configurations:
            e.g. for Electrum Server : reconnect to new server
     */

    init {
        createAppConfiguration()
    }

    private fun createAppConfiguration(): AppConfiguration {
        if (db[key] == null) {
            db.put(AppConfiguration())
        }
        return db[key] ?: error("App configuration must be initialized.")
    }

    fun getAppConfiguration() : AppConfiguration {
        return db[key] ?: createAppConfiguration()
    }

    fun putElectrumServer(electrumServer: String) {
        db.put(key, getAppConfiguration().copy(electrumServer = electrumServer))
    }

    fun putFiatCurrency(fiatCurrency: FiatCurrency) {
        db.put(key, getAppConfiguration().copy(fiatCurrency = fiatCurrency))
    }

    fun putBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        db.put(key, getAppConfiguration().copy(bitcoinUnit = bitcoinUnit))
    }

    fun putAppTheme(appTheme: AppTheme) {
        db.put(key, getAppConfiguration().copy(appTheme = appTheme))
    }
}
