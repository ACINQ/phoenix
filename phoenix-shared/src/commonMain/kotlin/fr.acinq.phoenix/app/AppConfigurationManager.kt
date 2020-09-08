package fr.acinq.phoenix.app

import fr.acinq.phoenix.data.*
import fr.acinq.phoenix.utils.TAG_APPLICATION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.kodein.db.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalCoroutinesApi::class)
class AppConfigurationManager (override val di: DI) : DIAware, CoroutineScope by MainScope() {
    private val db: DB by instance(tag = TAG_APPLICATION)

    private val logger = direct.instance<LoggerFactory>().newLogger(AppConfigurationManager::class)

    private val electrumServerUpdates = ConflatedBroadcastChannel<ElectrumServer>()
    fun openElectrumServerUpdateSubscription(): ReceiveChannel<ElectrumServer> = electrumServerUpdates.openSubscription()

    /*
        TODO Manage updates for connection configurations:
            e.g. for Electrum Server : reconnect to new server
     */
    init {
        db.on<ElectrumServer>().register {
            didPut {
                launch { electrumServerUpdates.send(it) }
            }
        }
    }


    private val appConfigurationKey = db.key<AppConfiguration>(0)
    private fun createAppConfiguration(): AppConfiguration {
        if (db[appConfigurationKey] == null) {
            logger.info { "Create app configuration" }
            db.put(AppConfiguration())
        }
        return db[appConfigurationKey] ?: error("App configuration must be initialized.")
    }

    fun getAppConfiguration() : AppConfiguration = db[appConfigurationKey] ?: createAppConfiguration()

    fun putFiatCurrency(fiatCurrency: FiatCurrency) {
        logger.info { "Change fiat currency [$fiatCurrency]" }
        db.put(appConfigurationKey, getAppConfiguration().copy(fiatCurrency = fiatCurrency))
    }

    fun putBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        logger.info { "Change bitcoin unit [$bitcoinUnit]" }
        db.put(appConfigurationKey, getAppConfiguration().copy(bitcoinUnit = bitcoinUnit))
    }

    fun putAppTheme(appTheme: AppTheme) {
        logger.info { "Change app theme [$appTheme]" }
        db.put(appConfigurationKey, getAppConfiguration().copy(appTheme = appTheme))
    }

    private val electrumServerKey = db.key<ElectrumServer>(0)
    private fun createElectrumConfiguration(): ElectrumServer {
        if (db[electrumServerKey] == null) {
            logger.info { "Create ElectrumX configuration" }
            db.put(ElectrumServer())
        }
        return db[electrumServerKey] ?: error("ElectrumServer must be initialized.")
    }

    fun getElectrumServer(): ElectrumServer = db[electrumServerKey] ?: createElectrumConfiguration()

    fun putElectrumServerAddress(host: String, port: Int) {
        putElectrumServer(getElectrumServer().copy(host = host, port = port))
    }
    fun putElectrumServer(electrumServer: ElectrumServer) {
        logger.info { "Update electrum configuration [$electrumServer]" }
        db.put(electrumServerKey, electrumServer)
    }
}
